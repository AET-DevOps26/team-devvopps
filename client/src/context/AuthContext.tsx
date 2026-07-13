import { createContext, useContext, useEffect, useState, useCallback } from "react";
import type { ReactNode } from "react";

const API_URL = "/api";

export type Role = "USER" | "ADMIN";

export interface AuthUser {
  userId: number;
  email: string;
  role: Role;
}

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

// Parses an error response body into a user-facing message.
async function errorMessage(res: Response, fallback: string): Promise<string> {
  try {
    const data = await res.json();
    return data.message || data.detail || data.error || fallback;
  } catch {
    return fallback;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const res = await fetch(`${API_URL}/auth/me`, { credentials: "include" });
        const data = res.ok ? await res.json() : null;
        if (active) setUser(data);
      } catch {
        if (active) setUser(null);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const authenticate = useCallback(
    async (path: string, email: string, password: string, fallback: string) => {
      const res = await fetch(`${API_URL}/auth/${path}`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        throw new Error(await errorMessage(res, fallback));
      }
      setUser(await res.json());
    },
    []
  );

  const login = useCallback(
    (email: string, password: string) =>
      authenticate("login", email, password, "Invalid email or password"),
    [authenticate]
  );

  const signup = useCallback(
    (email: string, password: string) =>
      authenticate("signup", email, password, "Could not create account"),
    [authenticate]
  );

  const logout = useCallback(async () => {
    try {
      await fetch(`${API_URL}/auth/logout`, { method: "POST", credentials: "include" });
    } finally {
      setUser(null);
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
