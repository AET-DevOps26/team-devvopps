import { useEffect, useState } from "react";

const API = "/api";

interface User {
  user_id: number;
  name: string;
  email: string;
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
  progress: number;
  milestones: { title: string }[];
}

type Tab = "users" | "courses" | "logs";

export default function AdminPanel() {
  const [tab, setTab] = useState<Tab>("users");
  const [users, setUsers] = useState<User[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [llmLogs, setLlmLogs] = useState<LlmLog[]>([]);
  const [roadmapLogs, setRoadmapLogs] = useState<RoadmapLog[]>([]);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      fetch(`${API}/users`).then((r) => r.json()),
      fetch(`${API}/courses`).then((r) => r.json()),
    ]).then(([u, c]) => {
      setUsers(u);
      setCourses(c);
      setLoading(false);
    });
  }, []);

  const fetchLogs = () => {
    fetch("/llm/logs").then((r) => r.json()).then((d) => setLlmLogs(d.logs || [])).catch(() => {});
    fetch(`${API}/roadmaps`).then((r) => r.json()).then((d) => {
      const sorted = [...(Array.isArray(d) ? d : [])].sort(
        (a, b) => new Date(b.created_date).getTime() - new Date(a.created_date).getTime()
      );
      setRoadmapLogs(sorted.slice(0, 50));
    }).catch(() => {});
  };

  useEffect(() => {
    if (tab === "logs") fetchLogs();
  }, [tab]);

  const addUser = async () => {
    if (!name || !email) return;
    const res = await fetch(`${API}/users`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, email }),
    });
    if (res.ok) {
      const u = await res.json();
      setUsers((prev) => [...prev, u]);
      setName("");
      setEmail("");
      setStatus(`User "${u.name}" created.`);
      setTimeout(() => setStatus(""), 3000);
    }
  };

  const deleteUser = async (id: number) => {
    await fetch(`${API}/users/${id}`, { method: "DELETE" });
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
          <button style={tab === "logs" ? s.tabActive : s.tab} onClick={() => setTab("logs")}>
            Logs
          </button>
        </nav>
      </header>

      <main style={s.main}>
        {tab === "users" && (
          <section>
            <h2 style={s.sectionTitle}>Users</h2>
            <div style={s.form}>
              <input style={s.input} placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} />
              <input style={s.input} placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
              <button style={s.btn} onClick={addUser}>Add User</button>
            </div>
            {status && <p style={s.success}>{status}</p>}
            <table style={s.table}>
              <thead>
                <tr>
                  <th style={s.th}>ID</th>
                  <th style={s.th}>Name</th>
                  <th style={s.th}>Email</th>
                  <th style={s.th}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.user_id} style={s.tr}>
                    <td style={s.td}>{u.user_id}</td>
                    <td style={s.td}>{u.name}</td>
                    <td style={s.td}>{u.email}</td>
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

        {tab === "logs" && (
          <section>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h2 style={s.sectionTitle}>Logs</h2>
              <button style={s.btn} onClick={fetchLogs}>Refresh</button>
            </div>

            <h3 style={{ fontSize: 16, marginBottom: 12, color: "#444" }}>LLM Service Logs</h3>
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
                    <td style={{ ...s.td, fontSize: 12, color: "#555", maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{l.goal || "—"}</td>
                    <td style={{ ...s.td, textAlign: "right" }}>{l.llm_ms != null ? l.llm_ms : "—"}</td>
                    <td style={{ ...s.td, textAlign: "right" }}>{l.total_ms != null ? l.total_ms : "—"}</td>
                  </tr>
                ))}
                {llmLogs.length === 0 && (
                  <tr><td colSpan={6} style={{ ...s.td, color: "#888", textAlign: "center" }}>No LLM logs yet.</td></tr>
                )}
              </tbody>
            </table>

            <h3 style={{ fontSize: 16, marginBottom: 12, color: "#444" }}>Roadmap History</h3>
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
  page: { minHeight: "100vh", background: "#f5f6fa", fontFamily: "sans-serif" },
  header: { background: "#0055A4", color: "#fff", padding: "0 32px", display: "flex", alignItems: "center", gap: 32, height: 56 },
  logo: { fontSize: 18, fontWeight: 700, margin: 0 },
  nav: { display: "flex", gap: 4 },
  tab: { padding: "6px 16px", background: "transparent", color: "rgba(255,255,255,0.7)", border: "none", borderRadius: 4, cursor: "pointer", fontSize: 14 },
  tabActive: { padding: "6px 16px", background: "rgba(255,255,255,0.2)", color: "#fff", border: "none", borderRadius: 4, cursor: "pointer", fontSize: 14, fontWeight: 600 },
  main: { maxWidth: 1000, margin: "0 auto", padding: "32px 16px" },
  sectionTitle: { fontSize: 22, marginBottom: 20 },
  form: { display: "flex", gap: 8, marginBottom: 12 },
  input: { padding: "8px 12px", border: "1px solid #ddd", borderRadius: 6, fontSize: 14, background: "#fff" },
  btn: { padding: "8px 18px", background: "#0055A4", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", fontSize: 14 },
  deleteBtn: { padding: "4px 10px", background: "#e53935", color: "#fff", border: "none", borderRadius: 4, cursor: "pointer", fontSize: 12 },
  success: { color: "#2e7d32", marginBottom: 12, fontSize: 14 },
  table: { width: "100%", borderCollapse: "collapse", background: "#fff", borderRadius: 8, overflow: "hidden", boxShadow: "0 1px 4px rgba(0,0,0,0.08)" },
  th: { textAlign: "left", padding: "12px 16px", background: "#f0f4ff", fontSize: 13, fontWeight: 600, color: "#444" },
  tr: { borderBottom: "1px solid #f0f0f0" },
  td: { padding: "10px 16px", fontSize: 14 },
  center: { display: "flex", justifyContent: "center", alignItems: "center", height: "100vh", fontFamily: "sans-serif" },
};
