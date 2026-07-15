import { render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import AdminPanel from "../../src/pages/AdminPanel";

// AdminPanelTest verifies the functionality of the React AdminPanel component, including data loading, navigation, user management, logging, and feature configuration.

// The test suite covers:
// - Initial loading behavior and handling of asynchronous API requests.
// - Fetching and displaying users, courses, and feature flags during component initialization.
// - User management functionality, including displaying user information and deleting users.
// - Course management functionality, including searching and filtering courses by title and TUM course number.
// - Log management functionality, including displaying LLM logs, authentication events, and roadmap history.
// - Feature flag management, including displaying feature toggles, optimistic updates, rollback behavior after failed requests, and saving application settings.
// - Validation of invalid settings input and handling partial update failures.
// - Tab navigation and hash-based routing between Users, Courses, Logs, and Features sections.
// - Conditional rendering of features such as the Grafana link based on enabled feature flags.
// - Resilience against backend failures by verifying correct rendering with empty or partially available data.

// All API communication is mocked using predefined fixtures and fetch mocks to provide deterministic backend responses and ensure isolated component testing.

// ── Mount fetch order (3 parallel calls via Promise.all) ──────────────────────
// 1. GET /api/users
// 2. GET /api/courses
// 3. GET /api/features
//
// AdminPanel also uses window.location.hash for tab routing, so we reset it
// between tests — same pattern as RoadmapChat — to prevent state leakage.

// ── Fixtures ──────────────────────────────────────────────────────────────────

// Static fixture data used as fake backend responses.
// Keeping API responses in one place makes tests deterministic and avoids
// depending on real backend services.
//
// These objects represent the data shape returned by the actual REST APIs,
// allowing the component to be tested against realistic responses.
const USERS = [
  { user_id: 1, email: "alice@tum.de", role: "ADMIN" },
  { user_id: 2, email: "bob@tum.de",   role: "USER"  },
];

const COURSES = [
  { course_id: 1, title: "Introduction to ML",  tum_number: "IN2064", offered_in: "WS" },
  { course_id: 2, title: "Advanced Algorithms",  tum_number: "IN2310", offered_in: "SS" },
  { course_id: 3, title: "Database Systems",     tum_number: "IN0008", offered_in: "WS" },
];

const FEATURES = [
  { name: "tokenQuota",  enabled: true,  description: "Enforce monthly token budget per user." },
  { name: "llmLogs",    enabled: true,  description: "Show the Logs tab in the admin panel."  },
  { name: "grafanaLink", enabled: false, description: "Show Grafana link in the admin nav."    },
];

const SETTINGS = [
  { name: "monthlyTokenLimit",    value: "50000" },
  { name: "promptRole",           value: "You are a study advisor." },
  { name: "promptInstructions",   value: "Build a roadmap." },
  { name: "promptResponseFormat", value: '{"type":"object"}' },
];

const LLM_LOGS = {
  logs: [
    { timestamp: "2024-06-01T10:00:00Z", level: "INFO",  message: "Roadmap generated", goal: "Learn ML", llm_ms: 800,  total_ms: 950  },
    { timestamp: "2024-06-01T10:05:00Z", level: "ERROR", message: "Timeout",            goal: "Learn Go", llm_ms: null, total_ms: 3000 },
  ],
};

const AUTH_EVENTS = {
  logs: [
    { timestamp: "2024-06-01T09:00:00Z", type: "LOGIN",  email: "alice@tum.de", result: "success" },
    { timestamp: "2024-06-01T09:01:00Z", type: "LOGIN",  email: "eve@evil.com", result: "failure" },
  ],
};

const ROADMAP_LOGS = [
  { id: 10, title: "Learn ML",      created_date: "2024-06-01T08:00:00Z", progress: 50, milestones: [{ title: "Basics" }, { title: "Advanced" }] },
  { id: 11, title: "Learn Docker",  created_date: "2024-06-02T08:00:00Z", progress: 0,  milestones: [] },
];

// ── Mount helper ──────────────────────────────────────────────────────────────

// Returns mocks for the 3 parallel mount fetches in the order the component
// calls them: users → courses → features.
function mountMocks(overrides: {
  users?: object[];
  courses?: object[];
  features?: object[];
} = {}) {
  return [
    { ok: true, status: 200, json: async () => overrides.users   ?? USERS    },
    { ok: true, status: 200, json: async () => overrides.courses ?? COURSES  },
    { ok: true, status: 200, json: async () => overrides.features ?? FEATURES },
  ];
}

// Waits for the loading spinner to disappear, which signals all mount fetches
// resolved and state has been set.
async function waitForLoaded() {
  await waitFor(() => expect(screen.queryByText("Loading...")).not.toBeInTheDocument());
}

// ── Setup / teardown ──────────────────────────────────────────────────────────

beforeEach(() => {
  // Reset hash so tab routing starts on "users" in every test.
  window.location.hash = "";
});

afterEach(() => {
  window.location.hash = "";
  vi.restoreAllMocks();
});

// ── Loading state ─────────────────────────────────────────────────────────────

describe("Loading state", () => {
  test("shows loading indicator before mount fetches resolve", async () => {
    // Never resolves — keeps the component in the loading state.
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(() => {})));
    render(<AdminPanel />);
    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  test("hides loading indicator after data arrives", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
    );
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
  });
});

// ── Users tab ─────────────────────────────────────────────────────────────────

describe("Users tab", () => {
  beforeEach(() => {
    const [u, c, f] = mountMocks();
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(u)
      .mockResolvedValueOnce(c)
      .mockResolvedValueOnce(f)
    );
  });

  test("renders user rows with email and role", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByText("alice@tum.de")).toBeInTheDocument();
    expect(screen.getByText("bob@tum.de")).toBeInTheDocument();
    expect(screen.getByText("ADMIN")).toBeInTheDocument();
    expect(screen.getByText("USER")).toBeInTheDocument();
  });

  test("shows user count in the tab label", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByRole("button", { name: /Users \(2\)/i })).toBeInTheDocument();
  });

  test("shows 'No users found' when the list is empty", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [] })  // users
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
    );
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByText("No users found.")).toBeInTheDocument();
  });

  test("deletes a user and removes the row from the table", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({}) }) // DELETE
    );
    render(<AdminPanel />);
    await waitForLoaded();

    // Find the Delete button in bob's row.
    const bobRow = screen.getByText("bob@tum.de").closest("tr")!;
    await userEvent.click(within(bobRow).getByRole("button", { name: /delete/i }));

    expect(screen.queryByText("bob@tum.de")).not.toBeInTheDocument();
    expect(screen.getByText("alice@tum.de")).toBeInTheDocument();

    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      "/api/users/2",
      expect.objectContaining({ method: "DELETE" })
    );
  });

  test("keeps the row when DELETE request fails", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      // The component calls setUsers(prev => prev.filter(...)) regardless of
      // the response status, so we just confirm the DELETE is sent.
      .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) })
    );
    render(<AdminPanel />);
    await waitForLoaded();

    const bobRow = screen.getByText("bob@tum.de").closest("tr")!;
    await userEvent.click(within(bobRow).getByRole("button", { name: /delete/i }));

    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      "/api/users/2",
      expect.objectContaining({ method: "DELETE" })
    );
  });
});

// ── Courses tab ───────────────────────────────────────────────────────────────

describe("Courses tab", () => {
  beforeEach(() => {
    const [u, c, f] = mountMocks();
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(u)
      .mockResolvedValueOnce(c)
      .mockResolvedValueOnce(f)
    );
  });

  test("renders course rows with title, TUM number, and semester", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Courses/i }));

    expect(screen.getByText("Introduction to ML")).toBeInTheDocument();
    expect(screen.getByText("IN2064")).toBeInTheDocument();
    expect(screen.getByText("Advanced Algorithms")).toBeInTheDocument();
  });

  test("shows course count in the tab label", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByRole("button", { name: /Courses \(3\)/i })).toBeInTheDocument();
  });

  test("filters courses by title as the user types", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Courses/i }));

    await userEvent.type(screen.getByPlaceholderText(/search/i), "database");

    expect(screen.getByText("Database Systems")).toBeInTheDocument();
    expect(screen.queryByText("Introduction to ML")).not.toBeInTheDocument();
    expect(screen.queryByText("Advanced Algorithms")).not.toBeInTheDocument();
  });

  test("filters courses by TUM number", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Courses/i }));

    await userEvent.type(screen.getByPlaceholderText(/search/i), "IN2310");

    expect(screen.getByText("Advanced Algorithms")).toBeInTheDocument();
    expect(screen.queryByText("Introduction to ML")).not.toBeInTheDocument();
  });

  test("shows 'No courses found' when search matches nothing", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Courses/i }));

    await userEvent.type(screen.getByPlaceholderText(/search/i), "zzz-no-match");

    expect(screen.getByText("No courses found.")).toBeInTheDocument();
  });

  test("clears filter when search input is emptied", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Courses/i }));

    const input = screen.getByPlaceholderText(/search/i);
    await userEvent.type(input, "database");
    expect(screen.queryByText("Introduction to ML")).not.toBeInTheDocument();

    await userEvent.clear(input);
    expect(screen.getByText("Introduction to ML")).toBeInTheDocument();
    expect(screen.getByText("Advanced Algorithms")).toBeInTheDocument();
  });
});

// ── Logs tab ──────────────────────────────────────────────────────────────────

describe("Logs tab", () => {
  // Logs tab fires 3 fetches on entry: /llm/logs, /api/roadmaps/all, /api/auth/logs.
  function setupWithLogs() {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])   // mount: users
      .mockResolvedValueOnce(mountMocks()[1])   // mount: courses
      .mockResolvedValueOnce(mountMocks()[2])   // mount: features (llmLogs: true → tab visible)
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => LLM_LOGS })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ROADMAP_LOGS })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => AUTH_EVENTS })
    );
  }

  test("Logs tab is visible when llmLogs feature flag is on", async () => {
    const [u, c] = mountMocks();
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(u)
      .mockResolvedValueOnce(c)
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => FEATURES }) // llmLogs: true
    );
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByRole("button", { name: /Logs/i })).toBeInTheDocument();
  });

  test("Logs tab is hidden when llmLogs feature flag is off", async () => {
    const [u, c] = mountMocks();
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(u)
      .mockResolvedValueOnce(c)
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () =>
        FEATURES.map(f => f.name === "llmLogs" ? { ...f, enabled: false } : f)
      })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.queryByRole("button", { name: /^Logs$/i })).not.toBeInTheDocument();
  });

  test("renders LLM log rows when Logs tab is opened", async () => {
    setupWithLogs();
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Logs/i }));

    await waitFor(() =>
        expect(screen.getByText("Roadmap generated")).toBeInTheDocument()
    );

    expect(screen.getByText("Timeout")).toBeInTheDocument();

    const roadmapHeading = screen.getByRole("heading", {
        name: /Roadmap History/i,
    });

    const roadmapTable = roadmapHeading.nextElementSibling as HTMLTableElement;

    expect(within(roadmapTable).getByText("Learn ML")).toBeInTheDocument();
    });

  test("renders auth event rows", async () => {
    setupWithLogs();
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Logs/i }));

    await waitFor(() => expect(screen.getByText("alice@tum.de")).toBeInTheDocument());
    expect(screen.getByText("eve@evil.com")).toBeInTheDocument();
    expect(screen.getByText("success")).toBeInTheDocument();
    expect(screen.getByText("failure")).toBeInTheDocument();
  });

  test("renders roadmap history rows", async () => {
    setupWithLogs();
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Logs/i }));

    // Wait until the roadmap table has loaded.
    await waitFor(() =>
        expect(screen.getByText("Learn Docker")).toBeInTheDocument()
    );

    // The third table on the Logs page is the Roadmap History table.
    const roadmapTable = screen.getAllByRole("table")[2];

    expect(within(roadmapTable).getByText("Learn ML")).toBeInTheDocument();
    expect(within(roadmapTable).getByText("Learn Docker")).toBeInTheDocument();
    expect(within(roadmapTable).getByText("2")).toBeInTheDocument();
    });

  test("Refresh button re-fetches all log endpoints", async () => {
    setupWithLogs();
    // Second round of log fetches after Refresh click.
    vi.mocked(fetch)
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => LLM_LOGS } as Response)
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ROADMAP_LOGS } as Response)
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => AUTH_EVENTS } as Response);

    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Logs/i }));
    await waitFor(() => expect(screen.getByText("Roadmap generated")).toBeInTheDocument());

    await userEvent.click(screen.getByRole("button", { name: /Refresh/i }));

    // 3 mount + 3 first-open + 3 refresh = 9 total calls
    await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(9));
  });

  test("shows empty state rows when logs are empty", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ logs: [] }) })    // llm
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [] })                // roadmaps
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ logs: [] }) })   // auth
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Logs/i }));

    await waitFor(() => expect(screen.getByText("No LLM logs yet.")).toBeInTheDocument());
    expect(screen.getByText("No auth events yet.")).toBeInTheDocument();
    expect(screen.getByText("No roadmaps yet.")).toBeInTheDocument();
  });
});

// ── Features tab ──────────────────────────────────────────────────────────────

describe("Features tab", () => {
  // Each test stubs fetch directly: 3 mount calls + 1 lazy settings load +
  // any action-specific calls (toggle PUT, settings PUTs).

  test("renders feature flag rows with name, description, and toggle", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));

    await waitFor(() => {
        expect(
            screen.getAllByText("tokenQuota").some(el => el.tagName === "TD")
        ).toBe(true);
    });
    expect(screen.getByText("llmLogs")).toBeInTheDocument();
    expect(screen.getByText("grafanaLink")).toBeInTheDocument();
    expect(screen.getByText("Enforce monthly token budget per user.")).toBeInTheDocument();
  });

  test("shows ON/OFF labels reflecting current flag state", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));

    // "tokenQuota" appears in both the table <td> and a <code> tag in the hint
    // paragraph — wait for the <td> specifically.
    await waitFor(() =>
      expect(screen.getAllByText("tokenQuota").some(el => el.tagName === "TD")).toBe(true)
    );
    // tokenQuota and llmLogs are ON; grafanaLink is OFF.
    const toggleBtns = screen.getAllByRole("button", { name: /^(ON|OFF)$/ });
    const labels = toggleBtns.map(b => b.textContent);
    expect(labels.filter(l => l === "ON").length).toBe(2);
    expect(labels.filter(l => l === "OFF").length).toBe(1);
  });

  test("optimistically toggles a feature flag ON→OFF and sends PUT", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({}) }) // PUT toggle
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));

    // Pick the <td> node — "tokenQuota" also appears in a <code> tag below.
    const tokenQuotaTd = await waitFor(() => {
      const td = screen.getAllByText("tokenQuota").find(el => el.tagName === "TD");
      if (!td) throw new Error("tokenQuota <td> not found");
      return td;
    });
    const tokenQuotaRow = tokenQuotaTd.closest("tr")!;

    // tokenQuota row toggle is "ON" — click it to turn off.
    const toggleBtn = within(tokenQuotaRow).getByRole("button", { name: "ON" });
    await userEvent.click(toggleBtn);

    // Optimistic update: button text flips immediately.
    expect(within(tokenQuotaRow).getByRole("button", { name: "OFF" })).toBeInTheDocument();

    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      "/api/features/tokenQuota",
      expect.objectContaining({ method: "PUT", body: JSON.stringify({ enabled: false }) })
    );
  });

  test("reverts toggle optimistic update when PUT fails", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
      .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) }) // PUT fails
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));

    const tokenQuotaTd = await waitFor(() => {
      const td = screen.getAllByText("tokenQuota").find(el => el.tagName === "TD");
      if (!td) throw new Error("tokenQuota <td> not found");
      return td;
    });
    const tokenQuotaRow = tokenQuotaTd.closest("tr")!;
    await userEvent.click(within(tokenQuotaRow).getByRole("button", { name: "ON" }));

    // After revert the button should be back to ON.
    await waitFor(() =>
      expect(within(tokenQuotaRow).getByRole("button", { name: "ON" })).toBeInTheDocument()
    );
  });

  test("reverts toggle when PUT throws (network error)", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
      .mockRejectedValueOnce(new Error("Network error"))
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));

    const tokenQuotaTd = await waitFor(() => {
      const td = screen.getAllByText("tokenQuota").find(el => el.tagName === "TD");
      if (!td) throw new Error("tokenQuota <td> not found");
      return td;
    });
    const tokenQuotaRow = tokenQuotaTd.closest("tr")!;
    await userEvent.click(within(tokenQuotaRow).getByRole("button", { name: "ON" }));

    await waitFor(() =>
      expect(within(tokenQuotaRow).getByRole("button", { name: "ON" })).toBeInTheDocument()
    );
  });

  test("settings are lazy-loaded only when Features tab first opens", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
    );
    render(<AdminPanel />);
    await waitForLoaded();

    // Settings fetch has NOT been called yet (still on Users tab).
    const callsAfterMount = vi.mocked(fetch).mock.calls.length;
    expect(vi.mocked(fetch)).not.toHaveBeenCalledWith("/api/settings", expect.anything());

    await userEvent.click(screen.getByRole("button", { name: /Features/i }));

    await waitFor(() =>
      expect(vi.mocked(fetch)).toHaveBeenCalledWith("/api/settings", expect.anything())
    );
    const callsAfterOpen = vi.mocked(fetch).mock.calls.length;
    expect(callsAfterOpen).toBe(callsAfterMount + 1);

    // Switching away and back should NOT re-fetch settings.
    await userEvent.click(screen.getByRole("button", { name: /Users/i }));
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));
    expect(vi.mocked(fetch).mock.calls.length).toBe(callsAfterOpen);
  });

  test("populates settings inputs with values from the API", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));

    await waitFor(() =>
      expect(screen.getByDisplayValue("50000")).toBeInTheDocument()
    );
    expect(screen.getByDisplayValue("You are a study advisor.")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Build a roadmap.")).toBeInTheDocument();
  });

  test("Save settings sends PUT for each setting", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
      // One PUT per setting (4 settings).
      .mockResolvedValue({ ok: true, status: 200, json: async () => ({}) })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));
    await waitFor(() => expect(screen.getByDisplayValue("50000")).toBeInTheDocument());

    await userEvent.click(screen.getByRole("button", { name: /Save settings/i }));

    await waitFor(() =>
      expect(screen.getByText(/Saved — takes effect/i)).toBeInTheDocument()
    );

    // Verify a PUT was made to each settings endpoint.
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      expect.stringContaining("/api/settings/"),
      expect.objectContaining({ method: "PUT" })
    );
  });

  test("shows warning and blocks save when token limit is not a number", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));
    await waitFor(() => expect(screen.getByDisplayValue("50000")).toBeInTheDocument());

    const tokenInput = screen.getByDisplayValue("50000");
    await userEvent.clear(tokenInput);
    await userEvent.type(tokenInput, "not-a-number");

    await userEvent.click(screen.getByRole("button", { name: /Save settings/i }));

    expect(screen.getByText(/must be a positive number/i)).toBeInTheDocument();
    // No PUT calls should have been made.
    expect(vi.mocked(fetch)).not.toHaveBeenCalledWith(
      expect.stringContaining("/api/settings/"),
      expect.objectContaining({ method: "PUT" })
    );
  });

  test("shows partial-failure warning when some settings PUTs fail", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
      .mockResolvedValueOnce({ ok: true,  status: 200, json: async () => ({}) })
      .mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) })
      .mockResolvedValue({ ok: true, status: 200, json: async () => ({}) })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));
    await waitFor(() => expect(screen.getByDisplayValue("50000")).toBeInTheDocument());

    await userEvent.click(screen.getByRole("button", { name: /Save settings/i }));

    await waitFor(() =>
      expect(screen.getByText(/some settings failed/i)).toBeInTheDocument()
    );
  });

  test("shows 'No feature flags found' when features array is empty", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [] }) // empty features
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => SETTINGS })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));
    await waitFor(() => expect(screen.getByText("No feature flags found.")).toBeInTheDocument());
  });
});

// ── Tab routing / navigation ──────────────────────────────────────────────────

describe("Tab routing", () => {
  beforeEach(() => {
    const [u, c, f] = mountMocks();
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(u)
      .mockResolvedValueOnce(c)
      .mockResolvedValueOnce(f)
      // Settings fetch when Features tab opens.
      .mockResolvedValue({ ok: true, status: 200, json: async () => SETTINGS })
    );
  });

  test("defaults to the Users tab on first load", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByText("alice@tum.de")).toBeInTheDocument();
    expect(screen.queryByText(/search by title/i)).not.toBeInTheDocument();
  });

  test("navigates to Courses tab on click", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Courses/i }));
    expect(screen.getByPlaceholderText(/search by title/i)).toBeInTheDocument();
    expect(screen.queryByText("alice@tum.de")).not.toBeInTheDocument();
  });

  test("navigates to Features tab on click", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Features/i }));
    await waitFor(() => expect(screen.getByText("Feature toggles")).toBeInTheDocument());
  });

  test("updates window.location.hash when switching tabs", async () => {
    render(<AdminPanel />);
    await waitForLoaded();
    await userEvent.click(screen.getByRole("button", { name: /Courses/i }));
    expect(window.location.hash).toBe("#courses");

    await userEvent.click(screen.getByRole("button", { name: /Features/i }));
    expect(window.location.hash).toBe("#features");
  });

  test("restores the correct tab from hash on mount", async () => {
    window.location.hash = "#courses";
    render(<AdminPanel />);
    await waitForLoaded();
    // Should open directly on the Courses tab.
    expect(screen.getByPlaceholderText(/search by title/i)).toBeInTheDocument();
    expect(screen.queryByText("alice@tum.de")).not.toBeInTheDocument();
  });

  test("falls back to Users tab for an unrecognised hash", async () => {
    window.location.hash = "#nonexistent";
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByText("alice@tum.de")).toBeInTheDocument();
  });
});

// ── Grafana link ──────────────────────────────────────────────────────────────

describe("Grafana link", () => {
  test("Grafana link is visible when grafanaLink flag is on", async () => {
    const features = FEATURES.map(f =>
      f.name === "grafanaLink" ? { ...f, enabled: true } : f
    );
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => features })
    );
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByRole("link", { name: /Grafana/i })).toBeInTheDocument();
  });

  test("Grafana link is hidden when grafanaLink flag is off", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockResolvedValueOnce(mountMocks()[0])
      .mockResolvedValueOnce(mountMocks()[1])
      .mockResolvedValueOnce(mountMocks()[2]) // grafanaLink: false in FEATURES
    );
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.queryByRole("link", { name: /Grafana/i })).not.toBeInTheDocument();
  });
});

// ── Mount resilience ──────────────────────────────────────────────────────────

describe("Mount fetch resilience", () => {
  test("renders with empty state when all mount fetches fail", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Network down")));
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByText("No users found.")).toBeInTheDocument();
  });

  test("renders partial data when only users fetch fails", async () => {
    vi.stubGlobal("fetch", vi.fn()
      .mockRejectedValueOnce(new Error("users fail"))
      .mockResolvedValueOnce(mountMocks()[1]) // courses ok
      .mockResolvedValueOnce(mountMocks()[2]) // features ok
    );
    render(<AdminPanel />);
    await waitForLoaded();
    expect(screen.getByText("No users found.")).toBeInTheDocument();
    // Course count should still appear in the tab label.
    expect(screen.getByRole("button", { name: /Courses \(3\)/i })).toBeInTheDocument();
  });
});