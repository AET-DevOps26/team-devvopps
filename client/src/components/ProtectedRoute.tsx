import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * Gates a route behind authentication.
 * - while the session is being resolved → a lightweight loading state
 * - not logged in → redirect to /login
 * - logged in but lacking ADMIN when required → redirect to home
 */
export default function ProtectedRoute({
  children,
  requireAdmin = false,
}: {
  children: ReactNode;
  requireAdmin?: boolean;
}) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh", color: "#9aa4bd", fontFamily: "'Segoe UI', sans-serif", background: "linear-gradient(160deg, #0a0f2c 0%, #0c1436 55%, #090e28 100%)" }}>
        Loading…
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (requireAdmin && user.role !== "ADMIN") {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
