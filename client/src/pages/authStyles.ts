import type { CSSProperties } from "react";

// TUM blue kept as the primary accent, with a lighter tint for glows on dark.
const BRAND = "#0065BD";
const BRAND_GLOW = "#4d9bff";

/** Shared inline styles for the Login and Signup pages (dark, TUM blue theme). */
export const authStyles: Record<string, CSSProperties> = {
  container: {
    minHeight: "100vh",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    background:
      "radial-gradient(1000px 500px at 50% -10%, #12224e 0%, rgba(18,34,78,0) 60%), linear-gradient(160deg, #0a0f2c 0%, #0c1436 55%, #090e28 100%)",
    fontFamily: "'Segoe UI', sans-serif",
    padding: 24,
    boxSizing: "border-box",
  },
  card: {
    width: "100%",
    maxWidth: 400,
    background: "rgba(255,255,255,0.045)",
    border: "1px solid rgba(255,255,255,0.09)",
    borderRadius: 18,
    padding: "40px 34px",
    boxShadow: "0 20px 60px rgba(0,0,0,0.45)",
    backdropFilter: "blur(14px)",
    display: "flex",
    flexDirection: "column",
    textAlign: "left",
  },
  title: {
    fontSize: 34,
    fontWeight: 800,
    margin: 0,
    textAlign: "center",
    letterSpacing: -0.5,
    background: `linear-gradient(90deg, #ffffff, ${BRAND_GLOW})`,
    WebkitBackgroundClip: "text",
    WebkitTextFillColor: "transparent",
  },
  subtitle: {
    color: "#9aa4bd",
    fontSize: 15,
    textAlign: "center",
    margin: "8px 0 28px",
  },
  label: {
    fontSize: 13,
    fontWeight: 600,
    color: "#b8c1d9",
    marginBottom: 7,
  },
  input: {
    padding: "13px 15px",
    fontSize: 15,
    color: "#e8ecf5",
    background: "rgba(255,255,255,0.05)",
    border: "1px solid rgba(255,255,255,0.12)",
    borderRadius: 11,
    outline: "none",
    marginBottom: 18,
  },
  button: {
    padding: "14px 24px",
    fontSize: 15,
    fontWeight: 700,
    background: `linear-gradient(90deg, ${BRAND}, ${BRAND_GLOW})`,
    color: "#fff",
    border: "none",
    borderRadius: 11,
    cursor: "pointer",
    marginTop: 6,
    boxShadow: `0 6px 20px ${BRAND}55`,
  },
  error: {
    background: "rgba(239,83,80,0.12)",
    border: "1px solid rgba(239,83,80,0.4)",
    color: "#ff9b98",
    padding: "11px 15px",
    borderRadius: 9,
    marginBottom: 20,
    fontSize: 14,
  },
  switch: {
    textAlign: "center",
    fontSize: 14,
    color: "#9aa4bd",
    marginTop: 22,
    marginBottom: 0,
  },
  link: {
    color: BRAND_GLOW,
    fontWeight: 600,
    textDecoration: "none",
  },
};
