import type { Health } from "../lib/types";

interface Props {
  health: Health | null;
  sseConnected: boolean;
}

export function StatusBar({ health, sseConnected }: Props) {
  return (
    <div className="flex items-center gap-2 text-xs">
      <Pill ok={health?.bunq_authenticated ?? false}>
        bunq {health?.bunq_authenticated ? "ok" : "down"}
      </Pill>
      <Pill ok={!!health?.llm_provider}>
        llm {health?.llm_provider ?? "—"}
      </Pill>
      <Pill ok={sseConnected}>events {sseConnected ? "live" : "offline"}</Pill>
    </div>
  );
}

function Pill({
  children,
  ok,
}: {
  children: React.ReactNode;
  ok: boolean;
}) {
  return (
    <span
      className={[
        "chip",
        ok ? "border-accent/60 text-accent" : "border-bad/60 text-bad",
      ].join(" ")}
    >
      <span
        className={`h-1.5 w-1.5 rounded-full ${ok ? "bg-accent" : "bg-bad"}`}
      />
      {children}
    </span>
  );
}
