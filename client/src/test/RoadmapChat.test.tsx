// RoadmapChat.test.tsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import RoadmapChat from "../pages/RoadmapChat";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ used: 0, limit: 10000, remaining: 10000 }),
  }));
});
afterEach(() => vi.restoreAllMocks());

const roadmapPayload = {
  roadmap_id: 1,
  milestones: [
    {
      milestone_id: 1,
      title: "Python Basics",
      description: "Learn the fundamentals",
      status: "NOT_STARTED",
      tasks: [
        { task_id: 1, title: "Install Python", completed: false, order_index: 0 },
        { task_id: 2, title: "Learn Variables", completed: false, order_index: 1 },
      ],
    },
  ],
};

const usageMock = { ok: true, status: 200, json: async () => ({ used: 0, limit: 10000, remaining: 10000 }) };

// ── Input validation ──────────────────────────────────────────────────────────

test("disables button when input is empty", () => {
  render(<RoadmapChat />);
  expect(screen.getByRole("button", { name: /generate/i })).toBeDisabled();
});

test("enforces 200-character limit on input", async () => {
  render(<RoadmapChat />);
  const input = screen.getByRole("textbox");
  await userEvent.type(input, "a".repeat(210));
  expect(input).toHaveValue("a".repeat(200));
});

// ── 200 path ────────────────────────────────────────────────────────────────

test("renders milestones after successful generation", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ used: 100, limit: 10000, remaining: 9900 }) })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ used: 200, limit: 10000, remaining: 9800 }) })
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));

  await waitFor(() => expect(screen.getByText("Python Basics")).toBeInTheDocument());
  expect(screen.getByText("Install Python")).toBeInTheDocument();
});

// ── Error states ──────────────────────────────────────────────────────────────

test("shows error on 429 (quota exceeded)", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageMock)
    .mockResolvedValueOnce({ ok: false, status: 429, json: async () => ({ detail: "Token quota exceeded." }) })
    .mockResolvedValueOnce(usageMock)
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn ML");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));

  await waitFor(() => expect(screen.getByText(/token quota exceeded/i)).toBeInTheDocument());
});

test("shows error on 422 (goal too long)", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageMock)
    .mockResolvedValueOnce({ ok: false, status: 422, json: async () => ({ detail: "Goal too long." }) })
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));

  await waitFor(() => expect(screen.getByText(/goal too long/i)).toBeInTheDocument());
});

test("shows error on network failure", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageMock)
    .mockRejectedValueOnce(new Error("Network error"))
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));

  await waitFor(() => expect(screen.getByText(/network error/i)).toBeInTheDocument());
});

// ── Task toggling ─────────────────────────────────────────────────────────────

test("optimistically checks task and sends PATCH request", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ used: 100, limit: 10000, remaining: 9900 }) })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ used: 200, limit: 10000, remaining: 9800 }) })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({
      ...roadmapPayload,
      milestones: roadmapPayload.milestones.map(m => ({
        ...m,
        status: "IN_PROGRESS",
        tasks: m.tasks.map(t => t.task_id === 1 ? { ...t, completed: true } : t),
      })),
    }) })
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));
  await waitFor(() => expect(screen.getByText("Install Python")).toBeInTheDocument());

  await userEvent.click(screen.getByText("Install Python"));

  await waitFor(() => {
    const checkboxes = screen.getAllByRole("checkbox");
    expect(checkboxes[0]).toBeChecked();
  });

  expect(vi.mocked(fetch)).toHaveBeenCalledWith(
    expect.stringContaining("/tasks/1/complete"),
    expect.objectContaining({ method: "PATCH" })
  );
});

test("reverts task on PATCH failure", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageMock)
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload })
    .mockResolvedValueOnce(usageMock)
    .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) })
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));
  await waitFor(() => expect(screen.getByText("Install Python")).toBeInTheDocument());

  await userEvent.click(screen.getByText("Install Python"));

  await waitFor(() => expect(screen.getByText(/couldn't update task/i)).toBeInTheDocument());

  const checkboxes = screen.getAllByRole("checkbox");
  expect(checkboxes[0]).not.toBeChecked();
});

// ── Progress bar ──────────────────────────────────────────────────────────────

test("progress bar updates correctly after toggling a task", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageMock)
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload })
    .mockResolvedValueOnce(usageMock)
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({
      ...roadmapPayload,
      milestones: roadmapPayload.milestones.map(m => ({
        ...m,
        status: "IN_PROGRESS",
        tasks: m.tasks.map(t => t.task_id === 1 ? { ...t, completed: true } : t),
      })),
    }) })
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));
  await waitFor(() => expect(screen.getByText("Install Python")).toBeInTheDocument());

  // Initially 0% (0 of 2 tasks done)
  expect(screen.getByText("0%")).toBeInTheDocument();

  await userEvent.click(screen.getByText("Install Python"));

  // After toggling 1 of 2 tasks: 50%
  await waitFor(() => expect(screen.getByText("50%")).toBeInTheDocument());
});

// ── Start Over ────────────────────────────────────────────────────────────────

test("start over resets roadmap and clears input", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageMock)
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload })
    .mockResolvedValueOnce(usageMock)
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));
  await waitFor(() => expect(screen.getByText("Python Basics")).toBeInTheDocument());

  await userEvent.click(screen.getByRole("button", { name: /start over/i }));

  expect(screen.queryByText("Python Basics")).not.toBeInTheDocument();
  expect(screen.getByRole("textbox")).toHaveValue("");
});