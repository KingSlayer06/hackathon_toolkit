import { AnimatePresence, motion } from "framer-motion";
import type { RuleView } from "../lib/types";

interface Props {
  rules: RuleView[] | null;
  onDelete: (id: number) => void;
}

export function RulesPanel({ rules, onDelete }: Props) {
  return (
    <section className="panel p-4">
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-muted">
          Active rules
        </h2>
        <span className="text-xs text-muted">{rules?.length ?? 0}</span>
      </header>

      {!rules?.length && (
        <p className="text-sm text-muted">
          No rules armed. Speak a recurring or conditional command to add one.
        </p>
      )}

      <ul className="flex flex-col gap-2">
        <AnimatePresence initial={false}>
          {rules?.map((r) => (
            <motion.li
              key={r.id}
              layout
              initial={{ opacity: 0, scale: 0.96 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.96 }}
              className="panel-tight p-3"
            >
              <div className="mb-1 flex items-center gap-2">
                <span className="chip">
                  <Pulse />
                  {ruleKindLabel(r.kind)}
                </span>
                {r.fired_count > 0 && (
                  <span className="chip text-accent">
                    fired {r.fired_count}×
                  </span>
                )}
                <button
                  type="button"
                  onClick={() => onDelete(r.id)}
                  className="ml-auto text-xs text-muted hover:text-bad"
                  aria-label="Disarm rule"
                >
                  disarm
                </button>
              </div>
              <p className="text-sm text-ink">{r.summary}</p>
              <p className="mt-1 text-[10px] text-muted">
                #{r.id} · armed {timeAgo(r.created_at)}
              </p>
            </motion.li>
          ))}
        </AnimatePresence>
      </ul>
    </section>
  );
}

function Pulse() {
  return (
    <span className="relative inline-flex h-2 w-2">
      <span className="absolute inset-0 animate-ping rounded-full bg-accent/60" />
      <span className="relative inline-flex h-2 w-2 rounded-full bg-accent" />
    </span>
  );
}

function ruleKindLabel(kind: string): string {
  switch (kind) {
    case "recurring_split":
      return "recurring";
    case "conditional_freeze":
      return "guardrail";
    case "transaction_limit_freeze":
      return "tx limit";
    default:
      return kind;
  }
}

function timeAgo(iso: string): string {
  const t = new Date(iso + (iso.endsWith("Z") ? "" : "Z")).getTime();
  const diff = (Date.now() - t) / 1000;
  if (diff < 60) return `${Math.floor(diff)}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  return `${Math.floor(diff / 3600)}h ago`;
}
