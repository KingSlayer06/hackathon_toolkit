"""
Evaluate persisted rules against incoming bunq webhook events.

bunq POSTs a notification per Payment / Mutation. We unwrap the payload,
classify it (incoming / outgoing) and run all matching active rules.

Two kinds of rules are evaluated here:

- recurring_split:
    On INCOMING payment whose description matches `trigger_match`,
    move `percentage`% of it from the receiving account to `to_account_id`.

- conditional_freeze:
    On any OUTGOING payment matching `merchant_match`, if cumulative spend
    over the last `window_days` exceeds `threshold_eur`, freeze the card.
    (We sum over the receiving account's recent payments.)

We avoid loops by tagging Vox-initiated payments with a "[vox]" prefix in
the description, then refusing to react to them.
"""

from __future__ import annotations

import asyncio
from typing import Any

from . import bunq_service, db
from .models import FiringEvent

VOX_PREFIX = "[vox]"


# ---------------------------------------------------------------------------
# Pub/sub for the SSE event stream (frontend toasts)
# ---------------------------------------------------------------------------

_subscribers: list[asyncio.Queue[FiringEvent]] = []


def subscribe() -> asyncio.Queue[FiringEvent]:
    q: asyncio.Queue[FiringEvent] = asyncio.Queue(maxsize=64)
    _subscribers.append(q)
    return q


def unsubscribe(q: asyncio.Queue[FiringEvent]) -> None:
    if q in _subscribers:
        _subscribers.remove(q)


def _publish(ev: FiringEvent) -> None:
    for q in list(_subscribers):
        try:
            q.put_nowait(ev)
        except asyncio.QueueFull:
            pass


# ---------------------------------------------------------------------------
# Webhook entry point
# ---------------------------------------------------------------------------


def handle_webhook(payload: dict) -> list[FiringEvent]:
    """Parse a bunq notification and run any matching rules.

    Returns the FiringEvents produced (also published to SSE subscribers).
    """
    fired: list[FiringEvent] = []
    payment = _unwrap_payment(payload)
    if not payment:
        return fired

    description = (payment.get("description") or "").strip()
    if description.lower().startswith(VOX_PREFIX):
        return fired  # don't react to our own moves

    amount_eur = float((payment.get("amount") or {}).get("value", "0") or 0)
    counterparty_name = (payment.get("counterparty_alias") or {}).get("display_name") or ""
    account_id = (payment.get("monetary_account_id")
                  or (payment.get("alias") or {}).get("monetary_account_id"))

    is_incoming = amount_eur > 0
    is_outgoing = amount_eur < 0

    if is_incoming:
        fired.extend(_handle_incoming(account_id, amount_eur, description))
    if is_outgoing:
        fired.extend(_handle_outgoing(account_id, amount_eur, description, counterparty_name))

    for ev in fired:
        _publish(ev)
    return fired


def _handle_incoming(account_id: int | None, amount_eur: float, description: str) -> list[FiringEvent]:
    if account_id is None:
        return []
    fired: list[FiringEvent] = []
    desc_lower = description.lower()
    for rule in db.list_active_rules(kind="recurring_split"):
        cfg = rule["config"]
        match: str = (cfg.get("trigger_match") or "").lower()
        if not match or match not in desc_lower:
            continue

        pct = float(cfg.get("percentage") or 0)
        amount = round(amount_eur * pct / 100.0, 2)
        if amount <= 0:
            continue

        try:
            payment_id = bunq_service.transfer_between(
                from_account_id=int(account_id),
                to_account_id=int(cfg["to_account_id"]),
                amount_eur=amount,
                description=f"{VOX_PREFIX} {pct:g}% of '{description[:40]}'",
            )
            detail = {
                "trigger_description": description,
                "incoming_amount_eur": amount_eur,
                "transferred_eur": amount,
                "to_account": cfg.get("to_account_name"),
                "bunq_payment_id": payment_id,
            }
            db.record_firing(rule["id"], detail)
            fired.append(
                FiringEvent(
                    rule_id=rule["id"],
                    rule_kind=rule["kind"],
                    summary=f"Routed €{amount:.2f} ({pct:g}% of incoming) to {cfg.get('to_account_name')}",
                    detail=detail,
                )
            )
        except Exception as e:
            db.record_firing(rule["id"], {"error": str(e), "incoming": description})
    return fired


def _handle_outgoing(
    account_id: int | None, amount_eur: float, description: str, counterparty_name: str
) -> list[FiringEvent]:
    if account_id is None:
        return []
    fired: list[FiringEvent] = []
    haystack = f"{description} {counterparty_name}".lower()

    for rule in db.list_active_rules(kind="conditional_freeze"):
        cfg = rule["config"]
        match = (cfg.get("merchant_match") or "").lower()
        if not match or match not in haystack:
            continue

        window_days = int(cfg.get("window_days") or 7)
        threshold = float(cfg.get("threshold_eur") or 0)

        try:
            total, matched = bunq_service.spend_in_window(
                account_id=int(account_id),
                merchant_match=match,
                window_days=window_days,
            )
        except Exception as e:
            db.record_firing(rule["id"], {"error": f"spend lookup failed: {e}"})
            continue

        if total < threshold:
            continue  # not yet over budget

        card_id = int(cfg["card_id"])
        ok = bunq_service.freeze_card(card_id)
        detail = {
            "matched_count": len(matched),
            "spent_eur": total,
            "threshold_eur": threshold,
            "window_days": window_days,
            "card_id": card_id,
            "card_label": cfg.get("card_label"),
            "bunq_freeze_ok": ok,
            "trigger_payment_description": description,
        }
        db.record_firing(rule["id"], detail)
        # Soft-disable so we don't keep firing on every subsequent purchase.
        db.deactivate_rule(rule["id"])
        fired.append(
            FiringEvent(
                rule_id=rule["id"],
                rule_kind=rule["kind"],
                summary=(
                    f"Froze {cfg.get('card_label')!r}: spend on “{match}” reached "
                    f"€{total:.2f} (limit €{threshold:.2f})"
                ),
                detail=detail,
            )
        )
    return fired


# ---------------------------------------------------------------------------
# Payload unwrapping
# ---------------------------------------------------------------------------


def _unwrap_payment(payload: dict) -> dict[str, Any] | None:
    """bunq wraps notifications a few different ways. Find the Payment dict."""
    if not isinstance(payload, dict):
        return None

    nu = payload.get("NotificationUrl") or payload.get("notification_url") or payload
    obj = nu.get("object") or nu.get("Object") or {}

    for key in ("Payment", "MasterCardAction", "RequestInquiry", "Mutation"):
        if key in obj:
            inner = obj[key]
            # Mutation wraps a payment object inside it
            if key == "Mutation" and isinstance(inner, dict):
                if "Payment" in inner:
                    return inner["Payment"]
            return inner

    if "Payment" in payload:
        return payload["Payment"]
    return None
