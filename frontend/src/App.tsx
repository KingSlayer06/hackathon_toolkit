import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";

import { api, useFiringStream, usePolling } from "./lib/api";
import { useSpeechRecognition } from "./lib/speech";
import type {
  ExecutionResult,
  FiringEvent,
  Plan,
  Health,
} from "./lib/types";

import { MicButton } from "./components/MicButton";
import { Transcript } from "./components/Transcript";
import { DiffCards } from "./components/DiffCards";
import { AccountsPanel } from "./components/AccountsPanel";
import { RulesPanel } from "./components/RulesPanel";
import { Toaster, type Toast } from "./components/Toaster";
import { DemoControls } from "./components/DemoControls";
import { StatusBar } from "./components/StatusBar";

export default function App() {
  // ---- speech ----------------------------------------------------------
  const speech = useSpeechRecognition();

  // ---- backend data ----------------------------------------------------
  const [health, setHealth] = useState<Health | null>(null);
  useEffect(() => {
    api.health().then(setHealth).catch(() => setHealth(null));
  }, []);

  const accountsQ = usePolling(api.accounts, 4000);
  const rulesQ = usePolling(api.rules, 4000);

  // ---- planning + execution state -------------------------------------
  const [plan, setPlan] = useState<Plan | null>(null);
  const [planError, setPlanError] = useState<string | null>(null);
  const [planning, setPlanning] = useState(false);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [results, setResults] = useState<Map<number, ExecutionResult>>(new Map());
  const [executing, setExecuting] = useState(false);

  const submit = useCallback(
    async (text: string) => {
      setPlanning(true);
      setPlanError(null);
      setPlan(null);
      setResults(new Map());
      try {
        const p = await api.plan(text);
        setPlan(p);
        setSelected(new Set(p.actions.map((_, i) => i)));
      } catch (e) {
        setPlanError(String(e));
      } finally {
        setPlanning(false);
      }
    },
    [],
  );

  const execute = useCallback(async () => {
    if (!plan) return;
    setExecuting(true);
    try {
      const indexes = [...selected].sort();
      const r = await api.execute(plan, indexes);
      const m = new Map(results);
      for (const res of r.results) m.set(res.action_index, res);
      setResults(m);
      // refresh balances + rules right after
      accountsQ.refresh();
      rulesQ.refresh();
      pushToast({
        variant: r.results.every((x) => x.ok) ? "ok" : "bad",
        title: r.results.every((x) => x.ok)
          ? `Executed ${r.results.length} action${r.results.length === 1 ? "" : "s"}`
          : `Some actions failed (${r.results.filter((x) => !x.ok).length})`,
        detail: plan.summary,
      });
    } catch (e) {
      pushToast({ variant: "bad", title: "Execute failed", detail: String(e) });
    } finally {
      setExecuting(false);
    }
  }, [plan, selected, results, accountsQ, rulesQ]);

  const reset = useCallback(() => {
    setPlan(null);
    setResults(new Map());
    setSelected(new Set());
    setPlanError(null);
    speech.reset();
    lastFinalRef.current = "";
  }, [speech]);

  // ---- toaster ---------------------------------------------------------
  const [toasts, setToasts] = useState<Toast[]>([]);
  const toastId = useRef(0);
  const pushToast = useCallback((t: Omit<Toast, "id">) => {
    setToasts((prev) => [...prev, { ...t, id: ++toastId.current }]);
  }, []);
  const dismissToast = useCallback(
    (id: number) => setToasts((prev) => prev.filter((t) => t.id !== id)),
    [],
  );

  // Highlight accounts that have just changed (from execute or rule firing)
  const [hotAccounts, setHotAccounts] = useState<Set<number>>(new Set());
  const hotTimer = useRef<number | null>(null);
  const flashAccount = useCallback((id: number) => {
    setHotAccounts((s) => new Set(s).add(id));
    if (hotTimer.current) window.clearTimeout(hotTimer.current);
    hotTimer.current = window.setTimeout(() => setHotAccounts(new Set()), 2500);
  }, []);

  // ---- live SSE: rule firings -----------------------------------------
  const onFiring = useCallback(
    (ev: FiringEvent) => {
      pushToast({
        variant: "fire",
        title: `Rule fired · #${ev.rule_id}`,
        detail: ev.summary,
      });
      const detail = ev.detail as Record<string, unknown>;
      const acctName = detail.to_account as string | undefined;
      const acct = accountsQ.data?.find(
        (a) => acctName && a.description.toLowerCase() === acctName.toLowerCase(),
      );
      if (acct) flashAccount(acct.id);
      accountsQ.refresh();
      rulesQ.refresh();
    },
    [pushToast, accountsQ, rulesQ, flashAccount],
  );
  const sseConnected = useFiringStream(onFiring);

  // ---- mic button glue ------------------------------------------------
  const transcriptText = useMemo(() => speech.final, [speech.final]);

  // Auto-submit a moment after the user releases the mic, if they spoke
  // something substantial. (Disable by setting AUTO_PLAN=false.)
  const AUTO_PLAN = true;
  const lastFinalRef = useRef("");
  useEffect(() => {
    if (!AUTO_PLAN) return;
    if (speech.listening) return;
    if (speech.error) return;
    if (planning || executing) return;
    const t = speech.final.trim();
    if (!t || t === lastFinalRef.current) return;
    if (t.length < 8) return;
    lastFinalRef.current = t;
    submit(t);
  }, [speech.final, speech.listening, speech.error, planning, executing, submit]);

  return (
    <div className="min-h-screen w-full">
      <Header health={health} sseConnected={sseConnected} />

      <main className="mx-auto grid max-w-7xl grid-cols-1 gap-6 px-6 pb-16 lg:grid-cols-[20rem_1fr_22rem]">
        {/* Left rail */}
        <div className="flex flex-col gap-4">
          <AccountsPanel accounts={accountsQ.data} highlight={hotAccounts} />
          <DemoControls
            onFired={(label) =>
              pushToast({ variant: "info", title: "Demo trigger", detail: label })
            }
          />
        </div>

        {/* Center stage */}
        <div className="flex flex-col items-center gap-6 pt-4">
          <Hero />

          <MicButton
            supported={speech.supported}
            listening={speech.listening}
            onStart={speech.start}
            onStop={speech.stop}
            disabled={planning || executing}
          />

          <Transcript
            text={transcriptText}
            interim={speech.interim}
            listening={speech.listening}
            onSubmit={submit}
            onClear={reset}
            busy={planning}
          />

          {speech.error && (
            <div className="panel w-full max-w-2xl border-bad/60 p-3 text-sm text-bad">
              🎙️ {speech.error}
            </div>
          )}
          {planError && (
            <div className="panel w-full max-w-2xl border-bad/60 p-3 text-sm text-bad">
              {planError}
            </div>
          )}

          <AnimatePresence>
            {plan && (
              <motion.section
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                className="w-full max-w-2xl"
              >
                <div className="mb-3 flex items-baseline justify-between gap-3">
                  <p className="text-sm text-muted">
                    <span className="font-medium text-ink">Vox understood:</span>{" "}
                    {plan.summary}
                  </p>
                  <button
                    type="button"
                    onClick={reset}
                    className="text-xs text-muted hover:text-ink"
                  >
                    discard
                  </button>
                </div>

                {plan.warnings.length > 0 && (
                  <ul className="mb-3 space-y-1 rounded-lg border border-warn/40 bg-warn/5 p-3 text-xs text-warn">
                    {plan.warnings.map((w, i) => (
                      <li key={i}>⚠ {w}</li>
                    ))}
                  </ul>
                )}

                {plan.actions.length === 0 ? (
                  <p className="rounded-lg border border-line bg-panel2 p-4 text-sm text-muted">
                    Nothing actionable — try rephrasing.
                  </p>
                ) : (
                  <>
                    <DiffCards
                      actions={plan.actions}
                      selected={selected}
                      toggle={(i) =>
                        setSelected((s) => {
                          const n = new Set(s);
                          n.has(i) ? n.delete(i) : n.add(i);
                          return n;
                        })
                      }
                      results={results}
                      accounts={accountsQ.data ?? []}
                      busy={executing}
                    />

                    <div className="mt-4 flex items-center justify-between">
                      <span className="text-xs text-muted">
                        {selected.size}/{plan.actions.length} selected
                      </span>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={reset}
                          className="btn-ghost"
                          disabled={executing}
                        >
                          Cancel
                        </button>
                        <button
                          type="button"
                          onClick={execute}
                          disabled={executing || selected.size === 0}
                          className="btn-primary disabled:opacity-40"
                        >
                          {executing
                            ? "Executing…"
                            : `Execute ${selected.size} action${selected.size === 1 ? "" : "s"}`}
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </motion.section>
            )}
          </AnimatePresence>
        </div>

        {/* Right rail */}
        <div className="flex flex-col gap-4">
          <RulesPanel
            rules={rulesQ.data}
            onDelete={async (id) => {
              await api.deleteRule(id);
              rulesQ.refresh();
              pushToast({ variant: "info", title: `Disarmed rule #${id}` });
            }}
          />
          <Hint />
        </div>
      </main>

      <Toaster toasts={toasts} dismiss={dismissToast} />
    </div>
  );
}

function Header({
  health,
  sseConnected,
}: {
  health: Health | null;
  sseConnected: boolean;
}) {
  return (
    <header className="mx-auto flex max-w-7xl items-center justify-between px-6 py-5">
      <div className="flex items-center gap-3">
        <Logo />
        <div>
          <div className="text-lg font-bold tracking-tight">Vox</div>
          <div className="text-[11px] uppercase tracking-wider text-muted">
            talk to your bunq
          </div>
        </div>
      </div>
      <StatusBar health={health} sseConnected={sseConnected} />
    </header>
  );
}

function Logo() {
  return (
    <div className="grid h-10 w-10 place-items-center rounded-xl border border-accent/40 bg-accent/10">
      <svg viewBox="0 0 24 24" width={22} height={22} fill="none">
        <path
          d="M5 8c2 0 2-3 5-3s3 5 5 5 1-3 4-3"
          stroke="#22d3a8"
          strokeWidth={2}
          strokeLinecap="round"
        />
        <path
          d="M5 13c2 0 2-3 5-3s3 5 5 5 1-3 4-3"
          stroke="#22d3a8"
          strokeWidth={2}
          strokeLinecap="round"
          opacity={0.5}
        />
      </svg>
    </div>
  );
}

function Hero() {
  return (
    <div className="text-center">
      <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
        Tell your money what to do.
      </h1>
      <p className="mt-2 max-w-xl text-sm text-muted">
        Hold the mic, speak in plain English. Vox plans the bunq sub-account
        moves, recurring splits, and guardrails — you approve the diff before a
        single euro shifts.
      </p>
    </div>
  );
}

function Hint() {
  return (
    <section className="panel p-4 text-xs text-muted">
      <h3 className="mb-2 text-sm font-semibold uppercase tracking-wider text-ink">
        Try saying
      </h3>
      <ul className="space-y-2">
        <li className="rounded-lg border border-line bg-panel2 p-2 text-ink">
          “Move 50 from Groceries to Travel.”
        </li>
        <li className="rounded-lg border border-line bg-panel2 p-2 text-ink">
          “Set aside 15% of every salary deposit into Emergency.”
        </li>
        <li className="rounded-lg border border-line bg-panel2 p-2 text-ink">
          “Freeze my entertainment card if I spend more than 50 at bars this
          week.”
        </li>
      </ul>
      <p className="mt-3">
        Hold <kbd className="rounded border border-line bg-panel2 px-1">Space</kbd>{" "}
        anywhere to talk.
      </p>
    </section>
  );
}
