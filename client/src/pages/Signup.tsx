import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { authStyles as st } from "./authStyles";

// Mirrors the server-side minimum (SignupRequest @Size(min = 8)).
const MIN_PASSWORD_LENGTH = 8;

export default function Signup() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }
    setSubmitting(true);
    try {
      await signup(email, password);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign up failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={st.container}>
      <form onSubmit={handleSubmit} style={st.card}>
        <h1 style={st.title}>TUMgoal</h1>
        <p style={st.subtitle}>Create your account</p>

        {error && <div style={st.error}>⚠️ {error}</div>}

        <label style={st.label}>Email</label>
        <input
          style={st.input}
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        <label style={st.label}>Password</label>
        <input
          style={st.input}
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <label style={st.label}>Confirm password</label>
        <input
          style={st.input}
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          required
        />

        <button style={st.button} type="submit" disabled={submitting || !email || !password}>
          {submitting ? "Creating account…" : "Sign up"}
        </button>

        <p style={st.switch}>
          Already have an account? <Link to="/login" style={st.link}>Sign in</Link>
        </p>
      </form>
    </div>
  );
}
