/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // bunq-aligned palette
        bg: "#000000",        // true black canvas
        panel: "#0e0e10",     // raised surface
        panel2: "#17171a",    // sunken / row
        line: "#26262a",      // hairlines
        ink: "#ffffff",       // primary text
        muted: "#9ca3af",     // secondary text
        // "Easy Green" — bunq's signature mint
        accent: "#8defc2",
        accent2: "#7ce6bb",   // hover / pressed
        accentInk: "#003a23", // text on green
        warn: "#fbbf24",
        bad: "#fb7185",
      },
      fontFamily: {
        sans: [
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "sans-serif",
        ],
        // For the bunq-style italic accent in headlines
        display: [
          "DM Serif Display",
          "Iowan Old Style",
          "Apple Garamond",
          "Georgia",
          "serif",
        ],
        mono: ["ui-monospace", "SFMono-Regular", "Menlo", "monospace"],
      },
      borderRadius: {
        "4xl": "2rem",
      },
      keyframes: {
        pulseRing: {
          "0%": { transform: "scale(1)", opacity: "0.6" },
          "100%": { transform: "scale(1.6)", opacity: "0" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
      },
      animation: {
        pulseRing: "pulseRing 1.4s ease-out infinite",
        shimmer: "shimmer 2s linear infinite",
      },
    },
  },
  plugins: [],
};
