// Mirrors backend/models.py — keep in sync by hand.

export type ActionKind =
  | "transfer"
  | "recurring_split"
  | "conditional_freeze"
  | "transaction_limit_freeze";

export interface TransferAction {
  kind: "transfer";
  from_account: string;
  to_account: string;
  amount_eur: number;
  note?: string;
}

export interface RecurringSplitAction {
  kind: "recurring_split";
  trigger_match: string;
  percentage: number;
  to_account: string;
  note?: string;
}

export interface ConditionalFreezeAction {
  kind: "conditional_freeze";
  merchant_match: string;
  window_days: number;
  threshold_eur: number;
  card_label: string;
  note?: string;
}

export interface TransactionLimitFreezeAction {
  kind: "transaction_limit_freeze";
  max_tx_eur: number;
  card_label: string;
  from_account?: string | null;
  merchant_match?: string | null;
  note?: string;
}

export type Action =
  | TransferAction
  | RecurringSplitAction
  | ConditionalFreezeAction
  | TransactionLimitFreezeAction;

export interface Plan {
  actions: Action[];
  summary: string;
  warnings: string[];
}

export interface SubAccount {
  id: number;
  description: string;
  balance_eur: number;
  iban: string | null;
  color: string | null;
}

export interface CardInfo {
  id: number;
  label: string;
  status: string;
  type: string | null;
}

export interface RuleView {
  id: number;
  kind: ActionKind;
  summary: string;
  config: Record<string, unknown>;
  active: boolean;
  created_at: string;
  fired_count: number;
}

export interface ExecutionResult {
  action_index: number;
  kind: string;
  ok: boolean;
  detail: string;
  bunq_payment_id: number | null;
  rule_id: number | null;
}

export interface ExecuteResponse {
  results: ExecutionResult[];
}

export interface FiringEvent {
  rule_id: number;
  rule_kind: string;
  summary: string;
  detail: Record<string, unknown>;
}

export interface Health {
  ok: boolean;
  bunq_authenticated: boolean;
  bunq_error: string | null;
  llm_provider: string | null;
  transcribe_provider: string | null;
  transcribe_mode: "browser" | "server";
}
