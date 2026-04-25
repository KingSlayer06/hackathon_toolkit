import { useCallback, useEffect, useRef, useState } from "react";

// ---------------------------------------------------------------------------
// Web Speech API hook — hold-to-talk style.
//
// Key correctness rules:
//   - SpeechRecognitionEvent.results holds *all* results since session start,
//     not just new ones. We rebuild `final` and `interim` from scratch on
//     every event to avoid double-counting.
//   - Chrome's recognizer auto-stops after a short silence even with
//     continuous=true. While the user is still holding the button (i.e.
//     `intentListening`), we transparently restart it.
//   - Several "errors" the API emits ("no-speech", "aborted") are not user-
//     visible problems — we swallow them silently.
// ---------------------------------------------------------------------------

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  length: number;
  [index: number]: { transcript: string; confidence: number };
}

interface SpeechRecognitionEventLike {
  resultIndex: number;
  results: ArrayLike<SpeechRecognitionResultLike> & {
    length: number;
    [i: number]: SpeechRecognitionResultLike;
  };
}

interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onstart: (() => void) | null;
  onresult: ((e: SpeechRecognitionEventLike) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}

declare global {
  interface Window {
    SpeechRecognition?: { new (): SpeechRecognitionLike };
    webkitSpeechRecognition?: { new (): SpeechRecognitionLike };
  }
}

export interface SpeechHook {
  supported: boolean;
  listening: boolean;
  interim: string;
  final: string;
  error: string | null;
  start: () => void;
  stop: () => void;
  reset: () => void;
}

// Errors we should ignore — they're routine recognizer chatter.
const IGNORED_ERRORS = new Set(["no-speech", "aborted", "audio-capture"]);

export function useSpeechRecognition(lang = "en-US"): SpeechHook {
  const Ctor =
    typeof window !== "undefined"
      ? window.SpeechRecognition || window.webkitSpeechRecognition
      : undefined;
  const supported = !!Ctor;

  const [listening, setListening] = useState(false);
  const [interim, setInterim] = useState("");
  const [final, setFinal] = useState("");
  const [error, setError] = useState<string | null>(null);

  const recRef = useRef<SpeechRecognitionLike | null>(null);
  // True while the user is *intentionally* holding the mic. The recognizer
  // can stop on its own (silence timeout); we restart it as long as this
  // flag is still true.
  const intentListeningRef = useRef(false);
  // Hard guard against the auto-restart racing the user's stop().
  const restartingRef = useRef(false);

  useEffect(() => {
    if (!Ctor) return;
    const rec = new Ctor();
    rec.lang = lang;
    rec.continuous = true;
    rec.interimResults = true;
    rec.maxAlternatives = 1;

    rec.onstart = () => {
      restartingRef.current = false;
    };

    rec.onresult = (e) => {
      // Rebuild from scratch — `results` is cumulative.
      let interimText = "";
      let finalText = "";
      for (let i = 0; i < e.results.length; i++) {
        const res = e.results[i];
        const transcript = res[0]?.transcript ?? "";
        if (res.isFinal) finalText += transcript + " ";
        else interimText += transcript;
      }
      setFinal(finalText.trim());
      setInterim(interimText.trim());
    };

    rec.onerror = (e) => {
      if (IGNORED_ERRORS.has(e.error)) return;
      // Map common errors to friendlier messages.
      const map: Record<string, string> = {
        "not-allowed": "Microphone permission denied. Click the address-bar mic icon to enable it.",
        "service-not-allowed": "Speech recognition disabled by the browser/OS.",
        network: "Network error reaching the speech recognition service.",
        "language-not-supported": `Language ${lang} not supported.`,
      };
      setError(map[e.error] || `Speech error: ${e.error}`);
      intentListeningRef.current = false;
      setListening(false);
    };

    rec.onend = () => {
      // If the user is still holding the mic, the recognizer dropped on us
      // (silence timeout). Restart, don't surface to UI.
      if (intentListeningRef.current && !restartingRef.current) {
        restartingRef.current = true;
        try {
          rec.start();
        } catch {
          // start() can throw "InvalidStateError" if not fully torn down yet.
          // Try one more time on next tick.
          setTimeout(() => {
            try {
              rec.start();
            } catch {
              intentListeningRef.current = false;
              setListening(false);
            }
          }, 50);
        }
        return;
      }
      setListening(false);
      setInterim("");
    };

    recRef.current = rec;
    return () => {
      intentListeningRef.current = false;
      try {
        rec.abort();
      } catch {
        /* ignore */
      }
    };
  }, [Ctor, lang]);

  const start = useCallback(() => {
    if (!recRef.current) return;
    if (intentListeningRef.current) return; // already going
    setError(null);
    setFinal("");
    setInterim("");
    intentListeningRef.current = true;
    try {
      recRef.current.start();
      setListening(true);
    } catch (e) {
      // Most common: "InvalidStateError" from a previous session not fully
      // ended. abort + retry.
      try {
        recRef.current.abort();
        setTimeout(() => {
          try {
            recRef.current?.start();
            setListening(true);
          } catch (e2) {
            intentListeningRef.current = false;
            setError(String(e2));
          }
        }, 60);
      } catch {
        intentListeningRef.current = false;
        setError(String(e));
      }
    }
  }, []);

  const stop = useCallback(() => {
    intentListeningRef.current = false;
    if (!recRef.current) return;
    try {
      recRef.current.stop();
    } catch {
      /* ignore */
    }
    setListening(false);
  }, []);

  const reset = useCallback(() => {
    setFinal("");
    setInterim("");
    setError(null);
  }, []);

  return { supported, listening, interim, final, error, start, stop, reset };
}
