import { motion } from "framer-motion";
import { useEffect } from "react";

interface Props {
  listening: boolean;
  disabled?: boolean;
  supported: boolean;
  onStart: () => void;
  onStop: () => void;
}

export function MicButton({
  listening,
  disabled,
  supported,
  onStart,
  onStop,
}: Props) {
  // Hold-to-talk via mouse/touch + spacebar shortcut.
  useEffect(() => {
    if (disabled || !supported) return;
    const down = (e: KeyboardEvent) => {
      if (e.code === "Space" && !e.repeat && document.activeElement?.tagName !== "TEXTAREA") {
        e.preventDefault();
        onStart();
      }
    };
    const up = (e: KeyboardEvent) => {
      if (e.code === "Space") {
        e.preventDefault();
        onStop();
      }
    };
    window.addEventListener("keydown", down);
    window.addEventListener("keyup", up);
    return () => {
      window.removeEventListener("keydown", down);
      window.removeEventListener("keyup", up);
    };
  }, [disabled, supported, onStart, onStop]);

  const handlePointerDown = () => !disabled && supported && onStart();
  const handlePointerUp = () => !disabled && supported && onStop();

  return (
    <div className="relative flex flex-col items-center gap-3 select-none">
      <button
        type="button"
        disabled={disabled || !supported}
        onMouseDown={handlePointerDown}
        onMouseUp={handlePointerUp}
        onMouseLeave={listening ? handlePointerUp : undefined}
        onTouchStart={(e) => {
          e.preventDefault();
          handlePointerDown();
        }}
        onTouchEnd={(e) => {
          e.preventDefault();
          handlePointerUp();
        }}
        className={[
          "relative grid h-32 w-32 place-items-center rounded-full transition",
          "border border-line bg-panel shadow-[0_8px_40px_-8px_rgba(141,239,194,0.45)]",
          "disabled:opacity-40 disabled:cursor-not-allowed",
          listening
            ? "ring-4 ring-accent/60 bg-accent text-accentInk"
            : "hover:bg-panel2",
        ].join(" ")}
        aria-label={listening ? "Recording, release to stop" : "Hold to speak"}
      >
        {listening && (
          <>
            <span className="pointer-events-none absolute inset-0 rounded-full bg-accent/40 animate-pulseRing" />
            <span
              className="pointer-events-none absolute inset-0 rounded-full bg-accent/20 animate-pulseRing"
              style={{ animationDelay: "0.3s" }}
            />
          </>
        )}
        <MicIcon className={listening ? "text-accentInk" : "text-accent"} />
      </button>

      <motion.div
        animate={{ opacity: listening ? 1 : 0.6 }}
        className="text-xs text-muted"
      >
        {!supported
          ? "Web Speech API unavailable — try Chrome/Edge/Safari"
          : listening
            ? "Listening… release to plan"
            : "Hold the button (or spacebar) and speak"}
      </motion.div>
    </div>
  );
}

function MicIcon({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={42}
      height={42}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      <rect x="9" y="3" width="6" height="12" rx="3" />
      <path d="M5 11a7 7 0 0 0 14 0" />
      <line x1="12" y1="18" x2="12" y2="22" />
      <line x1="8" y1="22" x2="16" y2="22" />
    </svg>
  );
}
