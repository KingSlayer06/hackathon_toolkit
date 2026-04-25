"""
Create a sandbox debit card.

bunq sandbox quirks this script handles:
- `name_on_card` MUST be one of the values returned by
  GET /user/{id}/card-name-user. Free-text is rejected with
  USER_CARD_NAME_NOT_AVAILABLE.
- `alias` and `pin_code` are NOT accepted on /card-debit creation in sandbox
  (you set the PIN later via PUT /card/{id}). Including them returns 400.
- `second_line` is capped at 21 chars.
- Sandbox usually allows ~3 cards per user; past that you get
  USER_HAS_REACHED_CARD_LIMIT.

Usage:
    python 08_create_card.py "Travel"
    python 08_create_card.py "Bar" --type MASTERCARD
    python 08_create_card.py "Online" --name-index 1   # pick 2nd allowed name
"""

from __future__ import annotations

import argparse
import json
import os
import sys

from dotenv import load_dotenv

from bunq_client import BunqClient


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("label", help="Second-line label shown in the bunq app (max 21 chars)")
    p.add_argument(
        "--type",
        choices=[
            "MAESTRO_DEBIT",
            "MASTERCARD_DEBIT",
            "MASTERCARD_TRAVEL",
            "MASTERCARD_BUSINESS",
            "MASTERCARD_GREEN",
            "MASTERCARD_TRANSLINK",
            "MASTERCARD_CREDIT_PREPAID",
            "MASTERCARD_METAL",
        ],
        default="MASTERCARD_DEBIT",
        help="bunq product_type (default MASTERCARD_DEBIT — Maestro is retired)",
    )
    p.add_argument(
        "--name-index",
        type=int,
        default=0,
        help="Which allowed name to use from /card-name-user (default 0 = first)",
    )
    args = p.parse_args()

    load_dotenv()
    api_key = os.getenv("BUNQ_API_KEY", "").strip()
    if not api_key:
        sys.exit("BUNQ_API_KEY missing — set it in .env")

    c = BunqClient(api_key=api_key, sandbox=True)
    c.authenticate()

    allowed = _fetch_allowed_names(c)
    if not allowed:
        sys.exit(
            "bunq returned no allowed names for this user — usually means "
            "the sandbox user is too fresh; wait a minute and retry."
        )
    if args.name_index >= len(allowed):
        sys.exit(
            f"--name-index {args.name_index} out of range; "
            f"only {len(allowed)} allowed name(s): {allowed}"
        )
    name_on_card = allowed[args.name_index]

    # `type` is the legacy short enum (MAESTRO / MASTERCARD); `product_type`
    # is the new long enum. Some endpoint versions still validate `type`.
    legacy_type = "MAESTRO" if args.type.startswith("MAESTRO") else "MASTERCARD"
    body = {
        "second_line": args.label[:21],
        "name_on_card": name_on_card,
        "type": legacy_type,
        "product_type": args.type,
    }

    print(f"Creating {args.type} card with name_on_card={name_on_card!r}, "
          f"second_line={body['second_line']!r}…")
    resp = c.post(f"user/{c.user_id}/card-debit", body)
    card_id = _extract_card_id(resp)
    print(f"\n✓ Created card #{card_id}")

    print("\nFresh card list:")
    cards = c.get(f"user/{c.user_id}/card")
    for item in cards:
        for key in ("CardDebit", "CardCredit", "Card"):
            if key in item:
                card = item[key]
                print(
                    f"  #{card['id']:>6}  "
                    f"{(card.get('second_line') or '').ljust(22)}"
                    f"{card.get('type', '?'):<12}"
                    f"{card.get('status', '?')}"
                )
                break


def _extract_card_id(resp: list) -> int:
    """bunq returns one of:
      [{"Id": {"id": 123}}]                  # most POSTs
      [{"CardDebit": {"id": 123, ...}}]      # /card-debit
      [{"id": 123, ...}]                     # very rare
    """
    if not resp:
        raise RuntimeError("bunq returned an empty response")
    item = resp[0]
    if "Id" in item and isinstance(item["Id"], dict):
        return int(item["Id"]["id"])
    for key in ("CardDebit", "CardCredit", "Card"):
        if key in item and isinstance(item[key], dict) and "id" in item[key]:
            return int(item[key]["id"])
    if "id" in item:
        return int(item["id"])
    raise RuntimeError(f"couldn't find card id in response: {item!r}")


def _fetch_allowed_names(c: BunqClient) -> list[str]:
    """bunq pre-computes a small set of allowed values for `name_on_card`
    based on the user's display_name (e.g. ['JOHN DOE', 'J DOE', 'JOHN'])."""
    items = c.get(f"user/{c.user_id}/card-name")
    out: list[str] = []
    for item in items:
        # bunq wraps these inconsistently: CardUserNameArray (current),
        # CardName / UserCardName (older docs). Accept any.
        wrapper = (
            item.get("CardUserNameArray")
            or item.get("CardName")
            or item.get("UserCardName")
            or {}
        )
        for n in wrapper.get("possible_card_name_array", []) or []:
            if isinstance(n, str):
                out.append(n)
    if out:
        return out
    print("DEBUG raw /card-name response:", json.dumps(items, indent=2), file=sys.stderr)
    return out


if __name__ == "__main__":
    main()
