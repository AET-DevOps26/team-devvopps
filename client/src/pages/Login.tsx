import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { authStyles as st } from "./authStyles";

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={st.container}>
      <form onSubmit={handleSubmit} style={st.card}>
        <h1 style={st.title}>TUMgoal</h1>
        <p style={st.subtitle}>Sign in to your account</p>

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
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <button style={st.button} type="submit" disabled={submitting || !email || !password}>
          {submitting ? "Signing in…" : "Sign in"}
        </button>

        <p style={st.switch}>
          Don't have an account? <Link to="/signup" style={st.link}>Sign up</Link>
        </p>
      </form>
    </div>
  );
}
