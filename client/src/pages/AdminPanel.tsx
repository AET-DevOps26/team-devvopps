import { useEffect, useRef, useState } from "react";

const API = "/api";

// All admin calls are authenticated (JWT cookie) and require the ADMIN role,
// enforced at the gateway; credentials:"include" sends the cookie.
const withAuth: RequestInit = { credentials: "include" };

interface User {
  user_id: number;
  name?: string;
  email: string;
  role?: string;
}

interface AuthEvent {
  timestamp: string;
  type: string;
  email: string;
  result: string;
}

interface Course {
  course_id: number;
  title: string;
  tum_number: string;
  offered_in: string;
}

interface LlmLog {
  timestamp: string;
  level: string;
  message: string;
  goal?: string;
  llm_ms?: number;
  total_ms?: number;
}

interface RoadmapLog {
  id: number;
  title: string;
  created_date: string;
  progress?: number;
  milestones?: { title: string }[];
}

interface Feature {
  name: string;
  enabled: boolean;
  description: string;
}

type Tab = "users" | "courses" | "logs" | "features";

const TABS: Tab[] = ["users", "courses", "logs", "features"];

// The active tab lives in the URL hash (/admin#logs) so it survives page
// reloads and tab links are shareable. Plain useState would reset to
// "users" on every refresh.
function tabFromHash(): Tab {
  const h = window.location.hash.replace("#", "") as Tab;
  return TABS.includes(h) ? h : "users";
}

export default function AdminPanel() {
  const [tab, setTabState] = useState<Tab>(tabFromHash());

  const setTab = (t: Tab) => {
    window.location.hash = t;
    setTabState(t);
  };
  const [users, setUsers] = useState<User[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [llmLogs, setLlmLogs] = useState<LlmLog[]>([]);
  const [roadmapLogs, setRoadmapLogs] = useState<RoadmapLog[]>([]);
  const [authEvents, setAuthEvents] = useState<AuthEvent[]>([]);
  const [features, setFeatures] = useState<Feature[]>([]);
  const [settings, setSettings] = useState<Record<string, string>>({});
  const [settingsStatus, setSettingsStatus] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      fetch(`${API}/users`, withAuth).then((r) => r.json()).catch(() => []),
      fetch(`${API}/courses`, withAuth).then((r) => r.json()).catch(() => []),
      fetch(`${API}/features`, withAuth).then((r) => r.json()).catch(() => []),
    ]).then(([u, c, f]) => {
      setUsers(Array.isArray(u) ? u : []);
      setCourses(Array.isArray(c) ? c : []);
      setFeatures(Array.isArray(f) ? f : []);
      setLoading(false);
    });
  }, []);

  // Optimistic toggle: flip in the UI immediately, revert if the PUT fails.
  const toggleFeature = async (name: string, enabled: boolean) => {
    setFeatures((prev) => prev.map((f) => (f.name === name ? { ...f, enabled } : f)));
    const res = await fetch(`${API}/features/${name}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled }),
      ...withAuth,
    }).catch(() => null);
    if (!res || !res.ok) {
      setFeatures((prev) => prev.map((f) => (f.name === name ? { ...f, enabled: !enabled } : f)));
    }
  };

  const flagOn = (name: string) => features.find((f) => f.name === name)?.enabled !== false;

  // Lazy-load the runtime settings when the Features tab opens.
  useEffect(() => {
    if (tab !== "features" || Object.keys(settings).length > 0) return;
    fetch(`${API}/settings`, withAuth)
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((d: { name: string; value: string }[]) =>
        setSettings(Object.fromEntries(d.map((s) => [s.name, s.value]))))
      .catch((e) => console.error("Failed to fetch settings:", e));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  // Saves every setting; llm-service picks the new values up within ~30s.
  const saveSettings = async () => {
    if (!/^\d+$/.test((settings.monthlyTokenLimit || "").trim())) {
      setSettingsStatus("⚠️ Monthly token limit must be a positive number.");
      return;
    }
    setSettingsStatus("Saving...");
    const results = await Promise.all(
      Object.entries(settings).map(([name, value]) =>
        fetch(`${API}/settings/${name}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ value }),
          ...withAuth,
        }).catch(() => null)
      )
    );
    setSettingsStatus(results.every((r) => r && r.ok)
      ? "✓ Saved — takes effect on new requests within ~30s."
      : "⚠️ Some settings failed to save.");
  };

  const setSetting = (name: string, value: string) => {
    setSettings((prev) => ({ ...prev, [name]: value }));
    setSettingsStatus("");
  };

  // Guards against overlapping fetches (tab switch + Refresh): only the
  // latest invocation is allowed to write state, stale responses are dropped.
  const fetchSeq = useRef(0);

  const fetchLogs = () => {
    const seq = ++fetchSeq.current;
    fetch("/llm/logs", withAuth)
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((d) => {
        if (seq === fetchSeq.current) setLlmLogs(d.logs || []);
      })
      .catch((e) => console.error("Failed to fetch LLM logs:", e));
    fetch(`${API}/roadmaps/all`, withAuth)
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((d) => {
        if (seq !== fetchSeq.current) return;
        const sorted = [...(Array.isArray(d) ? d : [])].sort(
          (a, b) => new Date(b.created_date).getTime() - new Date(a.created_date).getTime()
        );
        setRoadmapLogs(sorted.slice(0, 50));
      })
      .catch((e) => console.error("Failed to fetch roadmap logs:", e));
    fetch(`${API}/auth/logs`, withAuth)
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((d) => {
        if (seq === fetchSeq.current) setAuthEvents(d.logs || []);
      })
      .catch((e) => console.error("Failed to fetch auth logs:", e));
  };

  useEffect(() => {
    if (tab === "logs") fetchLogs();
  }, [tab]);

  const deleteUser = async (id: number) => {
    await fetch(`${API}/users/${id}`, { method: "DELETE", ...withAuth });
    setUsers((prev) => prev.filter((u) => u.user_id !== id));
  };

  const filteredCourses = courses.filter((c) =>
    c.title?.toLowerCase().includes(search.toLowerCase()) ||
    c.tum_number?.toLowerCase().includes(search.toLowerCase())
  );

  if (loading) return <div style={s.center}>Loading...</div>;

  return (
    <div style={s.page}>
      <header style={s.header}>
        <h1 style={s.logo}>TUMgoal Admin</h1>
        <nav style={s.nav}>
          <button style={tab === "users" ? s.tabActive : s.tab} onClick={() => setTab("users")}>
            Users ({users.length})
          </button>
          <button style={tab === "courses" ? s.tabActive : s.tab} onClick={() => setTab("courses")}>
            Courses ({courses.length})
          </button>
          {flagOn("llmLogs") && (
            <button style={tab === "logs" ? s.tabActive : s.tab} onClick={() => setTab("logs")}>
              Logs
            </button>
          )}
          <button style={tab === "features" ? s.tabActive : s.tab} onClick={() => setTab("features")}>
            Features
          </button>
          {flagOn("grafanaLink") && (
            <a
              style={{ ...s.tab, textDecoration: "none" }}
              // On the server Grafana lives behind the ingress at /grafana;
              // locally (docker compose) it is published on localhost:3001.
              // Same-tab navigation: Safari silently drops target="_blank"
              // links that cross ports on localhost.
              href={window.location.hostname === "localhost" ? "http://localhost:3001" : "/grafana"}
              rel="noreferrer"
              title="Monitoring dashboards (Grafana login required)"
            >
              Grafana ↗
            </a>
          )}
        </nav>
      </header>

      <main style={s.main}>
        {tab === "users" && (
          <section>
            <h2 style={s.sectionTitle}>Users</h2>
            <p style={{ color: "#888", fontSize: 13, marginTop: -8, marginBottom: 16 }}>
              Users register themselves via signup. Admins can review and remove accounts here.
            </p>
            <table style={s.table}>
              <thead>
                <tr>
                  <th style={s.th}>ID</th>
                  <th style={s.th}>Email</th>
                  <th style={s.th}>Role</th>
                  <th style={s.th}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.user_id} style={s.tr}>
                    <td style={s.td}>{u.user_id}</td>
                    <td style={s.td}>{u.email}</td>
                    <td style={s.td}>{u.role || "USER"}</td>
                    <td style={s.td}>
                      <button style={s.deleteBtn} onClick={() => deleteUser(u.user_id)}>Delete</button>
                    </td>
                  </tr>
                ))}
                {users.length === 0 && (
                  <tr><td colSpan={4} style={{ ...s.td, color: "#888", textAlign: "center" }}>No users found.</td></tr>
                )}
              </tbody>
            </table>
          </section>
        )}

        {tab === "courses" && (
          <section>
            <h2 style={s.sectionTitle}>Courses</h2>
            <input
              style={{ ...s.input, width: "100%", marginBottom: 16, boxSizing: "border-box" }}
              placeholder="Search by title or TUM number..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <table style={s.table}>
              <thead>
                <tr>
                  <th style={s.th}>TUM Number</th>
                  <th style={s.th}>Title</th>
                  <th style={s.th}>Offered In</th>
                </tr>
              </thead>
              <tbody>
                {filteredCourses.slice(0, 50).map((c) => (
                  <tr key={c.course_id} style={s.tr}>
                    <td style={{ ...s.td, whiteSpace: "nowrap", color: "#888" }}>{c.tum_number || "—"}</td>
                    <td style={s.td}>{c.title}</td>
                    <td style={{ ...s.td, whiteSpace: "nowrap" }}>{c.offered_in || "—"}</td>
                  </tr>
                ))}
                {filteredCourses.length > 50 && (
                  <tr><td colSpan={3} style={{ ...s.td, color: "#888", textAlign: "center" }}>
                    Showing 50 of {filteredCourses.length} results. Use search to filter.
                  </td></tr>
                )}
                {filteredCourses.length === 0 && (
                  <tr><td colSpan={3} style={{ ...s.td, color: "#888", textAlign: "center" }}>No courses found.</td></tr>
                )}
              </tbody>
            </table>
          </section>
        )}

        {tab === "features" && (
          <section>
            <h2 style={s.sectionTitle}>Feature toggles</h2>
            <p style={{ color: "#888", fontSize: 13, marginTop: -8, marginBottom: 16 }}>
              Turn features on or off at runtime — no redeploy needed. Changes take
              effect immediately in the UI and within ~30s in backend services.
            </p>
            <table style={s.table}>
              <thead>
                <tr>
                  <th style={s.th}>Feature</th>
                  <th style={s.th}>Description</th>
                  <th style={s.th}>Status</th>
                </tr>
              </thead>
              <tbody>
                {features.map((f) => (
                  <tr key={f.name} style={s.tr}>
                    <td style={{ ...s.td, fontWeight: 600, whiteSpace: "nowrap" }}>{f.name}</td>
                    <td style={s.td}>{f.description}</td>
                    <td style={s.td}>
                      <button
                        style={f.enabled ? s.toggleOn : s.toggleOff}
                        onClick={() => toggleFeature(f.name, !f.enabled)}
                      >
                        {f.enabled ? "ON" : "OFF"}
                      </button>
                    </td>
                  </tr>
                ))}
                {features.length === 0 && (
                  <tr><td colSpan={3} style={{ ...s.td, color: "#888", textAlign: "center" }}>No feature flags found.</td></tr>
                )}
              </tbody>
            </table>

            <h2 style={{ ...s.sectionTitle, marginTop: 32 }}>Monthly token limit</h2>
            <p style={s.settingHint}>
              Per-user LLM token budget per calendar month (applies while the{" "}
              <code>tokenQuota</code> flag is ON). Must stay well above the
              per-request cap (4000) or every request would be rejected.
            </p>
            <input
              style={{ ...s.input, width: 160, fontFamily: "monospace" }}
              value={settings.monthlyTokenLimit || ""}
              onChange={(e) => setSetting("monthlyTokenLimit", e.target.value)}
            />

            <h2 style={{ ...s.sectionTitle, marginTop: 32 }}>LLM prompt</h2>
            <p style={s.settingHint}>
              The prompt is assembled from the sections below. The data block in the
              middle is structural and cannot be edited.
            </p>

            <h3 style={s.settingLabel}>1 · Role — who the LLM is</h3>
            <textarea
              style={s.promptArea} rows={2} spellCheck={false}
              value={settings.promptRole || ""}
              onChange={(e) => setSetting("promptRole", e.target.value)}
            />

            <h3 style={s.settingLabel}>2 · Data block (fixed)</h3>
            <pre style={s.fixedBlock}>{"Student's learning goal: {goal}\n\nAvailable courses in the catalogue:\n{courses}"}</pre>

            <h3 style={s.settingLabel}>3 · Instructions</h3>
            <textarea
              style={s.promptArea} rows={8} spellCheck={false}
              value={settings.promptInstructions || ""}
              onChange={(e) => setSetting("promptInstructions", e.target.value)}
            />

            <h3 style={s.settingLabel}>4 · Response format (JSON schema — edit with care)</h3>
            <textarea
              style={s.promptArea} rows={12} spellCheck={false}
              value={settings.promptResponseFormat || ""}
              onChange={(e) => setSetting("promptResponseFormat", e.target.value)}
            />

            <div style={{ display: "flex", alignItems: "center", gap: 16, marginTop: 12 }}>
              <button style={s.saveBtn} onClick={saveSettings}>Save settings</button>
              {settingsStatus && (
                <span style={{ fontSize: 13, color: settingsStatus.startsWith("✓") ? "#7ddb81" : "#e0a020" }}>
                  {settingsStatus}
                </span>
              )}
            </div>
          </section>
        )}

        {tab === "logs" && (
          <section>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h2 style={s.sectionTitle}>Logs</h2>
              <button style={s.btn} onClick={fetchLogs}>Refresh</button>
            </div>

            <h3 style={{ fontSize: 16, marginBottom: 12, color: "#b8c1d9" }}>Auth Events</h3>
            <table style={{ ...s.table, marginBottom: 32 }}>
              <thead>
                <tr>
                  <th style={s.th}>Time</th>
                  <th style={s.th}>Event</th>
                  <th style={s.th}>Email</th>
                  <th style={s.th}>Result</th>
                </tr>
              </thead>
              <tbody>
                {authEvents.map((e, i) => (
                  <tr key={i} style={s.tr}>
                    <td style={{ ...s.td, whiteSpace: "nowrap", color: "#888", fontSize: 12 }}>{new Date(e.timestamp).toLocaleTimeString()}</td>
                    <td style={{ ...s.td, fontSize: 13 }}>{e.type}</td>
                    <td style={{ ...s.td, fontSize: 13 }}>{e.email}</td>
                    <td style={{ ...s.td, fontWeight: 600, fontSize: 12, color: e.result === "success" ? "#2e7d32" : e.result === "failure" ? "#e53935" : "#f57c00" }}>{e.result}</td>
                  </tr>
                ))}
                {authEvents.length === 0 && (
                  <tr><td colSpan={4} style={{ ...s.td, color: "#888", textAlign: "center" }}>No auth events yet.</td></tr>
                )}
              </tbody>
            </table>

            <h3 style={{ fontSize: 16, marginBottom: 12, color: "#b8c1d9" }}>LLM Service Logs</h3>
            <table style={{ ...s.table, marginBottom: 32 }}>
              <thead>
                <tr>
                  <th style={s.th}>Time</th>
                  <th style={s.th}>Level</th>
                  <th style={s.th}>Message</th>
                  <th style={s.th}>Goal</th>
                  <th style={s.th}>LLM ms</th>
                  <th style={s.th}>Total ms</th>
                </tr>
              </thead>
              <tbody>
                {llmLogs.map((l, i) => (
                  <tr key={i} style={s.tr}>
                    <td style={{ ...s.td, whiteSpace: "nowrap", color: "#888", fontSize: 12 }}>{new Date(l.timestamp).toLocaleTimeString()}</td>
                    <td style={{ ...s.td, color: l.level === "ERROR" ? "#e53935" : l.level === "WARN" ? "#f57c00" : "#2e7d32", fontWeight: 600, fontSize: 12 }}>{l.level}</td>
                    <td style={{ ...s.td, fontSize: 13 }}>{l.message}</td>
                    <td style={{ ...s.td, fontSize: 12, color: "#9aa4bd", maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{l.goal || "—"}</td>
                    <td style={{ ...s.td, textAlign: "right" }}>{l.llm_ms != null ? l.llm_ms : "—"}</td>
                    <td style={{ ...s.td, textAlign: "right" }}>{l.total_ms != null ? l.total_ms : "—"}</td>
                  </tr>
                ))}
                {llmLogs.length === 0 && (
                  <tr><td colSpan={6} style={{ ...s.td, color: "#888", textAlign: "center" }}>No LLM logs yet.</td></tr>
                )}
              </tbody>
            </table>

            <h3 style={{ fontSize: 16, marginBottom: 12, color: "#b8c1d9" }}>Roadmap History</h3>
            <table style={s.table}>
              <thead>
                <tr>
                  <th style={s.th}>ID</th>
                  <th style={s.th}>Goal</th>
                  <th style={s.th}>Created</th>
                  <th style={s.th}>Milestones</th>
                  <th style={s.th}>Progress</th>
                </tr>
              </thead>
              <tbody>
                {roadmapLogs.map((r) => (
                  <tr key={r.id} style={s.tr}>
                    <td style={{ ...s.td, color: "#888" }}>{r.id}</td>
                    <td style={s.td}>{r.title}</td>
                    <td style={{ ...s.td, whiteSpace: "nowrap", fontSize: 12, color: "#888" }}>{r.created_date ? new Date(r.created_date).toLocaleString() : "—"}</td>
                    <td style={{ ...s.td, textAlign: "center" }}>{r.milestones?.length ?? 0}</td>
                    <td style={{ ...s.td, textAlign: "center" }}>{r.progress ?? 0}%</td>
                  </tr>
                ))}
                {roadmapLogs.length === 0 && (
                  <tr><td colSpan={5} style={{ ...s.td, color: "#888", textAlign: "center" }}>No roadmaps yet.</td></tr>
                )}
              </tbody>
            </table>
          </section>
        )}
      </main>
    </div>
  );
}

const s: Record<string, React.CSSProperties> = {
  page: { minHeight: "100vh", background: "linear-gradient(160deg, #0a0f2c 0%, #0c1436 55%, #090e28 100%)", fontFamily: "'Segoe UI', sans-serif", color: "#e8ecf5" },
  header: { background: "#0065BD", color: "#fff", padding: "0 32px", display: "flex", alignItems: "center", gap: 32, height: 56 },
  logo: { fontSize: 18, fontWeight: 700, margin: 0 },
  nav: { display: "flex", gap: 4 },
  tab: { padding: "6px 16px", background: "transparent", color: "rgba(255,255,255,0.75)", border: "none", borderRadius: 6, cursor: "pointer", fontSize: 14 },
  tabActive: { padding: "6px 16px", background: "rgba(255,255,255,0.22)", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", fontSize: 14, fontWeight: 600 },
  main: { maxWidth: 1000, margin: "0 auto", padding: "32px 16px" },
  sectionTitle: { fontSize: 22, marginBottom: 20, color: "#f2f5fc" },
  form: { display: "flex", gap: 8, marginBottom: 12 },
  input: { padding: "10px 14px", border: "1px solid rgba(255,255,255,0.12)", borderRadius: 8, fontSize: 14, background: "rgba(255,255,255,0.05)", color: "#e8ecf5", outline: "none" },
  btn: { padding: "9px 18px", background: "linear-gradient(90deg, #0065BD, #4d9bff)", color: "#fff", border: "none", borderRadius: 8, cursor: "pointer", fontSize: 14, fontWeight: 600, boxShadow: "0 4px 14px rgba(0,101,189,0.4)" },
  deleteBtn: { padding: "5px 12px", background: "rgba(239,83,80,0.15)", color: "#ff9b98", border: "1px solid rgba(239,83,80,0.4)", borderRadius: 6, cursor: "pointer", fontSize: 12, fontWeight: 600 },
  toggleOn: { padding: "5px 16px", background: "rgba(76,175,80,0.18)", color: "#7ddb81", border: "1px solid rgba(76,175,80,0.5)", borderRadius: 6, cursor: "pointer", fontSize: 12, fontWeight: 700, minWidth: 52 },
  toggleOff: { padding: "5px 16px", background: "rgba(120,120,120,0.15)", color: "#9aa", border: "1px solid rgba(120,120,120,0.4)", borderRadius: 6, cursor: "pointer", fontSize: 12, fontWeight: 700, minWidth: 52 },
  settingHint: { color: "#888", fontSize: 13, marginTop: -8, marginBottom: 12 },
  settingLabel: { fontSize: 14, fontWeight: 600, margin: "16px 0 6px", color: "#c6cde0" },
  promptArea: { width: "100%", boxSizing: "border-box", fontFamily: "monospace", fontSize: 13, lineHeight: 1.5, padding: 12, borderRadius: 8, border: "1px solid rgba(255,255,255,0.15)", background: "rgba(0,0,0,0.25)", color: "#dde3f0", resize: "vertical" },
  fixedBlock: { fontFamily: "monospace", fontSize: 13, lineHeight: 1.5, padding: 12, borderRadius: 8, border: "1px dashed rgba(255,255,255,0.2)", background: "rgba(255,255,255,0.04)", color: "#8a93a8", margin: 0, whiteSpace: "pre-wrap" },
  saveBtn: { padding: "8px 20px", background: "#0065BD", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", fontSize: 13, fontWeight: 700 },
  success: { color: "#22c55e", marginBottom: 12, fontSize: 14 },
  table: { width: "100%", borderCollapse: "collapse", background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.08)", borderRadius: 10, overflow: "hidden" },
  th: { textAlign: "left", padding: "12px 16px", background: "rgba(255,255,255,0.06)", fontSize: 13, fontWeight: 600, color: "#b8c1d9" },
  tr: { borderBottom: "1px solid rgba(255,255,255,0.07)" },
  td: { padding: "11px 16px", fontSize: 14, color: "#d6dcec" },
  center: { display: "flex", justifyContent: "center", alignItems: "center", height: "100vh", fontFamily: "'Segoe UI', sans-serif", background: "linear-gradient(160deg, #0a0f2c 0%, #0c1436 55%, #090e28 100%)", color: "#9aa4bd" },
};
