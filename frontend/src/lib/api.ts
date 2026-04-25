import { useEffect, useRef, useState } from "react";
import type {
  CardInfo,
  ExecuteResponse,
  FiringEvent,
  Health,
  Plan,
  RuleView,
  SubAccount,
} from "./types";

const BASE = "/api"; // proxied by Vite to http://localhost:8080

async function j<T>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers || {}),
    },
  });
  if (!r.ok) {
    const text = await r.text().catch(() => "");
    throw new Error(`${r.status} ${r.statusText} — ${text.slice(0, 200)}`);
  }
  return r.json() as Promise<T>;
}

export const api = {
  health: () => j<Health>("/health"),
  accounts: () => j<SubAccount[]>("/accounts"),
  cards: () => j<CardInfo[]>("/cards"),
  rules: () => j<RuleView[]>("/rules"),
  firings: () => j<unknown[]>("/firings"),
  deleteRule: (id: number) =>
    j<{ ok: boolean }>(`/rules/${id}`, { method: "DELETE" }),
  plan: (text: string) =>
    j<Plan>("/plan", { method: "POST", body: JSON.stringify({ text }) }),
  execute: (plan: Plan, selected_indexes?: number[]) =>
    j<ExecuteResponse>("/execute", {
      method: "POST",
      body: JSON.stringify({ plan, selected_indexes }),
    }),
  fireSalary: (amount_eur = 2000) =>
    j("/demo/fire-salary", {
      method: "POST",
      body: JSON.stringify({ amount_eur }),
    }),
  fireBarSpend: (amount_eur = 35) =>
    j("/demo/fire-bar-spend", {
      method: "POST",
      body: JSON.stringify({ amount_eur }),
    }),
  fireLargeTx: (amount_eur = 500) =>
    j("/demo/fire-large-tx", {
      method: "POST",
      body: JSON.stringify({ amount_eur }),
    }),
};

// ---------------------------------------------------------------------------
// SSE — listen for live rule firings from the backend.
// ---------------------------------------------------------------------------

export function useFiringStream(onEvent: (ev: FiringEvent) => void): boolean {
  const [connected, setConnected] = useState(false);
  const cb = useRef(onEvent);
  cb.current = onEvent;

  useEffect(() => {
    const es = new EventSource(`${BASE}/events`);
    es.onopen = () => setConnected(true);
    es.onerror = () => setConnected(false);
    es.addEventListener("firing", (e) => {
      try {
        const data = JSON.parse((e as MessageEvent).data) as FiringEvent;
        cb.current(data);
      } catch {
        /* ignore */
      }
    });
    return () => es.close();
  }, []);

  return connected;
}

// ---------------------------------------------------------------------------
// Polling helper for accounts (cheap; backend caches anyway).
// ---------------------------------------------------------------------------

export function usePolling<T>(
  fn: () => Promise<T>,
  intervalMs: number,
  deps: unknown[] = [],
): { data: T | null; refresh: () => void; error: Error | null } {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const tick = useRef(0);

  const refresh = async () => {
    const myTick = ++tick.current;
    try {
      const v = await fn();
      if (myTick === tick.current) {
        setData(v);
        setError(null);
      }
    } catch (e) {
      if (myTick === tick.current) setError(e as Error);
    }
  };

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, intervalMs);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, refresh, error };
}
