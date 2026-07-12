import { useState, useEffect } from "react";

const API_URL = "/api";
// nginx proxies /llm/ to the api-gateway, which authenticates and forwards to
// llm-service (see client/nginx.conf). The user identity comes from the JWT
// cookie, so no user id is sent from the client.
const LLM_URL = "/llm";

// Mirrors MAX_GOAL_CHARS in llm-service — requests longer than this are
// rejected server-side with 422, so block them in the input directly.
const MAX_GOAL_LENGTH = 200;

// Brand palette — TUM blue kept as the primary accent, with a lighter tint used
// for glows/borders so it reads well on the dark theme.
const BRAND = "#0065BD";
const BRAND_GLOW = "#4d9bff";
const GREEN = "#22c55e";
const GRAY = "#8a93a8";

type Status = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

const STATUS: Record<Status, { color: string; label: string }> = {
  COMPLETED: { color: GREEN, label: "Completed" },
  IN_PROGRESS: { color: BRAND_GLOW, label: "In Progress" },
  NOT_STARTED: { color: GRAY, label: "Not Started" },
};

// Decorative icon per milestone, cycled by position.
const MILESTONE_ICONS = ["🎓", "💻", "📊", "🧠", "🗄️", "🗂️", "☁️", "💼", "🚀"];

interface Task {
  task_id: number;
  title: string;
  completed: boolean;
  order_index: number;
}

interface Milestone {
  milestone_id: number;
  title: string;
  description: string;
  status: Status;
  tasks: Task[];
}

interface RoadmapResponse {
  roadmap_id: number;
  title?: string;
  created_date?: string;
  milestones: Milestone[];
}

interface TokenUsage {
  used: number;
  limit: number;
  remaining: number;
}

// Fetches token usage without touching React state, so it can be called from
// an effect without tripping the react-hooks/set-state-in-effect rule.
// Returns null on any failure — the usage badge is non-critical.
async function fetchUsageData(): Promise<TokenUsage | null> {
  try {
    const res = await fetch(`${LLM_URL}/usage`, { credentials: "include" });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

// Loads the signed-in user's saved roadmaps (newest first). Returns [] on failure.
async function fetchMyRoadmapsData(): Promise<RoadmapResponse[]> {
  try {
    const res = await fetch(`${API_URL}/roadmaps`, { credentials: "include" });
    if (!res.ok) return [];
    const data = await res.json();
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

// Task-level progress summary used by the saved-roadmap cards.
function roadmapProgress(r: RoadmapResponse) {
  const tasks = r.milestones?.flatMap((m) => m.tasks ?? []) ?? [];
  const done = tasks.filter((t) => t.completed).length;
  const pct = tasks.length ? Math.round((done / tasks.length) * 100) : 0;
  return { done, total: tasks.length, pct };
}

// Decodes legacy URL-encoded titles (e.g. "learn%20docker") for display.
function niceTitle(t?: string): string {
  if (!t) return "Untitled roadmap";
  try {
    return decodeURIComponent(t);
  } catch {
    return t;
  }
}

// Small status icon: filled check when done, hollow ring otherwise.
function TaskCheck({ done }: { done: boolean }) {
  return done ? (
    <svg width="18" height="18" viewBox="0 0 24 24" style={{ flexShrink: 0, marginTop: 1 }}>
      <circle cx="12" cy="12" r="11" fill={GREEN} />
      <path d="M7 12.3l3.2 3.2L17 8.9" fill="none" stroke="#0a0f2c" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ) : (
    <svg width="18" height="18" viewBox="0 0 24 24" style={{ flexShrink: 0, marginTop: 1 }}>
      <circle cx="12" cy="12" r="10.5" fill="none" stroke="#5a647d" strokeWidth="1.6" />
    </svg>
  );
}

export default function RoadmapChat() {
  const [goal, setGoal] = useState("");
  const [roadmap, setRoadmap] = useState<RoadmapResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [togglingTaskId, setTogglingTaskId] = useState<number | null>(null);
  const [usage, setUsage] = useState<TokenUsage | null>(null);
  const [myRoadmaps, setMyRoadmaps] = useState<RoadmapResponse[]>([]);

  // Token usage is best-effort: fetch on mount and refresh after each
  // generation so the remaining balance stays in sync.
  async function fetchUsage() {
    const data = await fetchUsageData();
    if (data) setUsage(data);
  }

  // Refreshes the saved-roadmap list (after generating or returning to it).
  async function refreshMyRoadmaps() {
    setMyRoadmaps(await fetchMyRoadmapsData());
  }

  // On mount, load token usage and the user's saved roadmaps in parallel.
  useEffect(() => {
    let active = true;
    (async () => {
      const [u, rms] = await Promise.all([fetchUsageData(), fetchMyRoadmapsData()]);
      if (!active) return;
      if (u) setUsage(u);
      setMyRoadmaps(rms);
    })();
    return () => { active = false; };
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!goal.trim()) return;

    setLoading(true);
    setError(null);
    setRoadmap(null);

    try {
      const res = await fetch(`${API_URL}/roadmaps/generate?goal=${encodeURIComponent(goal)}`, {
        method: "POST",
        credentials: "include",
      });

      if (res.status === 429) {
        const data = await res.json();
        setError(data.detail || "Token quota exceeded.");
        fetchUsage();
        return;
      }

      if (res.status === 422) {
        let detail: string | undefined;
        try {
          const data = await res.json();
          detail = data.detail;
        } catch {
          // body wasn't JSON — fall through to default message
        }
        setError(detail || "Your goal is too long. Please shorten it.");
        return;
      }

      if (!res.ok) throw new Error(`Error ${res.status}: ${res.statusText}`);
      const data: RoadmapResponse = await res.json();
      setRoadmap(data);
      fetchUsage();
      refreshMyRoadmaps();

    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }

  async function handleToggleTask(taskId: number) {
    if (!roadmap) return;
    if (togglingTaskId !== null) return;
    setTogglingTaskId(taskId);

    // Optimistic update
    const prevRoadmap = roadmap;
    setRoadmap(optimisticallyToggle(roadmap, taskId));

    try {
      const res = await fetch(
        `${API_URL}/roadmaps/${roadmap.roadmap_id}/tasks/${taskId}/complete`,
        { method: "PATCH", credentials: "include" }
      );
      if (!res.ok) throw new Error(`Error ${res.status}`);
      const updated: RoadmapResponse = await res.json();

      setRoadmap(prev => {
        if (!prev) return updated;

        return {
          ...updated,
          milestones: updated.milestones.map(updatedMilestone => {
            const oldMilestone = prev.milestones.find(
              m => m.milestone_id === updatedMilestone.milestone_id
            );

            if (!oldMilestone) return updatedMilestone;

            return {
              ...updatedMilestone,
              tasks: oldMilestone.tasks.map(oldTask => {
                const newTask = updatedMilestone.tasks.find(
                  t => t.task_id === oldTask.task_id
                );

                return newTask ?? oldTask;
              }),
            };
          }),
        };
      });
    } catch {
      setRoadmap(prevRoadmap);
      setError("Couldn't update task. Try again.");
      setTimeout(() => setError(null), 3000);
    } finally {
      setTogglingTaskId(null);
    }
  }

  const allTasks = roadmap?.milestones.flatMap(m => m.tasks) ?? [];
  const doneTasks = allTasks.filter(t => t.completed).length;
  const pct = allTasks.length > 0 ? Math.round((doneTasks / allTasks.length) * 100) : 0;
  const completedMilestones = roadmap?.milestones.filter(m => m.status === "COMPLETED").length ?? 0;
  // Keep a stable order so completing a task doesn't reorder the list.
  const sortedMilestones = [...(roadmap?.milestones ?? [])].sort(
    (a, b) => a.milestone_id - b.milestone_id
  );

  // Token badge: color shifts green → amber → red as the balance runs low.
  const tokenRatio = usage && usage.limit > 0 ? usage.remaining / usage.limit : 1;
  const tokenColor = tokenRatio > 0.5 ? GREEN : tokenRatio > 0.2 ? "#e0a020" : "#ef5350";

  // Character counter: warn as the goal approaches the server-enforced max.
  const charRatio = goal.length / MAX_GOAL_LENGTH;
  const charColor = charRatio >= 1 ? "#ef5350" : charRatio >= 0.8 ? "#e0a020" : GRAY;

  return (
    <div style={styles.page}>
      <div style={styles.inner}>

        {!roadmap && (
          <>
            <div style={styles.hero}>
              <h1 style={styles.heroTitle}>TUMgoal</h1>
              <p style={styles.heroSub}>Tell us your learning goal — we'll build your roadmap.</p>
            </div>

            {usage && (
              <div style={styles.tokenBar}>
                <div style={styles.tokenRow}>
                  <span style={styles.tokenLabel}>🎫 Remaining tokens</span>
                  <span style={{ ...styles.tokenValue, color: tokenColor }}>
                    {usage.remaining.toLocaleString()} / {usage.limit.toLocaleString()}
                  </span>
                </div>
                <div style={styles.tokenTrack}>
                  <div style={{ ...styles.tokenFill, width: `${Math.max(0, Math.min(100, tokenRatio * 100))}%`, background: tokenColor }} />
                </div>
              </div>
            )}

            <form onSubmit={handleSubmit} style={styles.form}>
              <input
                style={styles.input}
                type="text"
                placeholder="e.g. I want to learn Machine Learning"
                value={goal}
                onChange={(e) => setGoal(e.target.value.slice(0, MAX_GOAL_LENGTH))}
                maxLength={MAX_GOAL_LENGTH}
                disabled={loading}
              />
              <button style={styles.button} type="submit" disabled={loading || !goal.trim()}>
                {loading ? "Generating…" : "Generate Roadmap"}
              </button>
            </form>
            <div style={styles.charHintRow}>
              <span style={{ color: charColor, fontWeight: charRatio >= 0.8 ? 600 : 400 }}>
                {goal.length} / {MAX_GOAL_LENGTH} characters
              </span>
              {charRatio >= 1 && <span style={{ color: "#ef5350" }}>Maximum length reached</span>}
            </div>
          </>
        )}

        {error && <div style={styles.error}>⚠️ {error}</div>}

        {loading && (
          <div style={styles.loadingBox}>
            <div style={styles.spinner} />
            <p style={{ color: "#9aa4bd", marginTop: 12 }}>Searching courses and building your roadmap…</p>
          </div>
        )}

        {/* Saved roadmaps — shown when no roadmap is open */}
        {!loading && !roadmap && myRoadmaps.length > 0 && (
          <div style={styles.savedWrap}>
            <h2 style={styles.savedTitle}>Your roadmaps</h2>
            {myRoadmaps.map((r) => {
              const p = roadmapProgress(r);
              const barColor = p.pct === 100 ? GREEN : BRAND_GLOW;
              return (
                <button key={r.roadmap_id} style={styles.savedCard} onClick={() => setRoadmap(r)}>
                  <div style={styles.savedCardRow}>
                    <span style={styles.savedCardTitle}>{niceTitle(r.title)}</span>
                    <span style={{ fontWeight: 700, color: barColor }}>{p.pct}%</span>
                  </div>
                  <div style={styles.savedMeta}>
                    {r.created_date ? new Date(r.created_date).toLocaleDateString() : ""} · {p.done}/{p.total} tasks
                  </div>
                  <div style={styles.tokenTrack}>
                    <div style={{ ...styles.tokenFill, width: `${p.pct}%`, background: barColor }} />
                  </div>
                </button>
              );
            })}
          </div>
        )}

        {/* Active roadmap — glowing connected timeline */}
        {roadmap && roadmap.milestones.length > 0 && (
          <>
            <div style={styles.rmHeader}>
              <div style={styles.rmHeaderLeft}>
                <div style={styles.rmLogo}>🗺️</div>
                <div>
                  <h1 style={styles.rmTitle}>Your Roadmap</h1>
                  <p style={styles.rmGoal}>Goal: {niceTitle(roadmap.title)}</p>
                </div>
              </div>
              <button style={styles.ghostBtn} onClick={() => { setRoadmap(null); setGoal(""); refreshMyRoadmaps(); }}>
                ← My roadmaps
              </button>
            </div>

            {/* Progress bar */}
            <div style={styles.progressWrap}>
              <div style={styles.progressLabels}>
                <span>{completedMilestones} / {roadmap.milestones.length} milestones</span>
                <span>{doneTasks} / {allTasks.length} tasks</span>
                <span style={{ fontWeight: 700, color: pct === 100 ? GREEN : BRAND_GLOW }}>{pct}%</span>
              </div>
              <div style={styles.progressTrack}>
                <div style={{
                  ...styles.progressFill,
                  width: `${pct}%`,
                  background: pct === 100 ? GREEN : `linear-gradient(90deg, ${BRAND}, ${BRAND_GLOW})`,
                }} />
              </div>
            </div>

            <div style={styles.timeline}>
              {sortedMilestones.map((milestone, i) => {
                const st = STATUS[milestone.status ?? "NOT_STARTED"];
                const isLast = i === sortedMilestones.length - 1;
                const icon = MILESTONE_ICONS[i % MILESTONE_ICONS.length];
                return (
                  <div key={milestone.milestone_id} style={styles.msRow}>
                    <div style={styles.msNodeCol}>
                      {!isLast && <div style={styles.msConnector} />}
                      <div style={{
                        ...styles.msNode,
                        borderColor: st.color,
                        color: st.color,
                        boxShadow: `0 0 14px ${st.color}66, inset 0 0 8px ${st.color}22`,
                      }}>
                        {i + 1}
                      </div>
                    </div>

                    <div style={styles.msCard}>
                      <div style={styles.msCardHeader}>
                        <div style={styles.msIconTile}>{icon}</div>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={styles.msTitleRow}>
                            <h3 style={styles.msTitle}>{milestone.title}</h3>
                            <span style={{
                              ...styles.msBadge,
                              color: st.color,
                              borderColor: `${st.color}55`,
                              background: `${st.color}18`,
                            }}>
                              <span style={{ width: 7, height: 7, borderRadius: "50%", background: st.color, display: "inline-block" }} />
                              {st.label}
                            </span>
                          </div>
                          {milestone.description && <p style={styles.msDesc}>{milestone.description}</p>}
                        </div>
                      </div>

                      <ul style={{
                        ...styles.taskList,
                        pointerEvents: togglingTaskId !== null ? "none" : "auto",
                        opacity: togglingTaskId !== null ? 0.65 : 1,
                      }}>
                        {[...milestone.tasks]
                          .sort((a, b) => a.order_index - b.order_index)
                          .map((task) => (
                            <li
                              key={task.task_id}
                              style={{ ...styles.task, opacity: togglingTaskId === task.task_id ? 0.5 : 1 }}
                              onClick={() => task.task_id && handleToggleTask(task.task_id)}
                            >
                              <TaskCheck done={task.completed} />
                              <span style={{
                                textDecoration: task.completed ? "line-through" : "none",
                                color: task.completed ? "#6b7590" : "#d6dcec",
                              }}>
                                {task.title}
                              </span>
                            </li>
                          ))}
                      </ul>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Legend */}
            <div style={styles.legend}>
              {(["COMPLETED", "IN_PROGRESS", "NOT_STARTED"] as Status[]).map((s) => (
                <div key={s} style={styles.legendItem}>
                  <span style={{ ...styles.legendDot, background: STATUS[s].color }} />
                  {STATUS[s].label}
                </div>
              ))}
            </div>

            <button
              style={styles.primaryBtn}
              onClick={() => { setRoadmap(null); setGoal(""); refreshMyRoadmaps(); }}
            >
              + New roadmap
            </button>
          </>
        )}
      </div>
    </div>
  );
}

function optimisticallyToggle(roadmap: RoadmapResponse, taskId: number): RoadmapResponse {
  return {
    ...roadmap,
    milestones: roadmap.milestones.map((m) => {
      const tasks = m.tasks.map((t) =>
        t.task_id === taskId ? { ...t, completed: !t.completed } : t
      );
      const allDone = tasks.every((t) => t.completed);
      const noneDone = tasks.every((t) => !t.completed);
      const status: Status = allDone ? "COMPLETED" : noneDone ? "NOT_STARTED" : "IN_PROGRESS";
      return { ...m, tasks, status };
    }),
  };
}

const CARD_BG = "rgba(255,255,255,0.045)";
const CARD_BORDER = "1px solid rgba(255,255,255,0.09)";

const styles: Record<string, React.CSSProperties> = {
  page: {
    minHeight: "calc(100vh - 52px)",
    width: "100%",
    boxSizing: "border-box",
    // index.css (Vite template leftover) sets #root { text-align: center };
    // reset to left here so milestone text aligns naturally.
    textAlign: "left",
    background: "radial-gradient(1200px 600px at 20% -10%, #12224e 0%, rgba(18,34,78,0) 60%), linear-gradient(160deg, #0a0f2c 0%, #0c1436 55%, #090e28 100%)",
    padding: "40px 24px 64px",
    fontFamily: "'Segoe UI', sans-serif",
    color: "#e8ecf5",
  },
  inner: {
    maxWidth: 820,
    margin: "0 auto",
  },
  hero: {
    textAlign: "center",
    marginBottom: 32,
  },
  heroTitle: {
    fontSize: 38,
    fontWeight: 800,
    margin: 0,
    letterSpacing: -0.5,
    background: `linear-gradient(90deg, #ffffff, ${BRAND_GLOW})`,
    WebkitBackgroundClip: "text",
    WebkitTextFillColor: "transparent",
  },
  heroSub: {
    color: "#9aa4bd",
    marginTop: 10,
    fontSize: 16,
  },
  tokenBar: {
    background: CARD_BG,
    border: CARD_BORDER,
    borderRadius: 12,
    padding: "12px 16px",
    marginBottom: 20,
    backdropFilter: "blur(10px)",
  },
  tokenRow: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 },
  tokenLabel: { fontSize: 13, fontWeight: 600, color: "#b8c1d9" },
  tokenValue: { fontSize: 14, fontWeight: 700, fontVariantNumeric: "tabular-nums" },
  tokenTrack: { background: "rgba(255,255,255,0.09)", borderRadius: 6, height: 8, overflow: "hidden" },
  tokenFill: { height: "100%", borderRadius: 6, transition: "width 0.4s ease, background 0.4s ease" },
  form: { display: "flex", gap: 12, marginBottom: 12 },
  input: {
    flex: 1,
    padding: "14px 16px",
    fontSize: 15,
    color: "#e8ecf5",
    background: "rgba(255,255,255,0.05)",
    border: "1px solid rgba(255,255,255,0.12)",
    borderRadius: 12,
    outline: "none",
  },
  button: {
    padding: "14px 24px",
    fontSize: 15,
    fontWeight: 700,
    background: `linear-gradient(90deg, ${BRAND}, ${BRAND_GLOW})`,
    color: "#fff",
    border: "none",
    borderRadius: 12,
    cursor: "pointer",
    whiteSpace: "nowrap",
    boxShadow: `0 6px 20px ${BRAND}55`,
  },
  charHintRow: { display: "flex", justifyContent: "space-between", fontSize: 13, margin: "0 4px 24px" },
  error: {
    background: "rgba(239,83,80,0.12)",
    border: "1px solid rgba(239,83,80,0.4)",
    color: "#ff9b98",
    padding: "12px 16px",
    borderRadius: 10,
    marginBottom: 24,
  },
  loadingBox: { textAlign: "center", padding: "48px 0" },
  spinner: {
    width: 40, height: 40,
    border: "4px solid rgba(255,255,255,0.12)",
    borderTop: `4px solid ${BRAND_GLOW}`,
    borderRadius: "50%",
    animation: "spin 0.8s linear infinite",
    margin: "0 auto",
  },
  savedWrap: { marginTop: 8 },
  savedTitle: { fontSize: 18, fontWeight: 700, color: "#e8ecf5", margin: "0 0 14px" },
  savedCard: {
    display: "block",
    width: "100%",
    textAlign: "left",
    background: CARD_BG,
    border: CARD_BORDER,
    borderRadius: 12,
    padding: "14px 16px",
    marginBottom: 12,
    cursor: "pointer",
    fontFamily: "inherit",
    color: "#e8ecf5",
    backdropFilter: "blur(10px)",
  },
  savedCardRow: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 },
  savedCardTitle: { fontSize: 15, fontWeight: 600, color: "#eef2fb", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", paddingRight: 12 },
  savedMeta: { fontSize: 12, color: "#8a93a8", marginBottom: 8 },

  rmHeader: { display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 16, marginBottom: 24 },
  rmHeaderLeft: { display: "flex", alignItems: "center", gap: 14 },
  rmLogo: {
    width: 46, height: 46, borderRadius: 12, flexShrink: 0,
    display: "flex", alignItems: "center", justifyContent: "center", fontSize: 22,
    background: `linear-gradient(135deg, ${BRAND}, ${BRAND_GLOW})`,
    boxShadow: `0 6px 18px ${BRAND}55`,
  },
  rmTitle: { fontSize: 26, fontWeight: 800, margin: 0, color: "#f2f5fc" },
  rmGoal: { margin: "2px 0 0", fontSize: 14, color: BRAND_GLOW, fontWeight: 500 },
  ghostBtn: {
    padding: "9px 16px",
    fontSize: 13, fontWeight: 600,
    color: "#cdd5e8",
    background: "rgba(255,255,255,0.05)",
    border: "1px solid rgba(255,255,255,0.14)",
    borderRadius: 10, cursor: "pointer", whiteSpace: "nowrap",
  },
  progressWrap: { marginBottom: 30 },
  progressLabels: { display: "flex", justifyContent: "space-between", fontSize: 13, color: "#9aa4bd", marginBottom: 6 },
  progressTrack: { background: "rgba(255,255,255,0.09)", borderRadius: 8, height: 10, overflow: "hidden" },
  progressFill: { height: "100%", borderRadius: 8, transition: "width 0.4s ease" },

  timeline: { position: "relative" },
  msRow: { display: "flex", gap: 16, marginBottom: 26 },
  msNodeCol: { position: "relative", width: 44, flexShrink: 0, display: "flex", justifyContent: "center" },
  msConnector: {
    position: "absolute",
    top: 40,
    bottom: -26,
    left: "50%",
    width: 2,
    marginLeft: -1,
    background: `linear-gradient(180deg, ${BRAND_GLOW}bb, ${BRAND_GLOW}33)`,
  },
  msNode: {
    position: "relative",
    zIndex: 1,
    width: 40, height: 40, borderRadius: "50%",
    display: "flex", alignItems: "center", justifyContent: "center",
    fontWeight: 800, fontSize: 16,
    background: "#0c1330",
    border: "2px solid",
  },
  msCard: {
    flex: 1,
    minWidth: 0,
    background: CARD_BG,
    border: CARD_BORDER,
    borderRadius: 16,
    padding: "18px 20px",
    backdropFilter: "blur(10px)",
    boxShadow: "0 8px 30px rgba(0,0,0,0.25)",
  },
  msCardHeader: { display: "flex", gap: 14, marginBottom: 14 },
  msIconTile: {
    width: 46, height: 46, borderRadius: 12, flexShrink: 0,
    display: "flex", alignItems: "center", justifyContent: "center", fontSize: 22,
    background: "rgba(77,155,255,0.14)",
    border: "1px solid rgba(77,155,255,0.28)",
  },
  msTitleRow: { display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 },
  msTitle: { fontSize: 17, fontWeight: 700, margin: 0, color: "#f2f5fc" },
  msBadge: {
    display: "inline-flex", alignItems: "center", gap: 6,
    fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4,
    padding: "4px 10px", borderRadius: 999, border: "1px solid", whiteSpace: "nowrap",
  },
  msDesc: { margin: "6px 0 0", fontSize: 13.5, color: "#9aa4bd", lineHeight: 1.5 },
  taskList: { listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 10, transition: "opacity 0.2s ease" },
  task: {
    display: "flex", alignItems: "flex-start", gap: 10,
    cursor: "pointer", fontSize: 14, lineHeight: 1.4,
    padding: "2px 0",
  },

  legend: {
    display: "flex", gap: 20, flexWrap: "wrap",
    marginTop: 8, marginBottom: 28,
    padding: "12px 16px",
    background: CARD_BG, border: CARD_BORDER, borderRadius: 12,
    fontSize: 13, color: "#b8c1d9",
  },
  legendItem: { display: "flex", alignItems: "center", gap: 8 },
  legendDot: { width: 10, height: 10, borderRadius: "50%", display: "inline-block" },

  primaryBtn: {
    width: "100%",
    padding: "14px 24px",
    fontSize: 15, fontWeight: 700,
    background: `linear-gradient(90deg, ${BRAND}, ${BRAND_GLOW})`,
    color: "#fff", border: "none", borderRadius: 12, cursor: "pointer",
    boxShadow: `0 6px 20px ${BRAND}55`,
  },
};
