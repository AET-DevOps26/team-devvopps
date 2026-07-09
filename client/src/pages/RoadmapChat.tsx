import { useState } from "react";

const API_URL = "/api";

// Mirrors MAX_GOAL_CHARS in llm-service — requests longer than this are
// rejected server-side with 422, so block them in the input directly.
const MAX_GOAL_LENGTH = 200;

interface Task {
  title: string;
  completed: boolean;
}

interface Milestone {
  title: string;
  description: string;
  tasks: Task[];
}

interface RoadmapResponse {
  milestones: Milestone[];
}

export default function RoadmapChat() {
  const [goal, setGoal] = useState("");
  const [roadmap, setRoadmap] = useState<RoadmapResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

      if (res.status === 429) {
        const data = await res.json();
        setError(data.detail || "Token quota exceeded.");
        return;
      }

      if (!res.ok) throw new Error(`Error ${res.status}: ${res.statusText}`);
      const data: RoadmapResponse = await res.json();
      setRoadmap(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h1 style={styles.title}>TUMgoal</h1>
        <p style={styles.subtitle}>Tell us your learning goal — we'll build your roadmap.</p>
      </div>

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
          {loading ? "Generating..." : "Generate Roadmap"}
        </button>
      </form>
      {goal.length >= MAX_GOAL_LENGTH * 0.8 && (
        <p style={styles.charHint}>
          {goal.length}/{MAX_GOAL_LENGTH} characters
        </p>
      )}

      {error && (
        <div style={styles.error}>
          ⚠️ {error}
        </div>
      )}

      {loading && (
        <div style={styles.loadingBox}>
          <div style={styles.spinner} />
          <p style={{ color: "#888", marginTop: 12 }}>Searching courses and building your roadmap...</p>
        </div>
      )}

      {roadmap && roadmap.milestones.length > 0 && (
        <div style={styles.roadmap}>
          <h2 style={styles.roadmapTitle}>Your Learning Roadmap</h2>
          {roadmap.milestones.map((milestone, i) => (
            <div key={i} style={styles.milestone}>
              <div style={styles.milestoneHeader}>
                <span style={styles.milestoneNumber}>{i + 1}</span>
                <div>
                  <h3 style={styles.milestoneTitle}>{milestone.title}</h3>
                  <p style={styles.milestoneDesc}>{milestone.description}</p>
                </div>
              </div>
              <ul style={styles.taskList}>
                {milestone.tasks?.map((task, j) => (
                  <li key={j} style={styles.task}>
                    <span style={styles.taskDot}>→</span>
                    {task.title}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  );
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
  charHint: {
    color: "#888",
    fontSize: 13,
    margin: "-24px 0 24px 4px",
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
  roadmap: {
    display: "flex",
    flexDirection: "column",
    gap: 20,
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
