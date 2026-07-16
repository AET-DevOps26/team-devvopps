import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import RoadmapChat from "../../src/pages/RoadmapChat";


// Tests for the RoadmapChat page component.
//
// The real backend API is replaced with mocked fetch responses to verify
// different UI states and user interactions, including:
// - roadmap generation
// - error handling
// - task completion
// - saved roadmaps
// - token usage display
// - feature flags
// - input validation
 


// ── Mount fetch order (3 parallel calls via Promise.all) ──────────────────────
// 1. /api/llm/usage
// 2. /api/roadmaps
// 3. /api/features
// Any additional mocked responses after these three calls represent later
// user-triggered actions, such as refreshing data or updating a roadmap.

beforeEach(() => {
  // Reset browser state before every test.
  //
  // jsdom persists window.location.hash across tests. The component reads the
  // hash on mount to restore a roadmap view, so a stale hash from a previous
  // test causes it to open in detail mode instead of list mode.
  window.location.hash = "";

  // Replace the browser fetch API with a default mock response.
  //
  // Components under test should not communicate with real backend services.
  // A default successful response keeps tests isolated and allows individual
  // tests to override only the API calls they are interested in.
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ used: 0, limit: 10000, remaining: 10000 }),
  }));
});
afterEach(() => {
  // Clean up global modifications after each test.
  //
  // fetch is replaced globally during tests. Restoring mocks ensures that
  // mocked functions, spies, and browser API replacements cannot leak into
  // another test case.
  window.location.hash = "";
  vi.restoreAllMocks();
});

// ── Helpers ───────────────────────────────────────────────────────────────────

// Generates a token usage response with calculated values.
//
// The component derives UI behavior from the remaining quota percentage,
// therefore tests only need to specify the remaining amount rather than
// manually calculating used tokens.
const makeUsage = (remaining: number, limit = 10000) => ({
  ok: true,
  status: 200,
  json: async () => ({ used: limit - remaining, limit, remaining }),
});

// Predefined quota states used to verify different UI thresholds.
const usageFull = () => makeUsage(10000);
const usageLow  = () => makeUsage(2000);   // 20% remaining → orange boundary
const usageCrit = () => makeUsage(200);    // 2%  remaining → red

// Represents the backend response when the user has no generated roadmaps.
// Used to verify the empty-state UI without requiring database setup.
const emptyRoadmaps = () => ({ ok: true, status: 200, json: async () => [] });

// Feature flag helpers — the third call in every Promise.all mount batch.
const featuresEnabled  = () => ({
  ok: true,
  status: 200,
  json: async () => [{ name: "tokenQuota", enabled: true }],
});
const featuresDisabled = () => ({
  ok: true,
  status: 200,
  json: async () => [{ name: "tokenQuota", enabled: false }],
});
// Simulates a failed /api/features fetch; component falls back to defaults
// (treat every flag as enabled).
const featuresFailed = () => ({ ok: false, status: 500, json: async () => ({}) });

// ── Fixtures ──────────────────────────────────────────────────────────────────

// Static roadmap objects used as realistic backend responses.
const roadmapPayload = {
  roadmap_id: 1,
  title: "My ML Roadmap",
  created_date: "2024-06-01T00:00:00Z",
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

const roadmapPayload2 = {
  roadmap_id: 2,
  title: "Web Dev Roadmap",
  created_date: "2024-07-01T00:00:00Z",
  milestones: [
    {
      milestone_id: 2,
      title: "HTML Basics",
      description: "Learn HTML",
      status: "COMPLETED",
      tasks: [
        { task_id: 3, title: "Learn tags", completed: true, order_index: 0 },
      ],
    },
  ],
};

// Default reusable usage response for tests that only require a successful
// quota request and do not need a specific remaining-token scenario.
const usageMock = { ok: true, status: 200, json: async () => ({ used: 0, limit: 10000, remaining: 10000 }) };

// ── Input validation ──────────────────────────────────────────────────────────

test("disables button when input is empty", () => {
  render(<RoadmapChat />);
  expect(screen.getByRole("button", { name: /generate/i })).toBeDisabled();
});

// ── 200 path ──────────────────────────────────────────────────────────────────

test("renders milestones after successful generation", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageFull())       // mount: usage
    .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
    .mockResolvedValueOnce(featuresEnabled()) // mount: features
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => roadmapPayload,
    })                                        // generate
    .mockResolvedValueOnce(usageFull())       // usage refresh
    .mockResolvedValueOnce(emptyRoadmaps())   // roadmaps refresh
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));

  await waitFor(() => expect(screen.getByText("Python Basics")).toBeInTheDocument());
  expect(screen.getByText("Install Python")).toHaveStyle("text-decoration: none");
});

// ── Error states ──────────────────────────────────────────────────────────────

test("shows error on 429 (quota exceeded)", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageFull())       // mount: usage
    .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
    .mockResolvedValueOnce(featuresEnabled()) // mount: features
    .mockResolvedValueOnce({ ok: false, status: 429, json: async () => ({ detail: "Token quota exceeded." }) })
    .mockResolvedValueOnce(usageFull())       // usage refresh after 429
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn ML");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));

  await waitFor(() => expect(screen.getByText(/token quota exceeded/i)).toBeInTheDocument());
});

test("shows error on 422 (goal too long)", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageFull())       // mount: usage
    .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
    .mockResolvedValueOnce(featuresEnabled()) // mount: features
    .mockResolvedValueOnce({ ok: false, status: 422, json: async () => ({ detail: "Goal too long." }) })
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));

  await waitFor(() => expect(screen.getByText(/goal too long/i)).toBeInTheDocument());
});

test("shows error on network failure", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageFull())       // mount: usage
    .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
    .mockResolvedValueOnce(featuresEnabled()) // mount: features
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
    .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
    .mockResolvedValueOnce(featuresEnabled()) // mount: features
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
    expect(screen.getByText("Install Python")).toHaveStyle({ textDecoration: "none" });
  });

  expect(vi.mocked(fetch)).toHaveBeenCalledWith(
    expect.stringContaining("/tasks/1/complete"),
    expect.objectContaining({ method: "PATCH" })
  );
});

test("reverts task on PATCH failure", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageMock)         // mount: usage
    .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
    .mockResolvedValueOnce(featuresEnabled()) // mount: features
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
  expect(screen.getByText("Install Python")).toHaveStyle({ textDecoration: "none" });
});

// ── New roadmap ───────────────────────────────────────────────────────────────

test("new roadmap resets roadmap and clears input", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce(usageFull())       // mount: usage
    .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
    .mockResolvedValueOnce(featuresEnabled()) // mount: features
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload })
    .mockResolvedValueOnce(usageFull())       // usage refresh
    .mockResolvedValueOnce(emptyRoadmaps())   // roadmaps refresh
    .mockResolvedValueOnce(emptyRoadmaps())   // new roadmap refresh
  );

  render(<RoadmapChat />);
  await userEvent.type(screen.getByRole("textbox"), "Learn Python");
  await userEvent.click(screen.getByRole("button", { name: /generate/i }));
  await waitFor(() => expect(screen.getByText("Python Basics")).toBeInTheDocument());

  await userEvent.click(screen.getByRole("button", { name: /new roadmap/i }));

  expect(screen.queryByText("Python Basics")).not.toBeInTheDocument();
  expect(screen.getByRole("textbox")).toHaveValue("");
});

// ── Saved roadmaps ────────────────────────────────────────────────────────────

describe("Saved roadmaps", () => {
  test("displays saved roadmaps fetched on mount", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload, roadmapPayload2] })
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    // Wait for the list heading — it only appears when saved cards are rendered.
    await waitFor(() => expect(screen.getByText("Your roadmaps")).toBeInTheDocument());
    // Card titles are rendered inside <button> elements, distinguishing them
    // from the "Goal: …" subtitle that appears when a roadmap is open.
    expect(screen.getByRole("button", { name: /My ML Roadmap/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Web Dev Roadmap/ })).toBeInTheDocument();
  });

  test("saved card shows correct progress percentage and task count", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload2] })
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await waitFor(() => expect(screen.getByText("Your roadmaps")).toBeInTheDocument());
    expect(screen.getByText("100%")).toBeInTheDocument();
    expect(screen.getByText(/1\/1 tasks/)).toBeInTheDocument();
  });

  test("clicking a saved roadmap card opens it", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload] })
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await waitFor(() => expect(screen.getByText("Your roadmaps")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: /My ML Roadmap/ }));
    expect(screen.getByText("Python Basics")).toBeInTheDocument();
    expect(screen.getByText("Install Python")).toHaveStyle("text-decoration: none");
  });

  test("'My roadmaps' back button closes the roadmap and refreshes the list", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload] })
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload] }) // back: roadmaps refresh
    );
    render(<RoadmapChat />);
    // Wait for the list view, then open the card.
    await waitFor(() => expect(screen.getByText("Your roadmaps")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: /My ML Roadmap/ }));
    expect(screen.getByText("Python Basics")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /my roadmaps/i }));

    // Detail gone; list restored.
    expect(screen.queryByText("Python Basics")).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Your roadmaps")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: /My ML Roadmap/ })).toBeInTheDocument();
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(4);
  });

  test("New roadmap clears input", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload })
      .mockResolvedValueOnce(usageFull())       // usage refresh
      .mockResolvedValueOnce(emptyRoadmaps())   // roadmaps refresh
    );
    render(<RoadmapChat />);

    await userEvent.type(screen.getByRole("textbox"), "Learn Python");
    await userEvent.click(screen.getByRole("button", { name: /generate/i }));
    await waitFor(() => expect(screen.getByText("Python Basics")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: /\+ new roadmap/i }));

    expect(screen.queryByText("Python Basics")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /generate/i })).toBeInTheDocument();
  });

  test("saved roadmap list refreshes after generating a new roadmap", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload] })
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload2 })
      .mockResolvedValueOnce(usageFull())
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload, roadmapPayload2] })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload, roadmapPayload2] })
    );
    render(<RoadmapChat />);
    await waitFor(() => expect(screen.getByText("Your roadmaps")).toBeInTheDocument());

    await userEvent.type(screen.getByRole("textbox"), "Learn Web Dev");
    await userEvent.click(screen.getByRole("button", { name: /generate/i }));

    await waitFor(() => screen.getByRole("button", { name: /my roadmaps/i }));
    await userEvent.click(screen.getByRole("button", { name: /my roadmaps/i }));

    await waitFor(() => expect(screen.getByRole("button", { name: /Web Dev Roadmap/ })).toBeInTheDocument());
  });

  test("saved roadmaps section is hidden when a roadmap is open", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [roadmapPayload] })
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await waitFor(() => expect(screen.getByText("Your roadmaps")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: /My ML Roadmap/ }));
    expect(screen.queryByText("Your roadmaps")).not.toBeInTheDocument();
  });

  test("saved roadmaps section is absent when fetch returns empty array", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await act(async () => { await new Promise(r => setTimeout(r, 50)); });
    expect(screen.queryByText("Your roadmaps")).not.toBeInTheDocument();
  });
});

// ── Token usage display ───────────────────────────────────────────────────────

describe("Token usage display", () => {
  test("renders token bar with correct remaining value", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ used: 3000, limit: 10000, remaining: 7000 }) })
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await waitFor(() => expect(screen.getByText(/remaining tokens/i)).toBeInTheDocument());
    const valueSpan = document.querySelector<HTMLElement>('[style*="font-variant-numeric"]');
    expect(valueSpan?.textContent?.replace(/\s/g, "")).toMatch(/7[.,]?000/);
  });

  test("token bar is absent when usage fetch returns non-ok", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) })
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await act(async () => { await new Promise(r => setTimeout(r, 50)); });
    expect(screen.queryByText(/remaining tokens/i)).not.toBeInTheDocument();
  });

  test("token bar is absent when usage fetch throws", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockRejectedValueOnce(new Error("Network error"))
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await act(async () => { await new Promise(r => setTimeout(r, 50)); });
    expect(screen.queryByText(/remaining tokens/i)).not.toBeInTheDocument();
  });

  test("token value is green when remaining > 50%", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ used: 1000, limit: 10000, remaining: 9000 }) })
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await waitFor(() => screen.getByText(/remaining tokens/i));
    const span = document.querySelector<HTMLElement>('[style*="font-variant-numeric"]');
    expect(span?.style.color).toBe("rgb(34, 197, 94)");
  });

  test("token value is orange when remaining is 20-50%", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageLow())        // 2000/10000 = 20% → orange boundary
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await waitFor(() => screen.getByText(/remaining tokens/i));
    const span = document.querySelector<HTMLElement>('[style*="font-variant-numeric"]');
    expect(span?.style.color).toBe("rgb(239, 83, 80)");
  });

  test("token value is red when remaining is below 20%", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageCrit())       // mount: usage
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
    );
    render(<RoadmapChat />);
    await waitFor(() => screen.getByText(/remaining tokens/i));
    const span = document.querySelector<HTMLElement>('[style*="font-variant-numeric"]');
    expect(span?.style.color).toBe("rgb(239, 83, 80)");
  });

  // ── Feature flag: tokenQuota ──────────────────────────────────────────────

  test("token bar is hidden when tokenQuota flag is disabled", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())        // mount: usage (data present)
      .mockResolvedValueOnce(emptyRoadmaps())    // mount: roadmaps
      .mockResolvedValueOnce(featuresDisabled()) // mount: features — tokenQuota off
    );
    render(<RoadmapChat />);
    await act(async () => { await new Promise(r => setTimeout(r, 50)); });
    expect(screen.queryByText(/remaining tokens/i)).not.toBeInTheDocument();
  });

  test("token bar is shown when tokenQuota flag is enabled", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features — tokenQuota on
    );
    render(<RoadmapChat />);
    await waitFor(() => expect(screen.getByText(/remaining tokens/i)).toBeInTheDocument());
  });

  test("token bar is shown when features fetch fails (defaults to enabled)", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())     // mount: usage
      .mockResolvedValueOnce(emptyRoadmaps()) // mount: roadmaps
      .mockResolvedValueOnce(featuresFailed()) // mount: features — fails
    );
    render(<RoadmapChat />);
    await waitFor(() => expect(screen.getByText(/remaining tokens/i)).toBeInTheDocument());
  });
});

// ── Character counter ─────────────────────────────────────────────────────────

describe("Character counter", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())
      .mockResolvedValueOnce(emptyRoadmaps())
      .mockResolvedValueOnce(featuresEnabled())
    );
  });

  test("updates the character counter as the user types", async () => {
    render(<RoadmapChat />);
    const user = userEvent.setup();
    const input = screen.getByPlaceholderText(/i want to learn machine learning/i);

    expect(screen.getByTestId("character-counter")).toHaveTextContent("0 / 200 characters");
    await user.type(input, "hello");
    expect(screen.getByTestId("character-counter")).toHaveTextContent("5 / 200 characters");
  });

  test("'Maximum length reached' message appears at 200 chars", async () => {
    render(<RoadmapChat />);
    await userEvent.type(screen.getByRole("textbox"), "a".repeat(200));
    expect(screen.getByText(/maximum length reached/i)).toBeInTheDocument();
  });

  test("counter span becomes bold at ≥ 160 characters (80% threshold)", async () => {
    render(<RoadmapChat />);
    await userEvent.type(screen.getByRole("textbox"), "a".repeat(160));
    expect(screen.getByTestId("character-counter")).toHaveStyle({ fontWeight: "600" });
  });
});

// ── Error auto-clear ──────────────────────────────────────────────────────────

describe("Error auto-clear", () => {
  test("task PATCH failure error disappears after 3 seconds", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });

    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => roadmapPayload })
      .mockResolvedValueOnce(usageFull())       // usage refresh
      .mockResolvedValueOnce(emptyRoadmaps())   // roadmaps refresh
      .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) }) // PATCH fails
    );

    render(<RoadmapChat />);

    await act(async () => {
      await userEvent.type(screen.getByRole("textbox"), "Learn Python");
      await userEvent.click(screen.getByRole("button", { name: /generate/i }));
    });

    await waitFor(() => screen.getByText("Install Python"));

    await act(async () => {
      await userEvent.click(screen.getByText("Install Python"));
    });

    await waitFor(() => expect(screen.getByText(/couldn't update task/i)).toBeInTheDocument());

    act(() => { vi.advanceTimersByTime(3000); });

    await waitFor(() => expect(screen.queryByText(/couldn't update task/i)).not.toBeInTheDocument());

    vi.useRealTimers();
  });
});

// ── Empty milestones edge case ────────────────────────────────────────────────

describe("Empty milestones edge case", () => {
  test("roadmap section is not rendered when milestones array is empty", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ roadmap_id: 99, milestones: [] }) })
      .mockResolvedValueOnce(usageFull())       // usage refresh
      .mockResolvedValueOnce(emptyRoadmaps())   // roadmaps refresh
    );

    render(<RoadmapChat />);
    await userEvent.type(screen.getByRole("textbox"), "Empty goal");
    await userEvent.click(screen.getByRole("button", { name: /generate/i }));

    await waitFor(() => expect(screen.queryByText(/generating/i)).not.toBeInTheDocument());
    expect(screen.queryByText("Your Roadmap")).not.toBeInTheDocument();
  });
});

// ── 422 with non-JSON body ────────────────────────────────────────────────────

describe("422 with non-JSON body", () => {
  test("shows default message when 422 body is not valid JSON", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(usageFull())       // mount: usage
      .mockResolvedValueOnce(emptyRoadmaps())   // mount: roadmaps
      .mockResolvedValueOnce(featuresEnabled()) // mount: features
      .mockResolvedValueOnce({
        ok: false,
        status: 422,
        json: async () => { throw new SyntaxError("Unexpected token"); },
      })
    );

    render(<RoadmapChat />);
    await userEvent.type(screen.getByRole("textbox"), "Learn Python");
    await userEvent.click(screen.getByRole("button", { name: /generate/i }));

    await waitFor(() =>
      expect(screen.getByText(/your goal is too long/i)).toBeInTheDocument()
    );
  });
});

// ── Mount fetch failures ──────────────────────────────────────────────────────

describe("Mount fetch failures", () => {
  test("renders cleanly when both usage and roadmaps fetches fail on mount", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Network down")));

    render(<RoadmapChat />);
    await act(async () => { await new Promise(r => setTimeout(r, 100)); });

    expect(screen.getByRole("textbox")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /generate/i })).toBeInTheDocument();
    expect(screen.queryByText("Your roadmaps")).not.toBeInTheDocument();
    expect(screen.queryByText(/remaining tokens/i)).not.toBeInTheDocument();
  });
});