import { motion } from "framer-motion";
import { useState } from "react";

interface Props {
  text: string;
  interim: string;
  listening: boolean;
  onSubmit: (text: string) => void;
  onClear: () => void;
  busy: boolean;
}

export function Transcript({
  text,
  interim,
  listening,
  onSubmit,
  onClear,
  busy,
}: Props) {
  const [edit, setEdit] = useState(false);
  const [draft, setDraft] = useState("");

  const display = (text + (interim ? " " + interim : "")).trim();
  const canSubmit = !!display.trim() && !busy;

  return (
    <div className="panel mx-auto w-full max-w-2xl p-4">
      <div className="mb-2 flex items-center justify-between">
        <span className="chip">
          {listening ? (
            <>
              <span className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-bad" />
              recording
            </>
          ) : edit ? (
            "type instead"
          ) : (
            "transcript"
          )}
        </span>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => {
              setEdit((v) => !v);
              setDraft(display);
            }}
            className="text-xs text-muted hover:text-ink"
          >
            {edit ? "use voice" : "type instead"}
          </button>
          {(display || edit) && (
            <button
              type="button"
              onClick={() => {
                onClear();
                setDraft("");
                setEdit(false);
              }}
              className="text-xs text-muted hover:text-ink"
            >
              clear
            </button>
          )}
        </div>
      </div>

      {edit ? (
        <textarea
          autoFocus
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          rows={3}
          placeholder="Move 50 from Groceries to Travel, save 15% of any salary into Emergency, and freeze my entertainment card if I spend more than 50 at bars this week."
          className="w-full resize-none rounded-lg border border-line bg-panel2 p-3 text-sm text-ink outline-none focus:border-accent"
        />
      ) : (
        <motion.p
          layout
          className={[
            "min-h-[3.5rem] rounded-lg border border-dashed border-line/60 bg-panel2/40 p-3 text-base leading-relaxed",
            display ? "text-ink" : "text-muted",
            listening ? "shimmer-text" : "",
          ].join(" ")}
        >
          {display ||
            "Hold the mic and tell Vox what to do — e.g. “move 50 from groceries to travel, set aside 15% of every salary into emergency.”"}
        </motion.p>
      )}

      <div className="mt-3 flex items-center justify-end gap-2">
        <button
          type="button"
          disabled={!canSubmit && !(edit && draft.trim())}
          onClick={() => {
            const t = (edit ? draft : display).trim();
            if (t) onSubmit(t);
          }}
          className="btn-primary disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {busy ? (
            <>
              <Spinner /> Planning…
            </>
          ) : (
            <>Plan it</>
          )}
        </button>
      </div>
    </div>
  );
}

function Spinner() {
  return (
    <svg
      width={14}
      height={14}
      viewBox="0 0 24 24"
      fill="none"
      className="animate-spin"
    >
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeOpacity={0.25} strokeWidth={3} />
      <path
        d="M12 2a10 10 0 0 1 10 10"
        stroke="currentColor"
        strokeWidth={3}
        strokeLinecap="round"
      />
    </svg>
  );
}
