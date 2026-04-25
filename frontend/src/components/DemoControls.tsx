import { useState } from "react";
import { api } from "../lib/api";

interface Props {
  onFired: (msg: string) => void;
}

export function DemoControls({ onFired }: Props) {
  const [busy, setBusy] = useState<string | null>(null);

  const run = async (key: string, fn: () => Promise<unknown>, label: string) => {
    setBusy(key);
    try {
      await fn();
      onFired(label);
    } catch (e) {
      onFired(`Failed: ${e}`);
    } finally {
      setBusy(null);
    }
  };

  return (
    <section className="panel p-4">
      <header className="mb-3">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-muted">
          Demo triggers
        </h2>
        <p className="mt-0.5 text-xs text-muted">
          For the kicker beats. These hit bunq sandbox so your rules fire live.
        </p>
      </header>

      <div className="flex flex-col gap-2">
        <button
          type="button"
          disabled={busy !== null}
          onClick={() =>
            run(
              "salary",
              () => api.fireSalary(2000),
              "Pulled €2000 ‘SALARY APRIL’ into Main",
            )
          }
          className="btn-ghost justify-start"
        >
          {busy === "salary" ? "…" : "💸"} Fire incoming salary (€2000)
        </button>
        <button
          type="button"
          disabled={busy !== null}
          onClick={() =>
            run(
              "bar",
              () => api.fireBarSpend(35),
              "Spent €35 ‘BAR — Cafe Belgique’ from Entertainment",
            )
          }
          className="btn-ghost justify-start"
        >
          {busy === "bar" ? "…" : "🍷"} Fire bar payment (€35)
        </button>
        <button
          type="button"
          disabled={busy !== null}
          onClick={() =>
            run(
              "large",
              () => api.fireLargeTx(500),
              "Spent €500 ‘LARGE PURCHASE’ from Main",
            )
          }
          className="btn-ghost justify-start"
        >
          {busy === "large" ? "…" : "🛡️"} Fire large transaction (€500)
        </button>
      </div>
    </section>
  );
}
