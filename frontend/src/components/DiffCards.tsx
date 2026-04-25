import { AnimatePresence, motion } from "framer-motion";
import type {
  Action,
  ConditionalFreezeAction,
  ExecutionResult,
  RecurringSplitAction,
  SubAccount,
  TransactionLimitFreezeAction,
  TransferAction,
} from "../lib/types";

// ---------------------------------------------------------------------------
// Container — animates each diff card in with a stagger.
// ---------------------------------------------------------------------------

interface PanelProps {
  actions: Action[];
  selected: Set<number>;
  toggle: (i: number) => void;
  results: Map<number, ExecutionResult>;
  accounts: SubAccount[];
  busy: boolean;
}

export function DiffCards({
  actions,
  selected,
  toggle,
  results,
  accounts,
  busy,
}: PanelProps) {
  return (
    <div className="flex flex-col gap-3">
      <AnimatePresence initial={false}>
        {actions.map((a, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 14, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ delay: i * 0.07, type: "spring", stiffness: 220, damping: 24 }}
          >
            <DiffCard
              action={a}
              index={i}
              checked={selected.has(i)}
              onToggle={() => toggle(i)}
              result={results.get(i)}
              accounts={accounts}
              disabled={busy}
            />
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Single card — chooses sub-component by kind.
// ---------------------------------------------------------------------------

interface CardProps {
  action: Action;
  index: number;
  checked: boolean;
  onToggle: () => void;
  result?: ExecutionResult;
  accounts: SubAccount[];
  disabled?: boolean;
}

function DiffCard({
  action,
  index,
  checked,
  onToggle,
  result,
  accounts,
  disabled,
}: CardProps) {
  const ranOk = result?.ok === true;
  const ranBad = result?.ok === false;

  return (
    <div
      className={[
        "panel relative overflow-hidden p-4 transition",
        ranOk ? "border-accent/60" : ranBad ? "border-bad/60" : "",
        !checked ? "opacity-50" : "",
      ].join(" ")}
    >
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <input
            type="checkbox"
            checked={checked}
            onChange={onToggle}
            disabled={disabled || !!result}
            className="h-4 w-4 accent-accent"
          />
          <span className="chip">
            <KindIcon kind={action.kind} />
            {labelFor(action.kind)}
          </span>
          <span className="text-xs text-muted">action {index + 1}</span>
        </div>
        {result && (
          <span
            className={[
              "chip",
              ranOk ? "border-accent/60 text-accent" : "border-bad/60 text-bad",
            ].join(" ")}
          >
            {ranOk ? "executed" : "failed"}
          </span>
        )}
      </div>

      {action.kind === "transfer" && (
        <TransferBody action={action} accounts={accounts} />
      )}
      {action.kind === "recurring_split" && (
        <RecurringBody action={action} />
      )}
      {action.kind === "conditional_freeze" && (
        <ConditionalBody action={action} />
      )}
      {action.kind === "transaction_limit_freeze" && (
        <TxLimitBody action={action} />
      )}

      {result && (
        <p className={`mt-3 text-xs ${ranOk ? "text-accent" : "text-bad"}`}>
          {result.detail}
        </p>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Kind 1: Transfer — show before/after balance bars + a flying euro pill.
// ---------------------------------------------------------------------------

function TransferBody({
  action,
  accounts,
}: {
  action: TransferAction;
  accounts: SubAccount[];
}) {
  const src = matchAccount(accounts, action.from_account);
  const dst = matchAccount(accounts, action.to_account);
  const srcAfter = (src?.balance_eur ?? 0) - action.amount_eur;
  const dstAfter = (dst?.balance_eur ?? 0) + action.amount_eur;

  return (
    <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
      <BalanceBlock
        label={action.from_account}
        before={src?.balance_eur ?? 0}
        after={srcAfter}
        color={src?.color}
        negative
        unknown={!src}
      />
      <FlyingPill amount={action.amount_eur} />
      <BalanceBlock
        label={action.to_account}
        before={dst?.balance_eur ?? 0}
        after={dstAfter}
        color={dst?.color}
        unknown={!dst}
      />
    </div>
  );
}

function BalanceBlock({
  label,
  before,
  after,
  color,
  negative,
  unknown,
}: {
  label: string;
  before: number;
  after: number;
  color?: string | null;
  negative?: boolean;
  unknown?: boolean;
}) {
  return (
    <div className="panel-tight p-3">
      <div className="flex items-center gap-2 text-xs text-muted">
        <span
          className="inline-block h-2 w-2 rounded-full"
          style={{ background: color || "#52525b" }}
        />
        <span className="truncate">{label}</span>
        {unknown && <span className="text-bad">(?)</span>}
      </div>
      <div className="mt-1 flex items-baseline gap-2">
        <span className="text-sm text-muted line-through">
          €{fmt(before)}
        </span>
        <motion.span
          layout
          className={`text-lg font-semibold ${negative ? "text-warn" : "text-accent"}`}
        >
          €{fmt(after)}
        </motion.span>
      </div>
    </div>
  );
}

function FlyingPill({ amount }: { amount: number }) {
  return (
    <div className="relative flex h-10 w-24 items-center justify-center">
      <motion.div
        initial={{ x: -28, opacity: 0 }}
        animate={{ x: 28, opacity: 1 }}
        transition={{ duration: 0.9, ease: "easeOut" }}
        className="rounded-full border border-accent/40 bg-accent/10 px-3 py-1 text-xs font-semibold text-accent"
      >
        €{fmt(amount)} →
      </motion.div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Kind 2: Recurring split — pipe diagram.
// ---------------------------------------------------------------------------

function RecurringBody({ action }: { action: RecurringSplitAction }) {
  return (
    <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
      <div className="panel-tight p-3">
        <div className="text-xs text-muted">when incoming matches</div>
        <div className="mt-1 truncate font-mono text-sm text-ink">
          “{action.trigger_match}”
        </div>
      </div>
      <div className="flex flex-col items-center gap-1 text-xs text-accent">
        <Splitter />
        <span className="rounded-full border border-accent/40 bg-accent/10 px-2 py-0.5 font-semibold">
          {action.percentage}%
        </span>
      </div>
      <div className="panel-tight p-3">
        <div className="text-xs text-muted">route to</div>
        <div className="mt-1 truncate text-sm font-semibold text-accent">
          {action.to_account}
        </div>
      </div>
    </div>
  );
}

function Splitter() {
  return (
    <svg width={56} height={28} viewBox="0 0 56 28" fill="none">
      <path
        d="M2 14 H22 M34 14 H54"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeDasharray="2 4"
      />
      <circle cx="28" cy="14" r="6" fill="currentColor" fillOpacity="0.15" stroke="currentColor" />
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Kind 3: Conditional freeze — thermometer + card preview.
// ---------------------------------------------------------------------------

function ConditionalBody({ action }: { action: ConditionalFreezeAction }) {
  return (
    <div className="grid grid-cols-[1fr_auto] items-center gap-3">
      <div className="panel-tight p-3">
        <div className="text-xs text-muted">
          if spend on “{action.merchant_match}” over {action.window_days}d &gt;
        </div>
        <div className="mt-1 flex items-baseline gap-2">
          <span className="text-lg font-semibold text-warn">
            €{fmt(action.threshold_eur)}
          </span>
          <span className="text-xs text-muted">freeze trigger</span>
        </div>
        <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-line">
          <div
            className="h-full bg-gradient-to-r from-accent via-warn to-bad"
            style={{ width: "30%" }}
          />
        </div>
      </div>
      <CardPreview label={action.card_label} />
    </div>
  );
}

function CardPreview({ label }: { label: string }) {
  return (
    <div className="relative h-20 w-32 rounded-xl border border-line bg-gradient-to-br from-zinc-800 to-zinc-900 p-2 shadow-inner">
      <div className="text-[10px] uppercase tracking-wider text-muted">
        bunq · card
      </div>
      <div className="mt-1 truncate text-xs font-semibold">{label}</div>
      <div className="absolute bottom-2 right-2 text-[10px] text-muted">
        will lock
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Kind 4: Transaction-limit freeze — single-tx security guardrail.
// ---------------------------------------------------------------------------

function TxLimitBody({ action }: { action: TransactionLimitFreezeAction }) {
  const scopes: string[] = [];
  if (action.from_account) scopes.push(`from ${action.from_account}`);
  if (action.merchant_match) scopes.push(`matching “${action.merchant_match}”`);

  return (
    <div className="grid grid-cols-[1fr_auto] items-center gap-3">
      <div className="panel-tight p-3">
        <div className="text-xs text-muted">
          if any single transaction{scopes.length ? " " + scopes.join(" · ") : ""} ≥
        </div>
        <div className="mt-1 flex items-baseline gap-2">
          <span className="text-lg font-semibold text-warn">
            €{fmt(action.max_tx_eur)}
          </span>
          <span className="text-xs text-muted">freeze immediately</span>
        </div>
        <div className="mt-2 flex items-center gap-1 text-[10px] uppercase tracking-wider text-muted">
          <ShieldIcon /> security guardrail · one-shot
        </div>
      </div>
      <CardPreview label={action.card_label} />
    </div>
  );
}

function ShieldIcon() {
  return (
    <svg viewBox="0 0 24 24" width={11} height={11} fill="none" stroke="currentColor" strokeWidth={2}>
      <path d="M12 3l8 3v6c0 5-3.5 8.5-8 9-4.5-.5-8-4-8-9V6l8-3z" strokeLinejoin="round" />
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function matchAccount(accounts: SubAccount[], name: string): SubAccount | undefined {
  const t = name.trim().toLowerCase();
  return (
    accounts.find((a) => a.description.toLowerCase() === t) ||
    accounts.find((a) => a.description.toLowerCase().includes(t))
  );
}

function fmt(n: number): string {
  return n.toLocaleString("en-IE", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function labelFor(kind: Action["kind"]): string {
  switch (kind) {
    case "transfer":
      return "transfer";
    case "recurring_split":
      return "recurring rule";
    case "conditional_freeze":
      return "guardrail";
    case "transaction_limit_freeze":
      return "tx limit";
  }
}

function KindIcon({ kind }: { kind: Action["kind"] }) {
  const cls = "h-3.5 w-3.5 text-accent";
  if (kind === "transfer")
    return (
      <svg viewBox="0 0 24 24" className={cls} fill="none" stroke="currentColor" strokeWidth={2}>
        <path d="M3 12h14M13 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  if (kind === "recurring_split")
    return (
      <svg viewBox="0 0 24 24" className={cls} fill="none" stroke="currentColor" strokeWidth={2}>
        <path d="M21 12a9 9 0 1 1-3-6.7L21 8" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M21 3v5h-5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  if (kind === "transaction_limit_freeze")
    return (
      <svg viewBox="0 0 24 24" className={cls} fill="none" stroke="currentColor" strokeWidth={2}>
        <path
          d="M12 3l8 3v6c0 5-3.5 8.5-8 9-4.5-.5-8-4-8-9V6l8-3z"
          strokeLinejoin="round"
        />
        <path d="M9 12l2 2 4-4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  return (
    <svg viewBox="0 0 24 24" className={cls} fill="none" stroke="currentColor" strokeWidth={2}>
      <rect x="4" y="11" width="16" height="9" rx="2" />
      <path d="M8 11V7a4 4 0 1 1 8 0v4" />
    </svg>
  );
}
