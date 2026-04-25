import { motion, AnimatePresence } from "framer-motion";
import type { CardInfo } from "../lib/types";

interface Props {
  cards: CardInfo[] | null;
}

export function CardsPanel({ cards }: Props) {
  return (
    <section className="panel p-4">
      <header className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-muted">
          Cards
        </h2>
        <span className="text-xs text-muted">{cards?.length ?? 0}</span>
      </header>

      {!cards && <Skeleton />}
      {cards && cards.length === 0 && (
        <p className="text-sm text-muted">
          No cards on this account. Order one in the bunq app to use the freeze
          rules.
        </p>
      )}

      <ul className="flex flex-col gap-2">
        <AnimatePresence initial={false}>
          {cards?.map((c) => (
            <motion.li
              key={c.id}
              layout
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0 }}
              transition={{ type: "spring", stiffness: 260, damping: 22 }}
            >
              <CardRow card={c} />
            </motion.li>
          ))}
        </AnimatePresence>
      </ul>

      {cards && cards.length > 0 && (
        <p className="mt-3 text-[10px] text-muted">
          Use the labels above verbatim when speaking freeze rules.
        </p>
      )}
    </section>
  );
}

function CardRow({ card }: { card: CardInfo }) {
  const tone = statusTone(card.status);
  const frozen = tone === "bad";

  return (
    <div className="flex items-center gap-3">
      <MiniCard label={card.label} type={card.type} frozen={frozen} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-medium text-ink">
          {card.label || "(untitled card)"}
        </div>
        <div className="mt-0.5 flex items-center gap-1.5">
          <StatusDot tone={tone} />
          <span className={`text-[11px] font-medium ${toneText(tone)}`}>
            {prettyStatus(card.status)}
          </span>
          {card.type && (
            <span className="text-[10px] uppercase tracking-wider text-muted">
              · {card.type.toLowerCase()}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function MiniCard({
  label,
  type,
  frozen,
}: {
  label: string;
  type: string | null;
  frozen: boolean;
}) {
  return (
    <div
      className={[
        "relative h-12 w-[72px] shrink-0 overflow-hidden rounded-lg p-1.5 shadow-inner",
        frozen
          ? "border border-bad/40 bg-gradient-to-br from-zinc-800 to-zinc-900 opacity-70"
          : "bg-gradient-to-br from-accent to-accent2 text-accentInk",
      ].join(" ")}
    >
      <div className="text-[8px] font-semibold uppercase tracking-wider opacity-80">
        bunq
      </div>
      <div className="mt-0.5 truncate text-[9px] font-bold leading-tight">
        {label || "card"}
      </div>
      <div className="absolute bottom-1 right-1.5 text-[7px] uppercase tracking-wider opacity-70">
        {(type || "").slice(0, 6)}
      </div>
      {frozen && (
        <div className="absolute inset-0 grid place-items-center">
          <FrostIcon />
        </div>
      )}
    </div>
  );
}

type Tone = "ok" | "warn" | "bad" | "muted";

function statusTone(status: string): Tone {
  const s = status.toUpperCase();
  if (s === "ACTIVE") return "ok";
  if (s === "DEACTIVATED" || s === "CANCELLED" || s === "LOST" || s === "STOLEN")
    return "bad";
  if (s === "EXPIRED" || s === "PIN_TRIES_EXCEEDED") return "muted";
  return "warn";
}

function toneText(t: Tone): string {
  switch (t) {
    case "ok":
      return "text-accent";
    case "bad":
      return "text-bad";
    case "warn":
      return "text-warn";
    case "muted":
      return "text-muted";
  }
}

function StatusDot({ tone }: { tone: Tone }) {
  const bg =
    tone === "ok"
      ? "bg-accent"
      : tone === "bad"
        ? "bg-bad"
        : tone === "warn"
          ? "bg-warn"
          : "bg-muted";
  return (
    <span className="relative inline-flex h-1.5 w-1.5">
      {tone === "ok" && (
        <span className="absolute inset-0 animate-ping rounded-full bg-accent/60" />
      )}
      <span className={`relative inline-flex h-1.5 w-1.5 rounded-full ${bg}`} />
    </span>
  );
}

function prettyStatus(status: string): string {
  return status.replace(/_/g, " ").toLowerCase();
}

function FrostIcon() {
  return (
    <svg viewBox="0 0 24 24" width={18} height={18} fill="none" stroke="#fb7185" strokeWidth={1.5}>
      <path d="M12 2v20M2 12h20M5 5l14 14M19 5L5 19" strokeLinecap="round" />
    </svg>
  );
}

function Skeleton() {
  return (
    <ul className="flex flex-col gap-2">
      {Array.from({ length: 2 }).map((_, i) => (
        <li
          key={i}
          className="h-12 animate-pulse rounded-xl border border-line bg-panel2/40"
        />
      ))}
    </ul>
  );
}
