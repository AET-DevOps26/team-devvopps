import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, test, vi } from "vitest";
import Signup from "../../src/pages/Signup";
import { useAuth } from "../../src/context/AuthContext";

// Tests for the Signup page component.
//
// AuthContext is mocked to verify:
// - form rendering
// - input validation
// - password matching rules
// - successful signup
// - signup errors
// - loading state
// - login navigation


/**
 * Mock the authentication context used by the Signup component.
 *
 * The real AuthContext communicates with the backend and manages global
 * authentication state. For this component test, we replace it with a
 * controlled mock so tests can simulate different authentication states
 * without depending on a running backend or existing user session.
 */
vi.mock("../../src/context/AuthContext", () => ({
  useAuth: vi.fn(),
}));

/**
 * Creates a mocked version of the useAuth hook.
 *
 * This allows the test to configure the returned authentication state and
 * verify how the Signup component interacts with authentication functions.
 */
const mockedUseAuth = vi.mocked(useAuth);

describe("Signup page", () => {
  // Mock function used to verify that signup is called with the expected data.
  const signupMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    /**
     * Resets all mock call history before each test and provides a predictable
     * authentication context.
     *
     * The component receives a fake authenticated state instead of the real
     * AuthProvider. This keeps every test independent and ensures that only the
     * Signup component behavior is evaluated.
     */
    mockedUseAuth.mockReturnValue({
      user: null,
      loading: false,
      login: vi.fn(),
      signup: signupMock,
      logout: vi.fn(),
    });
  });

  /**
   * Renders the Signup component inside a lightweight in-memory router.
   *
   * MemoryRouter provides the routing context required by React Router without
   * interacting with the real browser URL.
   */
  function renderSignup() {
    return render(
      <MemoryRouter>
        <Signup />
      </MemoryRouter>
    );
  }


  test("renders signup form correctly", () => {
    renderSignup();

    expect(screen.getByText("TUMgoal")).toBeInTheDocument();
    expect(
      screen.getByText("Create your account")
    ).toBeInTheDocument();

    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();
    expect(
      screen.getByLabelText("Confirm password")
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: "Sign up" })
    ).toBeInTheDocument();
  });


  test("submit button is disabled when fields are empty", () => {
    renderSignup();

    expect(
      screen.getByRole("button", { name: "Sign up" })
    ).toBeDisabled();
  });


  test("enables submit button after entering email and password", async () => {
    const user = userEvent.setup();

    renderSignup();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    expect(
      screen.getByRole("button", { name: "Sign up" })
    ).toBeEnabled();
  });


  test("shows error when password is shorter than minimum length", async () => {
    const user = userEvent.setup();

    renderSignup();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "short"
    );

    await user.type(
      screen.getByLabelText("Confirm password"),
      "short"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign up" })
    );

    expect(
      screen.getByText(
        /Password must be at least 8 characters./
      )
    ).toBeInTheDocument();

    expect(signupMock).not.toHaveBeenCalled();
  });


  test("shows error when passwords do not match", async () => {
    const user = userEvent.setup();

    renderSignup();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    await user.type(
      screen.getByLabelText("Confirm password"),
      "different123"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign up" })
    );

    expect(
      screen.getByText(/Passwords do not match./)
    ).toBeInTheDocument();

    expect(signupMock).not.toHaveBeenCalled();
  });


  test("calls signup with email and password on valid submission", async () => {
    const user = userEvent.setup();

    signupMock.mockResolvedValueOnce(undefined);

    renderSignup();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    await user.type(
      screen.getByLabelText("Confirm password"),
      "password123"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign up" })
    );

    await waitFor(() => {
      expect(signupMock).toHaveBeenCalledWith(
        "test@example.com",
        "password123"
      );
    });
  });


  test("shows error when signup fails", async () => {
    const user = userEvent.setup();

    signupMock.mockRejectedValueOnce(
      new Error("Email already exists")
    );

    renderSignup();

    await user.type(
      screen.getByLabelText("Email"),
      "taken@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    await user.type(
      screen.getByLabelText("Confirm password"),
      "password123"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign up" })
    );

    await waitFor(() => {
      expect(
        screen.getByText(/Email already exists/)
      ).toBeInTheDocument();
    });
  });


  test("shows generic error when signup throws non-Error value", async () => {
    const user = userEvent.setup();

    signupMock.mockRejectedValueOnce("failed");

    renderSignup();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    await user.type(
      screen.getByLabelText("Confirm password"),
      "password123"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign up" })
    );

    await waitFor(() => {
      expect(
        screen.getByText(/Sign up failed/)
      ).toBeInTheDocument();
    });
  });


  test("shows loading state while signup is pending", async () => {
    const user = userEvent.setup();

    let resolveSignup!: () => void;

    signupMock.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveSignup = resolve;
        })
    );

    renderSignup();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    await user.type(
      screen.getByLabelText("Confirm password"),
      "password123"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign up" })
    );

    expect(
      screen.getByRole("button", {
        name: /Creating account/i,
      })
    ).toBeDisabled();

    resolveSignup();

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "Sign up" })
      ).toBeEnabled();
    });
  });


  test("login link points to login page", () => {
    renderSignup();

    const link = screen.getByRole("link", {
      name: /Sign in/i,
    });

    expect(link).toHaveAttribute(
      "href",
      "/login"
    );
  });
});