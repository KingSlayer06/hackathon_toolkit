"""
One-shot script to provision the demo bunq sub-accounts.

    python -m backend.setup_demo

Idempotent — safe to run multiple times.

Creates (if missing):
    Main           — €0    (top-up source)
    Groceries      — €450
    Travel         — €120
    Emergency      — €0
    Entertainment  — €200

Then registers the webhook URL (defaults to env BUNQ_CALLBACK_URL).
"""

from __future__ import annotations

import os
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from dotenv import load_dotenv

load_dotenv()

from backend import bunq_service  # noqa: E402

DESIRED = [
    ("Main", "#0a84ff", 0.0),
    ("Groceries", "#34c759", 450.0),
    ("Travel", "#ff9500", 120.0),
    ("Emergency", "#ff3b30", 0.0),
    ("Entertainment", "#af52de", 200.0),
]

TOPUP_BUDGET = 1000.0  # we'll request this from sugardaddy and fan out


def main() -> None:
    print("Authenticating with bunq sandbox...")
    bunq_service.get_client()

    existing = {sa.description.lower(): sa for sa in bunq_service.list_sub_accounts()}
    created: list[str] = []
    for name, color, _ in DESIRED:
        if name.lower() in existing:
            print(f"  ✓ {name} already exists (id={existing[name.lower()].id})")
            continue
        print(f"  + creating {name}…")
        sa = bunq_service.create_sub_account(name, color=color)
        created.append(name)
        time.sleep(0.4)  # respect rate limits
        existing[sa.description.lower()] = sa

    bunq_service.invalidate_account_cache()
    accounts = {sa.description.lower(): sa for sa in bunq_service.list_sub_accounts()}

    main = accounts.get("main")
    if not main:
        print("Main account missing — aborting funding.")
        return

    if main.balance_eur < TOPUP_BUDGET:
        topup = TOPUP_BUDGET - main.balance_eur
        print(f"Pulling €{topup:.2f} from sugardaddy into Main…")
        bunq_service.request_sandbox_funds(main.id, topup, "Vox demo topup")
        time.sleep(2.0)
        bunq_service.invalidate_account_cache()
        accounts = {sa.description.lower(): sa for sa in bunq_service.list_sub_accounts()}
        main = accounts["main"]

    for name, _, target in DESIRED:
        if name == "Main":
            continue
        sa = accounts.get(name.lower())
        if not sa:
            print(f"  ! {name} missing, skipping fund")
            continue
        delta = target - sa.balance_eur
        if delta <= 0.5:
            print(f"  ✓ {name} already has €{sa.balance_eur:.2f} (target €{target:.2f})")
            continue
        print(f"  → topping up {name} with €{delta:.2f}")
        try:
            bunq_service.transfer_between(main.id, sa.id, delta, f"Vox demo seed for {name}")
        except Exception as e:
            print(f"    ! failed: {e}")
        time.sleep(0.6)

    callback = os.getenv("BUNQ_CALLBACK_URL", "").strip()
    if callback:
        print(f"Registering webhook → {callback}")
        try:
            bunq_service.register_callback(callback)
        except Exception as e:
            print(f"  ! webhook registration failed: {e}")
    else:
        print("(skip webhook setup — no BUNQ_CALLBACK_URL set)")

    print("\nFinal state:")
    for sa in bunq_service.list_sub_accounts():
        print(f"  id={sa.id:<8}  {sa.description:<14}  €{sa.balance_eur:>8.2f}  iban={sa.iban}")

    print("\nCards on file:")
    for c in bunq_service.list_cards():
        print(f"  id={c.id:<8}  label={c.label!r:<30}  status={c.status}")
    if not bunq_service.list_cards():
        print("  (none — ping #help-bunq-api to provision a sandbox card)")


if __name__ == "__main__":
    main()
