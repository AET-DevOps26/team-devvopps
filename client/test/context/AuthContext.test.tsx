import { render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { AuthProvider, useAuth } from "../../src/context/AuthContext";

const mockUser = {
  userId: 1,
  email: "test@example.com",
  role: "USER",
};

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


function renderAuth() {
  return render(
    <AuthProvider>
      <TestConsumer />
    </AuthProvider>
  );
}


describe("AuthContext", () => {

  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
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