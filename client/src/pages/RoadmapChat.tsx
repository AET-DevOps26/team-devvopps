import { useState } from "react";

const API_URL = "/api";

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
  status: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";
  tasks: Task[];
}

interface RoadmapResponse {
  roadmap_id: number;
  milestones: Milestone[];
}

export default function RoadmapChat() {
  const [goal, setGoal] = useState("");
  const [roadmap, setRoadmap] = useState<RoadmapResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [togglingTaskId, setTogglingTaskId] = useState<number | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!goal.trim()) return;

    setLoading(true);
    setError(null);
    setRoadmap(null);

    try {
      const res = await fetch(`${API_URL}/roadmaps/generate?goal=${encodeURIComponent(goal)}`, {
        method: "POST",
      });

      if (!res.ok) throw new Error(`Error ${res.status}: ${res.statusText}`);
      const data: RoadmapResponse = await res.json();
      setRoadmap(data);

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
        { method: "PATCH" }
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
  const sortedMilestones = [...(roadmap?.milestones ?? [])].sort((a, b) => {
    const aCompleted = isMilestoneCompleted(a);
    const bCompleted = isMilestoneCompleted(b);

    // completed milestones go down
    if (aCompleted && !bCompleted) return 1;
    if (!aCompleted && bCompleted) return -1;

    return a.milestone_id - b.milestone_id;
  });

  function isMilestoneCompleted(milestone: Milestone) {
    return milestone.tasks.length > 0 &&
      milestone.tasks.every(task => task.completed);
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h1 style={styles.title}>TUMgoal</h1>
        <p style={styles.subtitle}>Tell us your learning goal — we'll build your roadmap.</p>
      </div>

      {!roadmap && (
        <form onSubmit={handleSubmit} style={styles.form}>
          <input
            style={styles.input}
            type="text"
            placeholder="e.g. I want to learn Machine Learning"
            value={goal}
            onChange={(e) => setGoal(e.target.value)}
            disabled={loading}
          />
          <button style={styles.button} type="submit" disabled={loading || !goal.trim()}>
            {loading ? "Generating..." : "Generate Roadmap"}
          </button>
        </form>
      )}

      {error && <div style={styles.error}>⚠️ {error}</div>}

      {loading && (
        <div style={styles.loadingBox}>
          <div style={styles.spinner} />
          <p style={{ color: "#888", marginTop: 12 }}>Searching courses and building your roadmap...</p>
        </div>
      )}

      {roadmap && roadmap.milestones.length > 0 && (
        <>
          {/* Progress bar */}
          <div style={styles.progressWrap}>
            <div style={styles.progressLabels}>
              <span>{completedMilestones} / {roadmap.milestones.length} milestones</span>
              <span>{doneTasks} / {allTasks.length} tasks</span>
              <span style={{ fontWeight: 700, color: pct === 100 ? "#2e7d32" : "#0065BD" }}>{pct}%</span>
            </div>
            <div style={styles.progressTrack}>
              <div style={{
                ...styles.progressFill,
                width: `${pct}%`,
                background: pct === 100 ? "#2e7d32" : "#0065BD",
              }} />
            </div>
          </div>

          <div style={styles.roadmap}>
            <h2 style={styles.roadmapTitle}>Your Learning Roadmap</h2>
            {sortedMilestones.map((milestone, i) => {
              const badgeColor = {
                NOT_STARTED: "#999",
                IN_PROGRESS: "#0065BD",
                COMPLETED: "#2e7d32",
              }[milestone.status ?? "NOT_STARTED"];

              return (
                <div key={milestone.milestone_id} style={styles.milestone}>
                  <div style={styles.milestoneHeader}>
                    <span style={{
                      ...styles.milestoneNumber,
                      background: milestone.status === "COMPLETED" ? "#2e7d32" : "#0065BD",
                    }}>
                      {i + 1}
                    </span>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                        <h3 style={styles.milestoneTitle}>{milestone.title}</h3>
                        <span style={{ fontSize: 11, fontWeight: 700, color: badgeColor, textTransform: "uppercase", letterSpacing: 0.5 }}>
                          {(milestone.status ?? "NOT_STARTED").replace("_", " ")}
                        </span>
                      </div>
                      <p style={styles.milestoneDesc}>{milestone.description}</p>
                    </div>
                  </div>
                  <ul style={{
                        ...styles.taskList,
                        pointerEvents: togglingTaskId !== null ? "none" : "auto",
                        opacity: togglingTaskId !== null ? 0.6 : 1,
                      }}>
                    {[...milestone.tasks]
                      .sort((a, b) => a.order_index - b.order_index)
                      .map((task) => (
                      <li
                        key={task.task_id}
                        style={{
                          ...styles.task,
                          cursor: "pointer",
                          opacity: togglingTaskId === task.task_id ? 0.5 : 1,
                        }}
                        onClick={() => task.task_id && handleToggleTask(task.task_id)}
                      >
                        <input
                          type="checkbox"
                          checked={task.completed}
                          readOnly
                          style={{ accentColor: "#0065BD", marginRight: 6, marginTop: 2, flexShrink: 0 }}
                        />
                        <span style={{
                          textDecoration: task.completed ? "line-through" : "none",
                          color: task.completed ? "#999" : "#333",
                        }}>
                          {task.title}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>

          <button
            style={{ ...styles.button, marginTop: 32, width: "100%" }}
            onClick={() => { setRoadmap(null); setGoal(""); }}
          >
            Start Over
          </button>
        </>
      )}
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
      const status: Milestone["status"] = allDone ? "COMPLETED" : noneDone ? "NOT_STARTED" : "IN_PROGRESS";
      return { ...m, tasks, status };
    }),
  };
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    maxWidth: 720,
    margin: "0 auto",
    padding: "48px 24px",
    fontFamily: "'Segoe UI', sans-serif",
    color: "#1a1a1a",
  },
  header: {
    textAlign: "center",
    marginBottom: 40,
  },
  title: {
    fontSize: 36,
    fontWeight: 700,
    margin: 0,
    color: "#0065BD",
  },
  subtitle: {
    color: "#666",
    marginTop: 8,
    fontSize: 16,
  },
  form: {
    display: "flex",
    gap: 12,
    marginBottom: 32,
  },
  input: {
    flex: 1,
    padding: "14px 16px",
    fontSize: 15,
    border: "2px solid #e0e0e0",
    borderRadius: 10,
    outline: "none",
  },
  button: {
    padding: "14px 24px",
    fontSize: 15,
    fontWeight: 600,
    background: "#0065BD",
    color: "#fff",
    border: "none",
    borderRadius: 10,
    cursor: "pointer",
    whiteSpace: "nowrap",
  },
  error: {
    background: "#fff3f3",
    border: "1px solid #ffcdd2",
    color: "#c62828",
    padding: "12px 16px",
    borderRadius: 8,
    marginBottom: 24,
  },
  loadingBox: {
    textAlign: "center",
    padding: "48px 0",
  },
  spinner: {
    width: 40,
    height: 40,
    border: "4px solid #e0e0e0",
    borderTop: "4px solid #0065BD",
    borderRadius: "50%",
    animation: "spin 0.8s linear infinite",
    margin: "0 auto",
  },
  progressWrap: { 
    marginBottom: 28 
  },
  progressLabels: { 
    display: "flex", 
    justifyContent: "space-between", 
    fontSize: 13, 
    color: "#555", 
    marginBottom: 6 
  },
  progressTrack: { 
    background: "#e5e5e5", 
    borderRadius: 8, 
    height: 10, 
    overflow: "hidden" 
  },
  progressFill: { 
    height: "100%", 
    borderRadius: 8, 
    transition: "width 0.4s ease" 
  },
  roadmap: {
    display: "flex",
    flexDirection: "column",
    gap: 20,
    transition: "all 0.4s ease",
  },
  roadmapTitle: {
    fontSize: 22,
    fontWeight: 700,
    marginBottom: 8,
    color: "#0065BD",
  },
  milestone: {
    background: "#f8f9ff",
    border: "1px solid #dde3ff",
    borderRadius: 12,
    padding: "20px 24px",
    transition: "transform 0.4s ease, opacity 0.4s ease",
  },
  milestoneHeader: {
    display: "flex",
    gap: 16,
    alignItems: "flex-start",
    marginBottom: 14,
  },
  milestoneNumber: {
    background: "#0065BD",
    color: "#fff",
    borderRadius: "50%",
    width: 32,
    height: 32,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontWeight: 700,
    fontSize: 14,
    flexShrink: 0,
  },
  milestoneTitle: {
    margin: 0,
    fontSize: 16,
    fontWeight: 700,
  },
  milestoneDesc: {
    margin: "4px 0 0",
    color: "#555",
    fontSize: 14,
  },
  taskList: {
    listStyle: "none",
    padding: 0,
    margin: 0,
    display: "flex",
    flexDirection: "column",
    gap: 8,
  },
  task: {
    display: "flex",
    gap: 10,
    fontSize: 14,
    color: "#333",
    alignItems: "flex-start",
  },
  taskDot: {
    color: "#0065BD",
    fontWeight: 700,
    flexShrink: 0,
  },
};
