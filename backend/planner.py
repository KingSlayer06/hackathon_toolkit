"""
Translate a natural-language sentence into a structured `Plan`.

We support two LLM backends, selected by env var:

    LLM_PROVIDER=anthropic   (default if ANTHROPIC_API_KEY is set)
    LLM_PROVIDER=openai      (default if OPENAI_API_KEY is set)

Both go through Pydantic for validation, so the rest of the codebase
never sees raw model output.
"""

from __future__ import annotations

import json
import os
from typing import Any

from pydantic import ValidationError

from .bunq_service import list_cards, list_sub_accounts
from .models import Plan

SYSTEM_PROMPT = """\
You are Vox, a planner that turns short natural-language voice commands about \
a bunq bank account into a structured Plan of one or more Actions. \
You never move money OUT of the user's bunq (no external IBANs, no payments \
to people). All transfers are between the user's own sub-accounts.

About sub-account names:
- The user has the sub-accounts listed below ("Available sub-accounts"). \
Match against them case-insensitively and use the exact spelling shown when one matches.
- The user MAY mention a sub-account that doesn't exist yet (e.g. "Travel" when \
they only have "Main"). That's fine — emit the action using the user's name (Title-cased), \
and add a `warnings` entry like "Will create sub-account 'Travel'." \
The system will create it on demand.
- You MUST NOT invent a SOURCE sub-account that doesn't exist for a transfer — \
you can't take money from an account that doesn't exist. If the user's source \
isn't listed, refuse that transfer in `warnings` and skip it.
- Card labels work the same way EXCEPT cards cannot be auto-created. If no card \
matches, refuse the conditional_freeze in `warnings`.

There are exactly four Action kinds you may emit:

1. transfer
   - Move money NOW between two of the user's sub-accounts.
   - Required: from_account, to_account, amount_eur (positive number).

2. recurring_split
   - Standing rule: when an INCOMING payment arrives whose description \
contains `trigger_match` (case-insensitive), automatically route \
`percentage`% of it into `to_account`.
   - Required: trigger_match, percentage (0–100), to_account.

3. conditional_freeze
   - Standing rule: if cumulative outgoing spend matching `merchant_match` \
in the last `window_days` days exceeds `threshold_eur`, freeze the card \
labelled `card_label`.
   - Required: merchant_match, threshold_eur, card_label. Default window_days=7.

4. transaction_limit_freeze
   - Per-transaction security guardrail: if ANY SINGLE outgoing payment is \
greater than or equal to `max_tx_eur`, immediately freeze `card_label`. \
Use this when the user talks about a per-payment cap or "if I ever spend more \
than X in one go" — distinct from #3 which is about cumulative spend.
   - Required: max_tx_eur, card_label.
   - Optional: `from_account` (only count tx originating from this sub-account), \
`merchant_match` (only count tx whose merchant/description contains this substring).

Rules of engagement:
- If the user mentions a sub-account or card you don't recognise, pick the \
closest match by name and add a warning explaining what you assumed.
- If the user asks for something you can't represent (e.g. paying an external \
person, buying stocks, opening a new account), put a clear refusal in `warnings` \
and skip that part — DO NOT emit an Action for it.
- `summary` is one short sentence the user will see in the UI confirming \
what you understood.
- Be conservative: when ambiguous, prefer the smaller / safer interpretation.

Return ONLY a JSON object matching the Plan schema. No prose.
"""


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def make_plan(text: str) -> Plan:
    """Translate `text` into a validated `Plan`."""
    text = (text or "").strip()
    if not text:
        return Plan(actions=[], summary="(empty input)", warnings=["No text was provided."])

    sub_accounts = [{"description": s.description, "balance_eur": s.balance_eur} for s in list_sub_accounts()]
    cards = [{"label": c.label, "status": c.status} for c in list_cards()]

    user_prompt = _build_user_prompt(text, sub_accounts, cards)

    provider = _select_provider()
    if provider == "anthropic":
        raw = _call_anthropic(user_prompt)
    elif provider == "openai":
        raw = _call_openai(user_prompt)
    else:
        raise RuntimeError(
            "No LLM provider configured. Set ANTHROPIC_API_KEY or OPENAI_API_KEY in your environment."
        )

    try:
        return Plan.model_validate(raw)
    except ValidationError as e:
        return Plan(
            actions=[],
            summary="Sorry — I couldn't parse that into a valid plan.",
            warnings=[f"validation error: {e.errors()[:3]}", f"raw: {json.dumps(raw)[:500]}"],
        )


# ---------------------------------------------------------------------------
# Provider selection
# ---------------------------------------------------------------------------


def _select_provider() -> str | None:
    explicit = os.getenv("LLM_PROVIDER", "").strip().lower()
    if explicit in {"anthropic", "openai"}:
        return explicit
    if os.getenv("ANTHROPIC_API_KEY"):
        return "anthropic"
    if os.getenv("OPENAI_API_KEY"):
        return "openai"
    return None


def _build_user_prompt(text: str, sub_accounts: list[dict], cards: list[dict]) -> str:
    return (
        f"Available sub-accounts (use these descriptions verbatim):\n"
        f"{json.dumps(sub_accounts, indent=2)}\n\n"
        f"Available cards (use these labels verbatim):\n"
        f"{json.dumps(cards, indent=2)}\n\n"
        f"User said:\n\"{text}\"\n\n"
        f"Emit the Plan JSON now."
    )


# ---------------------------------------------------------------------------
# Anthropic
# ---------------------------------------------------------------------------

PLAN_TOOL = {
    "name": "submit_plan",
    "description": "Submit the structured plan that translates the user's request.",
    "input_schema": Plan.model_json_schema(),
}


def _call_anthropic(user_prompt: str) -> dict[str, Any]:
    from anthropic import Anthropic

    client = Anthropic()
    model = os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-5")
    resp = client.messages.create(
        model=model,
        max_tokens=2048,
        system=SYSTEM_PROMPT,
        tools=[PLAN_TOOL],
        tool_choice={"type": "tool", "name": "submit_plan"},
        messages=[{"role": "user", "content": user_prompt}],
    )
    for block in resp.content:
        if getattr(block, "type", None) == "tool_use" and block.name == "submit_plan":
            return dict(block.input)
    raise RuntimeError("Anthropic did not return a tool_use block")


# ---------------------------------------------------------------------------
# OpenAI
# ---------------------------------------------------------------------------


def _call_openai(user_prompt: str) -> dict[str, Any]:
    from openai import OpenAI

    client = OpenAI()
    model = os.getenv("OPENAI_MODEL", "gpt-4o-2024-08-06")
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        response_format={
            "type": "json_schema",
            "json_schema": {
                "name": "Plan",
                "schema": Plan.model_json_schema(),
                "strict": False,
            },
        },
        temperature=0.1,
    )
    content = resp.choices[0].message.content or "{}"
    return json.loads(content)
