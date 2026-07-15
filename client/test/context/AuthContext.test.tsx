import { render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { AuthProvider, useAuth } from "../../src/context/AuthContext";

/**
 * Tests for the AuthContext provider and useAuth hook.
 *
 * These tests verify the authentication state management logic without
 * depending on a real backend. The browser fetch API is mocked to simulate
 * different authentication scenarios.
 *
 * The tests cover:
 * - Loading the current authenticated user when the provider mounts
 * - Handling unauthenticated users when /api/auth/me fails
 * - Handling network failures during the initial authentication check
 * - Updating authentication state after successful login
 * - Propagating backend error messages during failed login attempts
 * - Updating authentication state after successful signup
 * - Clearing the user state after logout
 * - Ensuring useAuth cannot be used outside an AuthProvider
 *
 * A small TestConsumer component is used because React hooks cannot be called
 * directly outside a component. It exposes AuthContext values and actions so
 * the tests can interact with the provider as a real component would.
 *
 * The tests use mocked fetch responses instead of real HTTP requests, which
 * keeps the tests isolated from the backend while still verifying that the
 * AuthProvider sends the correct requests and updates React state correctly.
 */


const mockUser = {
  userId: 1,
  email: "test@example.com",
  role: "USER",
};

// A test-only component used to access the AuthContext.
// Since hooks can only be called inside React components, this component
// exposes the context values and actions so the tests can interact with them.
function TestConsumer() {
  const {
    user,
    loading,
    login,
    signup,
    logout,
  } = useAuth();

  return (
    <div>
      <div data-testid="loading">
        {loading ? "loading" : "loaded"}
      </div>

      <div data-testid="user">
        {user ? `${user.email}-${user.role}` : "no-user"}
      </div>

      <button
        onClick={() => login("test@example.com", "password")}
      >
        login
      </button>

      <button
        onClick={() => signup("new@example.com", "password")}
      >
        signup
      </button>

      <button
        onClick={() => logout()}
      >
        logout
      </button>
    </div>
  );
}

// Helper that renders a component tree containing the real AuthProvider.
// This allows tests to verify the complete context behavior:
// - initial authentication state
// - login/signup/logout actions
// - state updates after API responses
//
// TestConsumer acts as a bridge between the provider and the test assertions.
function renderAuth() {
  return render(
    <AuthProvider>
      <TestConsumer />
    </AuthProvider>
  );
}


describe("AuthContext", () => {

  beforeEach(() => {
    // Replace the browser fetch implementation with a mock.
    // AuthProvider communicates with the backend through fetch,
    // so mocking it keeps the test isolated from real HTTP requests.
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    // Restore the original environment after every test.
    // This prevents mocked network calls from leaking into other tests.
    vi.restoreAllMocks();
  });


  test("loads current user on mount", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify(mockUser),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json",
          },
        }
      )
    );


    renderAuth();


    expect(
      screen.getByTestId("loading")
    ).toHaveTextContent("loading");


    await waitFor(() => {
      expect(
        screen.getByTestId("loading")
      ).toHaveTextContent("loaded");
    });


    expect(
      screen.getByTestId("user")
    ).toHaveTextContent(
      "test@example.com-USER"
    );


    expect(fetch).toHaveBeenCalledWith(
      "/api/auth/me",
      {
        credentials: "include",
      }
    );
  });



  test("sets user to null when /auth/me fails", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(null, {
        status: 401,
      })
    );


    renderAuth();


    await waitFor(() => {
      expect(
        screen.getByTestId("loading")
      ).toHaveTextContent("loaded");
    });


    expect(
      screen.getByTestId("user")
    ).toHaveTextContent("no-user");
  });



  test("handles network failure during initial auth check", async () => {
    vi.mocked(fetch).mockRejectedValueOnce(
      new Error("network error")
    );


    renderAuth();


    await waitFor(() => {
      expect(
        screen.getByTestId("loading")
      ).toHaveTextContent("loaded");
    });


    expect(
      screen.getByTestId("user")
    ).toHaveTextContent("no-user");
  });



  test("successful login updates user", async () => {

    vi.mocked(fetch)
      // /auth/me
      .mockResolvedValueOnce(
        new Response(null, {
          status: 401,
        })
      )
      // /auth/login
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify(mockUser),
          {
            status: 200,
            headers: {
              "Content-Type": "application/json",
            },
          }
        )
      );


    renderAuth();


    await userEvent.click(
      screen.getByRole("button", {
        name: "login",
      })
    );


    await waitFor(() => {
      expect(
        screen.getByTestId("user")
      ).toHaveTextContent(
        "test@example.com-USER"
      );
    });


    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "/api/auth/login",
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      })
    );

  });



  test("login throws backend error message", async () => {
    const wrapper = renderHook(() => useAuth(), {
      wrapper: AuthProvider,
    });

    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({
          message: "Wrong password",
        }),
      })
    );

    await expect(
      wrapper.result.current.login(
        "test@test.com",
        "wrong"
      )
    ).rejects.toThrow("Wrong password");
  });



  test("successful signup updates user", async () => {

    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(null, {
          status: 401,
        })
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify(mockUser),
          {
            status: 200,
          }
        )
      );


    renderAuth();


    await userEvent.click(
      screen.getByRole("button", {
        name: "signup",
      })
    );


    await waitFor(() => {
      expect(
        screen.getByTestId("user")
      ).toHaveTextContent(
        "test@example.com-USER"
      );
    });

  });



  test("logout clears user", async () => {

    vi.mocked(fetch)
      // initial /me
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify(mockUser),
          {
            status: 200,
          }
        )
      )
      // logout
      .mockResolvedValueOnce(
        new Response(null, {
          status: 200,
        })
      );


    renderAuth();


    await waitFor(() => {
      expect(
        screen.getByTestId("user")
      ).toHaveTextContent(
        "test@example.com-USER"
      );
    });


    await userEvent.click(
      screen.getByRole("button", {
        name: "logout",
      })
    );


    await waitFor(() => {
      expect(
        screen.getByTestId("user")
      ).toHaveTextContent(
        "no-user"
      );
    });


    expect(fetch).toHaveBeenLastCalledWith(
      "/api/auth/logout",
      {
        method: "POST",
        credentials: "include",
      }
    );

  });



  test("useAuth throws when used outside provider", () => {

    function BrokenComponent() {
      useAuth();
      return null;
    }


    expect(() =>
      render(<BrokenComponent />)
    ).toThrow(
      "useAuth must be used within an AuthProvider"
    );

  });

});