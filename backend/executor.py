"""
Execute a `Plan` produced by the planner.

Immediate actions (transfer) hit the bunq API directly.
Standing rules (recurring_split, conditional_freeze) get persisted into
sqlite — the rules engine evaluates them on every webhook.
"""

from __future__ import annotations

from typing import Iterable

from . import bunq_service, db
from .models import (
    Action,
    ConditionalFreezeAction,
    ExecuteResponse,
    ExecutionResult,
    Plan,
    RecurringSplitAction,
    TransactionLimitFreezeAction,
    TransferAction,
)


def execute_plan(plan: Plan, selected_indexes: Iterable[int] | None = None) -> ExecuteResponse:
    indexes = set(selected_indexes) if selected_indexes is not None else set(range(len(plan.actions)))
    results: list[ExecutionResult] = []
    for i, action in enumerate(plan.actions):
        if i not in indexes:
            continue
        results.append(_execute_one(i, action))
    return ExecuteResponse(results=results)


def _execute_one(index: int, action: Action) -> ExecutionResult:
    try:
        if isinstance(action, TransferAction):
            return _do_transfer(index, action)
        if isinstance(action, RecurringSplitAction):
            return _do_recurring(index, action)
        if isinstance(action, ConditionalFreezeAction):
            return _do_conditional(index, action)
        if isinstance(action, TransactionLimitFreezeAction):
            return _do_tx_limit(index, action)
        return ExecutionResult(
            action_index=index,
            kind=getattr(action, "kind", "?"),
            ok=False,
            detail=f"unknown action type: {type(action).__name__}",
        )
    except Exception as e:
        return ExecutionResult(
            action_index=index,
            kind=getattr(action, "kind", "?"),
            ok=False,
            detail=f"{type(e).__name__}: {e}",
        )


# ---------------------------------------------------------------------------
# Per-action handlers
# ---------------------------------------------------------------------------


def _do_transfer(index: int, a: TransferAction) -> ExecutionResult:
    src = bunq_service.find_sub_account(a.from_account)
    if not src:
        return _fail(
            index,
            a.kind,
            f"source sub-account {a.from_account!r} doesn't exist (and we won't auto-create one with no funds)",
        )

    dst, dst_created = bunq_service.find_or_create_sub_account(a.to_account)
    if src.id == dst.id:
        return _fail(index, a.kind, "source and destination are the same account")
    if src.balance_eur < a.amount_eur:
        return _fail(
            index,
            a.kind,
            f"insufficient funds: {src.description} has €{src.balance_eur:.2f}, need €{a.amount_eur:.2f}",
        )

    note = a.note or f"Vox: {src.description} -> {dst.description}"
    payment_id = bunq_service.transfer_between(src.id, dst.id, a.amount_eur, note)
    suffix = f" (created sub-account {dst.description!r})" if dst_created else ""
    return ExecutionResult(
        action_index=index,
        kind=a.kind,
        ok=True,
        detail=f"Moved €{a.amount_eur:.2f} from {src.description} to {dst.description}.{suffix}",
        bunq_payment_id=payment_id,
    )


def _do_recurring(index: int, a: RecurringSplitAction) -> ExecutionResult:
    dst, dst_created = bunq_service.find_or_create_sub_account(a.to_account)
    summary = (
        f"When an incoming payment matches “{a.trigger_match}”, route "
        f"{a.percentage:g}% to {dst.description}."
    )
    if dst_created:
        summary += f" (Created sub-account {dst.description!r}.)"
    rule_id = db.insert_rule(
        kind=a.kind,
        summary=summary,
        config={
            "trigger_match": a.trigger_match,
            "percentage": a.percentage,
            "to_account_id": dst.id,
            "to_account_name": dst.description,
        },
    )
    return ExecutionResult(
        action_index=index,
        kind=a.kind,
        ok=True,
        detail=f"Armed rule #{rule_id}: {summary}",
        rule_id=rule_id,
    )


def _do_conditional(index: int, a: ConditionalFreezeAction) -> ExecutionResult:
    card = bunq_service.find_card(a.card_label)
    if not card:
        return _fail(index, a.kind, f"unknown card label {a.card_label!r}")
    summary = (
        f"If spend matching “{a.merchant_match}” over {a.window_days}d > €{a.threshold_eur:.2f}, "
        f"freeze card {card.label!r}."
    )
    rule_id = db.insert_rule(
        kind=a.kind,
        summary=summary,
        config={
            "merchant_match": a.merchant_match,
            "window_days": a.window_days,
            "threshold_eur": a.threshold_eur,
            "card_id": card.id,
            "card_label": card.label,
        },
    )
    return ExecutionResult(
        action_index=index,
        kind=a.kind,
        ok=True,
        detail=f"Armed rule #{rule_id}: {summary}",
        rule_id=rule_id,
    )


def _do_tx_limit(index: int, a: TransactionLimitFreezeAction) -> ExecutionResult:
    card = bunq_service.find_card(a.card_label)
    if not card:
        return _fail(index, a.kind, f"unknown card label {a.card_label!r}")

    scope_account_id: int | None = None
    scope_account_name: str | None = None
    if a.from_account:
        sa = bunq_service.find_sub_account(a.from_account)
        if not sa:
            return _fail(
                index,
                a.kind,
                f"sub-account {a.from_account!r} not found — can't scope the rule",
            )
        scope_account_id = sa.id
        scope_account_name = sa.description

    scope_bits = []
    if scope_account_name:
        scope_bits.append(f"on {scope_account_name}")
    if a.merchant_match:
        scope_bits.append(f"matching “{a.merchant_match}”")
    scope = (" " + " ".join(scope_bits)) if scope_bits else ""

    summary = (
        f"If any single transaction{scope} ≥ €{a.max_tx_eur:.2f}, "
        f"freeze card {card.label!r}."
    )
    rule_id = db.insert_rule(
        kind=a.kind,
        summary=summary,
        config={
            "max_tx_eur": a.max_tx_eur,
            "card_id": card.id,
            "card_label": card.label,
            "from_account_id": scope_account_id,
            "from_account_name": scope_account_name,
            "merchant_match": (a.merchant_match or "").strip() or None,
        },
    )
    return ExecutionResult(
        action_index=index,
        kind=a.kind,
        ok=True,
        detail=f"Armed rule #{rule_id}: {summary}",
        rule_id=rule_id,
    )


def _fail(index: int, kind: str, msg: str) -> ExecutionResult:
    return ExecutionResult(action_index=index, kind=kind, ok=False, detail=msg)
