"""
Schema for Plans the LLM emits and Rules we persist.

The LLM produces a `Plan` with one or more `Action`s. Actions are either
*immediate* (transfer money now) or *standing rules* (recurring split,
conditional freeze) that our rules engine evaluates on every bunq webhook.
"""

from __future__ import annotations

from typing import Annotated, Literal, Union

from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# Actions emitted by the planner
# ---------------------------------------------------------------------------


class TransferAction(BaseModel):
    """Move money between two sub-accounts immediately."""

    kind: Literal["transfer"] = "transfer"
    from_account: str = Field(..., description="Source sub-account description, e.g. 'Groceries'.")
    to_account: str = Field(..., description="Destination sub-account description, e.g. 'Travel'.")
    amount_eur: float = Field(..., gt=0, description="Amount in EUR. Must be positive.")
    note: str = Field("", description="Short human description.")


class RecurringSplitAction(BaseModel):
    """When an incoming payment whose description matches `trigger_match`
    arrives, instantly route `percentage`% of it into `to_account`."""

    kind: Literal["recurring_split"] = "recurring_split"
    trigger_match: str = Field(
        ...,
        description="Case-insensitive substring matched against incoming payment description "
        "(e.g. 'salary', 'paycheck').",
    )
    percentage: float = Field(..., gt=0, le=100, description="Percentage of incoming amount.")
    to_account: str = Field(..., description="Destination sub-account description.")
    note: str = Field("", description="Short human description.")


class ConditionalFreezeAction(BaseModel):
    """When cumulative spending matching `merchant_match` over the last
    `window_days` exceeds `threshold_eur`, freeze the card labelled `card_label`."""

    kind: Literal["conditional_freeze"] = "conditional_freeze"
    merchant_match: str = Field(
        ...,
        description="Case-insensitive substring matched against payment counterparty/description "
        "(e.g. 'bar', 'uber', 'amazon').",
    )
    window_days: int = Field(7, ge=1, le=90, description="Rolling window in days.")
    threshold_eur: float = Field(..., gt=0, description="Trigger threshold in EUR.")
    card_label: str = Field(..., description="Card label to freeze, e.g. 'entertainment'.")
    note: str = Field("", description="Short human description.")


class TransactionLimitFreezeAction(BaseModel):
    """Per-transaction security guardrail: if any single OUTGOING payment exceeds
    `max_tx_eur`, immediately freeze the card labelled `card_label`.

    Optional scopes narrow which transactions count:
    - `from_account`: only fire when the payment originates from this sub-account.
    - `merchant_match`: only fire when the merchant/description contains this substring.
    """

    kind: Literal["transaction_limit_freeze"] = "transaction_limit_freeze"
    max_tx_eur: float = Field(
        ...,
        gt=0,
        description="Single-transaction trigger threshold in EUR (>= this fires).",
    )
    card_label: str = Field(..., description="Card label to freeze when the rule fires.")
    from_account: str | None = Field(
        None,
        description="Optional sub-account name to scope the rule to (e.g. 'Main').",
    )
    merchant_match: str | None = Field(
        None,
        description="Optional case-insensitive substring matched against merchant/description.",
    )
    note: str = Field("", description="Short human description.")


Action = Annotated[
    Union[
        TransferAction,
        RecurringSplitAction,
        ConditionalFreezeAction,
        TransactionLimitFreezeAction,
    ],
    Field(discriminator="kind"),
]


class Plan(BaseModel):
    """The full structured plan emitted by the planner."""

    actions: list[Action]
    summary: str = Field(..., description="One-line natural-language summary.")
    warnings: list[str] = Field(
        default_factory=list,
        description="Anything the planner was unsure about or ignored.",
    )


# ---------------------------------------------------------------------------
# API request / response wrappers
# ---------------------------------------------------------------------------


class PlanRequest(BaseModel):
    text: str = Field(..., description="Natural language instruction from the user.")


class TranscribeResponse(BaseModel):
    text: str


class ExecuteRequest(BaseModel):
    plan: Plan
    selected_indexes: list[int] | None = Field(
        None,
        description="Optional subset of action indexes to execute. None = execute all.",
    )


class ExecutionResult(BaseModel):
    action_index: int
    kind: str
    ok: bool
    detail: str
    bunq_payment_id: int | None = None
    rule_id: int | None = None


class ExecuteResponse(BaseModel):
    results: list[ExecutionResult]


# ---------------------------------------------------------------------------
# Lightweight read models exposed to the frontend
# ---------------------------------------------------------------------------


class SubAccount(BaseModel):
    id: int
    description: str
    balance_eur: float
    iban: str | None = None
    color: str | None = None  # hex; nice-to-have for UI


class CardInfo(BaseModel):
    id: int
    label: str
    status: str
    type: str | None = None


class RuleView(BaseModel):
    id: int
    kind: str
    summary: str
    config: dict
    active: bool
    created_at: str
    fired_count: int


class FiringEvent(BaseModel):
    """Pushed to the frontend (via /events SSE) whenever a rule fires."""

    rule_id: int
    rule_kind: str
    summary: str
    detail: dict
