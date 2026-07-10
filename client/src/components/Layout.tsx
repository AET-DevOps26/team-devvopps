import type { ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * App chrome for authenticated pages: a slim top bar showing the signed-in
 * user, an Admin link (ADMIN only), and a Logout button.
 */
export default function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <div>
      <header style={styles.bar}>
        <Link to="/" style={styles.brand}>TUMgoal</Link>
        <div style={styles.right}>
          {user?.role === "ADMIN" && (
            <Link to="/admin" style={styles.link}>Admin</Link>
          )}
          {user && <span style={styles.email}>{user.email}</span>}
          <button style={styles.logout} onClick={handleLogout}>Logout</button>
        </div>
      </header>
      {children}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  bar: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "0 24px",
    height: 52,
    background: "#0065BD",
    color: "#fff",
    fontFamily: "'Segoe UI', sans-serif",
  },
  brand: { fontSize: 18, fontWeight: 700, color: "#fff", textDecoration: "none" },
  right: { display: "flex", alignItems: "center", gap: 16 },
  link: { color: "#fff", textDecoration: "none", fontSize: 14, fontWeight: 600 },
  email: { fontSize: 13, color: "rgba(255,255,255,0.85)" },
  logout: {
    padding: "6px 14px",
    background: "rgba(255,255,255,0.15)",
    color: "#fff",
    border: "1px solid rgba(255,255,255,0.4)",
    borderRadius: 6,
    cursor: "pointer",
    fontSize: 13,
  },
};
