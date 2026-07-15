import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, test, vi } from "vitest";
import Login from "../../src/pages/Login";
import { useAuth } from "../../src/context/AuthContext";

// LoginPageTest verifies the functionality and user interactions of the React Login page component. The tests mock the AuthContext to isolate the component from the real authentication service and simulate different authentication scenarios.

// The test suite covers:
// - Correct rendering of the login form, including input fields, buttons, and navigation links.
// - Initial form validation by ensuring the login button is disabled when required fields are empty.
// - Enabling the login button after valid email and password input are provided.
// - Successful login behavior, including calling the authentication function with the correct credentials and redirecting the user after authentication.
// - Error handling when authentication fails with an Error object and displaying the returned error message.
// - Handling unexpected login failures where a non-Error value is thrown and displaying a generic error message.
// - Displaying the submitting state while the login request is still pending and disabling the button to prevent duplicate submissions.
// - Correct navigation to the signup page through the signup link.

// The authentication logic is replaced with a controlled mock using Vitest, allowing the component behavior to be tested independently from backend services and ensuring deterministic test execution.


/**
 * Mock the authentication context used by the Login component.
 *
 * The real AuthContext communicates with the backend and manages global
 * authentication state. For this component test, we replace it with a
 * controlled mock so tests can simulate different authentication states
 * without depending on a running backend or existing user session.
 */
vi.mock("../../src/context/AuthContext", () => ({
  useAuth: vi.fn(),
}));

// Typed reference to the mocked hook.
// This allows configuring different authentication states in individual tests.
const mockedUseAuth = vi.mocked(useAuth);

describe("Login page", () => {
  // Shared mock function used to verify that the Login component
  // calls the authentication service with the expected credentials.
  const loginMock = vi.fn();

  beforeEach(() => {
    // Reset previous calls and mock states before each test.
    // This prevents one test's interactions from affecting another test.
    vi.clearAllMocks();

    // Provide a default unauthenticated user context.
    //
    // The Login page expects AuthContext to exist, but the test does not need
    // the real provider. Instead, it receives a predictable mock context:
    // - no logged-in user
    // - authentication is already initialized
    // - login function can be observed during the test
    mockedUseAuth.mockReturnValue({
      user: null,
      loading: false,
      login: loginMock,
      signup: vi.fn(),
      logout: vi.fn(),
    });
  });

  /**
   * Renders the Login component inside a lightweight in-memory router.
   *
   * MemoryRouter provides the routing context required by React Router without
   * interacting with the real browser URL.
   */
  function renderLogin() {
    return render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    );
  }

  test("renders login form correctly", () => {
    renderLogin();

    expect(screen.getByText("TUMgoal")).toBeInTheDocument();
    expect(screen.getByText("Sign in to your account")).toBeInTheDocument();

    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: "Sign in" })
    ).toBeInTheDocument();

    expect(screen.getByText(/Don't have an account/i)).toBeInTheDocument();
  });


  test("submit button is disabled when fields are empty", () => {
    renderLogin();

    expect(
      screen.getByRole("button", { name: "Sign in" })
    ).toBeDisabled();
  });


  test("enables submit button after entering email and password", async () => {
    const user = userEvent.setup();

    renderLogin();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    expect(
      screen.getByRole("button", { name: "Sign in" })
    ).toBeEnabled();
  });


  test("calls login and redirects on successful login", async () => {
    const user = userEvent.setup();

    loginMock.mockResolvedValueOnce(undefined);

    renderLogin();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign in" })
    );

    await waitFor(() => {
      expect(loginMock).toHaveBeenCalledWith(
        "test@example.com",
        "password123"
      );
    });

    // Verify navigation happened
    await waitFor(() => {
      expect(window.location.pathname).toBe("/");
    });
  });


  test("shows error message when login fails", async () => {
    const user = userEvent.setup();

    loginMock.mockRejectedValueOnce(
      new Error("Invalid credentials")
    );

    renderLogin();

    await user.type(
      screen.getByLabelText("Email"),
      "wrong@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "wrong-password"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign in" })
    );

    await waitFor(() => {
      expect(
        screen.getByText(/Invalid credentials/i)
      ).toBeInTheDocument();
    });
  });


  test("shows generic error when thrown value is not Error", async () => {
    const user = userEvent.setup();

    loginMock.mockRejectedValueOnce("failed");

    renderLogin();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign in" })
    );

    await waitFor(() => {
      expect(
        screen.getByText(/Login failed/i)
      ).toBeInTheDocument();
    });
  });


  test("shows submitting state while login is pending", async () => {
    const user = userEvent.setup();

    let resolveLogin!: () => void;

    loginMock.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveLogin = resolve;
        })
    );

    renderLogin();

    await user.type(
      screen.getByLabelText("Email"),
      "test@example.com"
    );

    await user.type(
      screen.getByLabelText("Password"),
      "password123"
    );

    await user.click(
      screen.getByRole("button", { name: "Sign in" })
    );

    expect(
      screen.getByRole("button", { name: /Signing in/i })
    ).toBeDisabled();

    resolveLogin();

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "Sign in" })
      ).toBeEnabled();
    });
  });


  test("signup link points to signup page", () => {
    renderLogin();

    const link = screen.getByRole("link", {
      name: /Sign up/i,
    });

    expect(link).toHaveAttribute(
      "href",
      "/signup"
    );
  });
});