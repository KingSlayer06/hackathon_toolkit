import { motion, AnimatePresence } from "framer-motion";
import type { SubAccount } from "../lib/types";

interface Props {
  accounts: SubAccount[] | null;
  highlight: Set<number>;
}

export function AccountsPanel({ accounts, highlight }: Props) {
  return (
    <section className="panel p-4">
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-muted">
          Sub-accounts
        </h2>
        <span className="text-xs text-muted">
          {accounts?.length ?? 0}
        </span>
      </header>

      {!accounts && <Skeleton />}
      {accounts && accounts.length === 0 && (
        <p className="text-sm text-muted">
          No sub-accounts found. Run{" "}
          <code className="text-ink">python -m backend.setup_demo</code> to
          provision the demo set.
        </p>
      )}

      <ul className="flex flex-col gap-2">
        <AnimatePresence initial={false}>
          {accounts?.map((a) => {
            const hot = highlight.has(a.id);
            return (
              <motion.li
                key={a.id}
                layout
                initial={{ opacity: 0, x: -8 }}
                animate={{
                  opacity: 1,
                  x: 0,
                  boxShadow: hot
                    ? "0 0 0 2px rgba(141,239,194,0.7)"
                    : "0 0 0 0 rgba(0,0,0,0)",
                }}
                exit={{ opacity: 0 }}
                transition={{ type: "spring", stiffness: 260, damping: 22 }}
                className="panel-tight flex items-center justify-between gap-3 p-3"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <span
                    className="h-3 w-3 shrink-0 rounded-full"
                    style={{ background: a.color || "#52525b" }}
                  />
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium">
                      {a.description}
                    </div>
                    {a.iban && (
                      <div className="truncate font-mono text-[10px] text-muted">
                        {a.iban}
                      </div>
                    )}
                  </div>
                </div>
                <Balance value={a.balance_eur} />
              </motion.li>
            );
          })}
        </AnimatePresence>
      </ul>
    </section>
  );
}

function Balance({ value }: { value: number }) {
  return (
    <motion.span
      key={value}
      initial={{ scale: 1.1, color: "#8defc2" }}
      animate={{ scale: 1, color: "#ffffff" }}
      transition={{ duration: 0.6 }}
      className="font-mono text-sm font-semibold tabular-nums"
    >
      €
      {value.toLocaleString("en-IE", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })}
    </motion.span>
  );
}

function Skeleton() {
  return (
    <ul className="flex flex-col gap-2">
      {Array.from({ length: 4 }).map((_, i) => (
        <li
          key={i}
          className="h-12 animate-pulse rounded-xl border border-line bg-panel2/40"
        />
      ))}
    </ul>
  );
}
