import { AnimatePresence, motion } from "framer-motion";
import { useEffect } from "react";

export interface Toast {
  id: number;
  variant: "info" | "fire" | "ok" | "bad";
  title: string;
  detail?: string;
  ttlMs?: number;
}

interface Props {
  toasts: Toast[];
  dismiss: (id: number) => void;
}

export function Toaster({ toasts, dismiss }: Props) {
  return (
    <div className="pointer-events-none fixed bottom-6 right-6 z-50 flex w-[26rem] max-w-[90vw] flex-col gap-2">
      <AnimatePresence initial={false}>
        {toasts.map((t) => (
          <ToastCard key={t.id} toast={t} dismiss={dismiss} />
        ))}
      </AnimatePresence>
    </div>
  );
}

function ToastCard({ toast, dismiss }: { toast: Toast; dismiss: (id: number) => void }) {
  useEffect(() => {
    const ttl = toast.ttlMs ?? 6000;
    const t = setTimeout(() => dismiss(toast.id), ttl);
    return () => clearTimeout(t);
  }, [toast, dismiss]);

  const accent =
    toast.variant === "fire"
      ? "border-accent/60 bg-accent/10 text-accent"
      : toast.variant === "ok"
        ? "border-accent/60 bg-panel"
        : toast.variant === "bad"
          ? "border-bad/60 bg-bad/10 text-bad"
          : "border-line bg-panel";

  return (
    <motion.div
      layout
      initial={{ opacity: 0, x: 30, scale: 0.96 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: 30, scale: 0.96 }}
      className={[
        "panel pointer-events-auto flex items-start gap-3 p-3 text-sm shadow-2xl",
        accent,
      ].join(" ")}
    >
      <div className="mt-0.5">{glyph(toast.variant)}</div>
      <div className="flex-1">
        <div className="font-semibold">{toast.title}</div>
        {toast.detail && (
          <div className="mt-0.5 text-xs opacity-80">{toast.detail}</div>
        )}
      </div>
      <button
        type="button"
        onClick={() => dismiss(toast.id)}
        className="text-xs text-muted hover:text-ink"
        aria-label="Dismiss"
      >
        ×
      </button>
    </motion.div>
  );
}

function glyph(v: Toast["variant"]) {
  if (v === "fire") return <span aria-hidden>⚡</span>;
  if (v === "ok") return <span aria-hidden>✓</span>;
  if (v === "bad") return <span aria-hidden>⚠</span>;
  return <span aria-hidden>·</span>;
}
