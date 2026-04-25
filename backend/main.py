"""
Vox backend — FastAPI entry point.

Run from project root:

    pip install -r requirements.txt
    uvicorn backend.main:app --reload --port 8080

Endpoints:
    GET  /health
    GET  /accounts                -> list sub-accounts (with balances)
    GET  /cards                   -> list cards
    GET  /rules                   -> list active rules
    DEL  /rules/{id}              -> deactivate a rule
    GET  /firings                 -> recent firing history
    POST /transcribe              -> multipart upload {file} -> {text}
    POST /plan                    -> {text} -> Plan
    POST /execute                 -> {plan, selected_indexes?} -> ExecuteResponse
    POST /webhook/bunq            -> bunq notification target
    POST /demo/fire-salary        -> simulate salary deposit (kicker #1)
    POST /demo/fire-bar-spend     -> simulate bar spend (kicker #2)
    POST /demo/fire-large-tx      -> simulate single large tx (kicker #3)
    GET  /events                  -> SSE stream of FiringEvent
"""

from __future__ import annotations

import asyncio
import json
import os
from typing import AsyncIterator

from dotenv import load_dotenv
from fastapi import Body, FastAPI, File, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

load_dotenv()

from . import bunq_service, db, executor, planner, rules_engine, transcribe  # noqa: E402
from .models import (  # noqa: E402
    CardInfo,
    ExecuteRequest,
    ExecuteResponse,
    Plan,
    PlanRequest,
    RuleView,
    SubAccount,
    TranscribeResponse,
)

app = FastAPI(title="Vox", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # hackathon — tighten for prod
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def _startup() -> None:
    db.init_db()
    try:
        bunq_service.get_client()
    except Exception as e:
        # Don't crash the server — surface via /health so the UI can show a banner.
        print(f"[startup] bunq auth failed: {e}")


# ---------------------------------------------------------------------------
# Read endpoints
# ---------------------------------------------------------------------------


@app.get("/health")
def health() -> dict:
    bunq_ok = False
    bunq_err: str | None = None
    try:
        bunq_service.get_client()
        bunq_ok = True
    except Exception as e:
        bunq_err = str(e)
    transcribe_provider = (
        "groq" if os.getenv("GROQ_API_KEY")
        else ("openai" if os.getenv("OPENAI_API_KEY") else None)
    )
    return {
        "ok": True,
        "bunq_authenticated": bunq_ok,
        "bunq_error": bunq_err,
        "llm_provider": planner._select_provider(),
        # Frontend uses the browser Web Speech API; backend transcribe is optional.
        "transcribe_provider": transcribe_provider,
        "transcribe_mode": "browser" if transcribe_provider is None else "server",
    }


@app.get("/accounts", response_model=list[SubAccount])
def accounts() -> list[SubAccount]:
    bunq_service.invalidate_account_cache()
    return bunq_service.list_sub_accounts()


@app.get("/cards", response_model=list[CardInfo])
def cards() -> list[CardInfo]:
    return bunq_service.list_cards()


@app.get("/rules", response_model=list[RuleView])
def rules() -> list[RuleView]:
    return [RuleView(**r) for r in db.list_active_rules()]


@app.delete("/rules/{rule_id}")
def delete_rule(rule_id: int) -> dict:
    db.deactivate_rule(rule_id)
    return {"ok": True, "rule_id": rule_id}


@app.get("/firings")
def firings(limit: int = 50) -> list[dict]:
    return db.recent_firings(limit=limit)


# ---------------------------------------------------------------------------
# Voice -> Plan -> Execute pipeline
# ---------------------------------------------------------------------------


@app.post("/transcribe", response_model=TranscribeResponse)
async def transcribe_endpoint(file: UploadFile = File(...)) -> TranscribeResponse:
    audio = await file.read()
    if not audio:
        raise HTTPException(400, "empty audio")
    text = transcribe.transcribe(audio, mime=file.content_type or "audio/webm")
    return TranscribeResponse(text=text)


@app.post("/plan", response_model=Plan)
def plan_endpoint(req: PlanRequest) -> Plan:
    return planner.make_plan(req.text)


@app.post("/execute", response_model=ExecuteResponse)
def execute_endpoint(req: ExecuteRequest) -> ExecuteResponse:
    return executor.execute_plan(req.plan, req.selected_indexes)


# ---------------------------------------------------------------------------
# Webhook from bunq (callback target)
# ---------------------------------------------------------------------------


@app.post("/webhook/bunq")
async def webhook(request: Request) -> dict:
    try:
        payload = await request.json()
    except Exception:
        payload = {}
    fired = rules_engine.handle_webhook(payload)
    return {"ok": True, "fired": [ev.model_dump() for ev in fired]}


# ---------------------------------------------------------------------------
# SSE: live stream of rule firings (frontend toasts)
# ---------------------------------------------------------------------------


@app.get("/events")
async def events() -> StreamingResponse:
    queue = rules_engine.subscribe()

    async def gen() -> AsyncIterator[bytes]:
        try:
            yield b": connected\n\n"
            while True:
                try:
                    ev = await asyncio.wait_for(queue.get(), timeout=15.0)
                    payload = json.dumps(ev.model_dump())
                    yield f"event: firing\ndata: {payload}\n\n".encode()
                except asyncio.TimeoutError:
                    yield b": ping\n\n"  # keepalive
        finally:
            rules_engine.unsubscribe(queue)

    return StreamingResponse(gen(), media_type="text/event-stream")


# ---------------------------------------------------------------------------
# Demo triggers — these are what you click on stage during the kicker beats
# ---------------------------------------------------------------------------


@app.post("/demo/fire-salary")
def demo_fire_salary(amount_eur: float = Body(2000.0, embed=True)) -> dict:
    """Simulate a salary deposit by pulling money from sugardaddy with a
    description that recurring_split rules will match.

    The payment hits bunq, bunq webhooks us back, the rules engine fires.
    """
    main = bunq_service.find_sub_account(os.getenv("VOX_MAIN_ACCOUNT", "Main"))
    if not main:
        raise HTTPException(404, "Main sub-account not found — run setup_demo.py first")
    bunq_service.request_sandbox_funds(main.id, amount_eur, description="SALARY APRIL")
    return {"ok": True, "account": main.description, "amount_eur": amount_eur}


@app.post("/demo/fire-bar-spend")
def demo_fire_bar_spend(amount_eur: float = Body(35.0, embed=True)) -> dict:
    """Simulate spending at a bar by sending money OUT of the entertainment-
    funded account back to sugardaddy with a 'BAR' description.
    """
    src_name = os.getenv("VOX_ENTERTAINMENT_ACCOUNT", "Entertainment")
    src = bunq_service.find_sub_account(src_name)
    if not src:
        raise HTTPException(404, f"{src_name!r} sub-account not found")
    c = bunq_service.get_client()
    resp = c.post(
        f"user/{c.user_id}/monetary-account/{src.id}/payment",
        {
            "amount": {"value": f"{amount_eur:.2f}", "currency": "EUR"},
            "counterparty_alias": {
                "type": "EMAIL",
                "value": "sugardaddy@bunq.com",
                "name": "Cafe Belgique BAR",
            },
            "description": "BAR — Cafe Belgique",
        },
    )
    bunq_service.invalidate_account_cache()
    return {"ok": True, "account": src.description, "amount_eur": amount_eur, "payment_id": resp[0]["Id"]["id"]}


@app.post("/demo/fire-large-tx")
def demo_fire_large_tx(amount_eur: float = Body(500.0, embed=True)) -> dict:
    """Simulate a single large outgoing transaction so a
    transaction_limit_freeze rule can trip. Sends EUR out of Main → sugardaddy
    with a 'LARGE PURCHASE' description.
    """
    src_name = os.getenv("VOX_MAIN_ACCOUNT", "Main")
    src = bunq_service.find_sub_account(src_name)
    if not src:
        raise HTTPException(404, f"{src_name!r} sub-account not found")
    c = bunq_service.get_client()
    resp = c.post(
        f"user/{c.user_id}/monetary-account/{src.id}/payment",
        {
            "amount": {"value": f"{amount_eur:.2f}", "currency": "EUR"},
            "counterparty_alias": {
                "type": "EMAIL",
                "value": "sugardaddy@bunq.com",
                "name": "BIG TICKET MERCHANT",
            },
            "description": "LARGE PURCHASE — demo",
        },
    )
    bunq_service.invalidate_account_cache()
    return {
        "ok": True,
        "account": src.description,
        "amount_eur": amount_eur,
        "payment_id": resp[0]["Id"]["id"],
    }


@app.post("/demo/replay-payment")
def demo_replay_payment(payload: dict = Body(...)) -> dict:
    """Manually feed a fake bunq webhook payload — useful when you don't have
    ngrok set up yet but still want to demo the rules engine end-to-end."""
    fired = rules_engine.handle_webhook(payload)
    return {"ok": True, "fired": [ev.model_dump() for ev in fired]}
