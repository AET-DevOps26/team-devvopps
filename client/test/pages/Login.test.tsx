import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";
import Login from "../../src/pages/Login";
import { useAuth } from "../../src/context/AuthContext";

vi.mock("../../src/context/AuthContext", () => ({
  useAuth: vi.fn(),
}));

const mockedUseAuth = vi.mocked(useAuth);

describe("Login page", () => {
  const loginMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    mockedUseAuth.mockReturnValue({
      user: null,
      loading: false,
      login: loginMock,
      signup: vi.fn(),
      logout: vi.fn(),
    });
  });

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