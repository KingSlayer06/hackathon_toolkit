"""
High-level bunq operations for Vox.

Wraps the lower-level `BunqClient` (which only knows GET/POST/PUT/signing)
with the verbs the executor / rules engine actually want:

    list_sub_accounts()          -> [SubAccount, ...]
    transfer(from_id, to_id, eur, description) -> payment_id
    list_cards()                 -> [CardInfo, ...]
    freeze_card(card_id)         -> bool
    unfreeze_card(card_id)       -> bool
    recent_payments(account_id, since=None) -> [...]

All sub-account lookups are by *description* (case-insensitive) — that's
what the LLM emits and what the user actually says out loud.

We cache the singleton client + account list to avoid hammering /session-server
(rate limit: 1 / 30s).
"""

from __future__ import annotations

import os
import sys
import threading
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

# Allow `from bunq_client import BunqClient` from project root.
ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from bunq_client import BunqClient  # noqa: E402  (sys.path mutation above)

from .models import CardInfo, SubAccount  # noqa: E402

_client_lock = threading.Lock()
_client: BunqClient | None = None
_accounts_cache: list[dict] | None = None
_accounts_cached_at: float = 0.0
ACCOUNTS_TTL_SECONDS = 5.0  # short — we want balances fresh for the demo


# ---------------------------------------------------------------------------
# Client bootstrap
# ---------------------------------------------------------------------------


def get_client() -> BunqClient:
    global _client
    with _client_lock:
        if _client is None:
            api_key = os.getenv("BUNQ_API_KEY", "").strip()
            if not api_key:
                raise RuntimeError(
                    "BUNQ_API_KEY missing — set it in .env or export it before starting the backend."
                )
            c = BunqClient(api_key=api_key, sandbox=True)
            c.authenticate()
            _client = c
        return _client


# ---------------------------------------------------------------------------
# Sub-account helpers
# ---------------------------------------------------------------------------


def _raw_accounts(force: bool = False) -> list[dict]:
    """List all MonetaryAccountBank items, lightly cached."""
    global _accounts_cache, _accounts_cached_at
    now = time.time()
    if not force and _accounts_cache is not None and now - _accounts_cached_at < ACCOUNTS_TTL_SECONDS:
        return _accounts_cache
    c = get_client()
    items = c.get(f"user/{c.user_id}/monetary-account-bank")
    accounts = [item["MonetaryAccountBank"] for item in items if "MonetaryAccountBank" in item]
    accounts = [a for a in accounts if a.get("status") == "ACTIVE"]
    _accounts_cache = accounts
    _accounts_cached_at = now
    return accounts


def invalidate_account_cache() -> None:
    global _accounts_cache, _accounts_cached_at
    _accounts_cache = None
    _accounts_cached_at = 0.0


def list_sub_accounts() -> list[SubAccount]:
    out: list[SubAccount] = []
    for a in _raw_accounts():
        ibans = [al["value"] for al in a.get("alias", []) if al.get("type") == "IBAN"]
        bal = a.get("balance", {}) or {}
        out.append(
            SubAccount(
                id=a["id"],
                description=a.get("description") or f"account-{a['id']}",
                balance_eur=float(bal.get("value", "0") or 0),
                iban=ibans[0] if ibans else None,
                color=_color_from_setting(a.get("setting") or {}),
            )
        )
    return out


def _color_from_setting(setting: dict) -> str | None:
    c = setting.get("color")
    if isinstance(c, str) and c.startswith("#"):
        return c
    return None


def find_sub_account(name: str) -> SubAccount | None:
    """Case-insensitive description match."""
    target = name.strip().lower()
    for sa in list_sub_accounts():
        if sa.description.lower() == target:
            return sa
    # second pass: substring match
    for sa in list_sub_accounts():
        if target in sa.description.lower():
            return sa
    return None


def create_sub_account(description: str, color: str | None = None) -> SubAccount:
    c = get_client()
    body: dict[str, Any] = {"currency": "EUR", "description": description}
    if color or _color_for(description):
        body["setting"] = {"color": color or _color_for(description)}
    c.post(f"user/{c.user_id}/monetary-account-bank", body)
    invalidate_account_cache()
    sa = find_sub_account(description)
    if not sa:
        raise RuntimeError(f"Created sub-account {description!r} but couldn't read it back")
    return sa


def find_or_create_sub_account(name: str, color: str | None = None) -> tuple[SubAccount, bool]:
    """Return (sub_account, created_just_now)."""
    existing = find_sub_account(name)
    if existing:
        return existing, False
    sa = create_sub_account(name.strip().title(), color=color)
    return sa, True


# Friendly colors for common sub-account names. Falls back to a rotating palette.
_NAME_COLOR_MAP = {
    "groceries": "#34c759",
    "travel": "#ff9500",
    "emergency": "#ff3b30",
    "entertainment": "#af52de",
    "savings": "#0a84ff",
    "rent": "#8e8e93",
    "tax": "#ffcc00",
    "investments": "#5ac8fa",
    "fun": "#ff2d92",
    "main": "#0a84ff",
}
_PALETTE_FALLBACK = [
    "#34c759", "#ff9500", "#ff3b30", "#af52de",
    "#0a84ff", "#5ac8fa", "#ffcc00", "#ff2d92",
]


def _color_for(name: str) -> str:
    key = name.strip().lower()
    if key in _NAME_COLOR_MAP:
        return _NAME_COLOR_MAP[key]
    h = sum(ord(ch) for ch in key)
    return _PALETTE_FALLBACK[h % len(_PALETTE_FALLBACK)]


# ---------------------------------------------------------------------------
# Transfers
# ---------------------------------------------------------------------------


def transfer_between(
    from_account_id: int,
    to_account_id: int,
    amount_eur: float,
    description: str = "Vox transfer",
) -> int:
    """Internal transfer between two of the user's own sub-accounts.

    Implemented as a normal Payment to the destination's IBAN — bunq treats
    it as an internal SCT and it shows up immediately on both sides.
    """
    if amount_eur <= 0:
        raise ValueError("amount must be positive")

    c = get_client()
    accounts = {a["id"]: a for a in _raw_accounts()}
    if from_account_id not in accounts:
        raise ValueError(f"unknown source account {from_account_id}")
    if to_account_id not in accounts:
        raise ValueError(f"unknown destination account {to_account_id}")
    dest = accounts[to_account_id]
    dest_iban = next((al for al in dest.get("alias", []) if al.get("type") == "IBAN"), None)
    if not dest_iban:
        raise RuntimeError(f"destination account {to_account_id} has no IBAN")

    resp = c.post(
        f"user/{c.user_id}/monetary-account/{from_account_id}/payment",
        {
            "amount": {"value": f"{amount_eur:.2f}", "currency": "EUR"},
            "counterparty_alias": {
                "type": "IBAN",
                "value": dest_iban["value"],
                "name": dest_iban.get("name") or dest.get("description") or "self",
            },
            "description": description[:140],
        },
    )
    invalidate_account_cache()
    return int(resp[0]["Id"]["id"])


# ---------------------------------------------------------------------------
# Cards
# ---------------------------------------------------------------------------


def list_cards() -> list[CardInfo]:
    c = get_client()
    items = c.get(f"user/{c.user_id}/card")
    out: list[CardInfo] = []
    for item in items:
        # bunq returns these under various keys depending on card type
        for key in ("CardDebit", "CardCredit", "Card"):
            if key in item:
                card = item[key]
                out.append(
                    CardInfo(
                        id=card["id"],
                        label=(card.get("second_line") or card.get("description") or f"card-{card['id']}").strip(),
                        status=card.get("status") or "UNKNOWN",
                        type=card.get("type"),
                    )
                )
                break
    return out


def find_card(label: str) -> CardInfo | None:
    target = label.strip().lower()
    cards = list_cards()
    for card in cards:
        if card.label.lower() == target:
            return card
    for card in cards:
        if target in card.label.lower():
            return card
    return None


def freeze_card(card_id: int) -> bool:
    """Set card status to DEACTIVATED. Returns True if bunq accepted it.

    Sandbox sometimes refuses real status changes — caller should be
    prepared to fall back to a 'soft freeze' state in our DB.
    """
    c = get_client()
    try:
        c.put(f"user/{c.user_id}/card/{card_id}", {"status": "DEACTIVATED"})
        return True
    except Exception:
        return False


def unfreeze_card(card_id: int) -> bool:
    c = get_client()
    try:
        c.put(f"user/{c.user_id}/card/{card_id}", {"status": "ACTIVE"})
        return True
    except Exception:
        return False


# ---------------------------------------------------------------------------
# Payment history (used by the rules engine for spend windows)
# ---------------------------------------------------------------------------


def recent_payments(account_id: int, since: datetime | None = None, limit: int = 50) -> list[dict]:
    """Return outgoing payments newer than `since`, normalised to dicts."""
    c = get_client()
    items = c.get(
        f"user/{c.user_id}/monetary-account/{account_id}/payment",
        params={"count": limit},
    )
    out: list[dict] = []
    for item in items:
        p = item.get("Payment", {})
        if not p:
            continue
        amt = float((p.get("amount") or {}).get("value", "0") or 0)
        created = p.get("created")
        ts = _parse_bunq_ts(created) if created else None
        if since and ts and ts < since:
            continue
        out.append(
            {
                "id": p.get("id"),
                "amount_eur": amt,
                "description": p.get("description") or "",
                "counterparty_name": ((p.get("counterparty_alias") or {}).get("display_name") or ""),
                "created": ts.isoformat() if ts else created,
                "type": p.get("type"),
            }
        )
    return out


def spend_in_window(
    account_id: int, merchant_match: str, window_days: int
) -> tuple[float, list[dict]]:
    """Sum outgoing payments matching `merchant_match` in the last window."""
    since = datetime.now(timezone.utc) - timedelta(days=window_days)
    target = merchant_match.lower()
    matched: list[dict] = []
    total = 0.0
    for p in recent_payments(account_id, since=since, limit=200):
        if p["amount_eur"] >= 0:  # only outgoing (negative amounts in bunq)
            continue
        haystack = f"{p['description']} {p['counterparty_name']}".lower()
        if target in haystack:
            matched.append(p)
            total += abs(p["amount_eur"])
    return total, matched


# ---------------------------------------------------------------------------
# Webhook setup
# ---------------------------------------------------------------------------


def register_callback(callback_url: str) -> None:
    """Idempotently register PAYMENT + MUTATION webhooks at `callback_url`."""
    c = get_client()
    c.post(
        f"user/{c.user_id}/notification-filter-url",
        {
            "notification_filters": [
                {"category": "PAYMENT", "notification_target": callback_url},
                {"category": "MUTATION", "notification_target": callback_url},
            ]
        },
    )


def list_callbacks() -> list[dict]:
    c = get_client()
    items = c.get(f"user/{c.user_id}/notification-filter-url")
    out: list[dict] = []
    for item in items:
        nf = item.get("NotificationFilterUrl", {})
        for f in nf.get("notification_filters", []):
            out.append(
                {
                    "category": f.get("category"),
                    "target": f.get("notification_target"),
                }
            )
    return out


# ---------------------------------------------------------------------------
# Sandbox helpers
# ---------------------------------------------------------------------------


def request_sandbox_funds(account_id: int, amount_eur: float, description: str = "Sandbox top-up") -> None:
    """Pull test money from sugardaddy@bunq.com (sandbox only)."""
    c = get_client()
    c.post(
        f"user/{c.user_id}/monetary-account/{account_id}/request-inquiry",
        {
            "amount_inquired": {"value": f"{amount_eur:.2f}", "currency": "EUR"},
            "counterparty_alias": {
                "type": "EMAIL",
                "value": "sugardaddy@bunq.com",
                "name": "Sugar Daddy",
            },
            "description": description[:140],
            "allow_bunqme": False,
        },
    )


# ---------------------------------------------------------------------------
# Utils
# ---------------------------------------------------------------------------


def _parse_bunq_ts(s: str) -> datetime:
    # bunq returns "YYYY-MM-DD HH:MM:SS.ffffff" in UTC
    try:
        return datetime.strptime(s, "%Y-%m-%d %H:%M:%S.%f").replace(tzinfo=timezone.utc)
    except ValueError:
        return datetime.strptime(s, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
