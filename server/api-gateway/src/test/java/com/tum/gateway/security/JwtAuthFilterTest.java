package com.tum.gateway.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthFilter.
 *
 * This test class verifies the authentication and authorization behavior
 * of the gateway security filter.
 *
 * The tests cover:
 * - Public endpoints bypass authentication.
 * - Requests without JWT cookies are rejected with HTTP 401.
 * - Invalid JWT tokens are rejected with HTTP 401.
 * - Valid JWT tokens authenticate users successfully.
 * - User identity information is stored as request attributes.
 * - ADMIN-only endpoints reject normal users with HTTP 403.
 * - ADMIN users can access protected admin endpoints.
 * - JWT tokens are correctly read from cookies.
 * - /roadmaps/all requires ADMIN role.
 * - /features and /settings: GET is public for all authenticated users,
 *   write methods (POST, PUT, PATCH, DELETE) require ADMIN.
 *
 * The tests mock the JwtVerifier because JWT creation and
 * cryptographic verification are tested separately. This class only tests
 * the filter's access-control decisions.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtAuthFilterTest {

    private JwtVerifier verifier;
    private JwtAuthFilter filter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    /**
     * Initializes fresh mocks and a new filter instance before each test.
     *
     * A new JwtAuthFilter is constructed with a mocked JwtVerifier
     * and an in-memory SimpleMeterRegistry, so metric counters work without
     * a real Micrometer backend.
     *
     * HttpServletRequest, HttpServletResponse, and FilterChain are mocked so 
     * individual tests can stub only the behaviour they care about and 
     * assert on interactions afterwards.
     */
    @BeforeEach
    void setUp() {
        verifier = mock(JwtVerifier.class);

        filter = new JwtAuthFilter(
            verifier,
            new SimpleMeterRegistry()
        );

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }


    // -------------------------------------------------------------------------
    // Public endpoints
    // -------------------------------------------------------------------------

    /**
     * Verifies that public endpoints do not require authentication.
     */
    @Test
    @DisplayName("Should allow public endpoints without JWT token")
    void shouldAllowPublicEndpointsWithoutToken() throws Exception {

        when(request.getRequestURI()).thenReturn("/auth/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(verifier);
    }

    /**
     * Verifies actuator endpoints are publicly accessible.
     */
    @Test
    @DisplayName("Should allow actuator endpoints without authentication")
    void shouldAllowActuatorEndpoints() throws Exception {

        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(verifier);
    }


    // -------------------------------------------------------------------------
    // Authentication (401)
    // -------------------------------------------------------------------------

    /**
     * Verifies that protected endpoints reject requests
     * when no token cookie exists.
     */
    @Test
    @DisplayName("Should return 401 when JWT cookie is missing")
    void shouldRejectMissingToken() throws Exception {

        when(request.getRequestURI()).thenReturn("/roadmaps");
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(
            HttpServletResponse.SC_UNAUTHORIZED,
            "Authentication required"
        );
        verifyNoInteractions(chain);
    }

    /**
     * Verifies that invalid JWT tokens are rejected.
     */
    @Test
    @DisplayName("Should return 401 when JWT verification fails")
    void shouldRejectInvalidToken() throws Exception {

        when(request.getRequestURI()).thenReturn("/roadmaps");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "invalid-token")
        });
        when(verifier.verify("invalid-token")).thenThrow(new RuntimeException("Invalid JWT"));

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(
            HttpServletResponse.SC_UNAUTHORIZED,
            "Authentication required"
        );
        verifyNoInteractions(chain);
    }


    // -------------------------------------------------------------------------
    // Authenticated user
    // -------------------------------------------------------------------------

    /**
     * Verifies that a valid USER JWT allows access to normal protected routes.
     */
    @Test
    @DisplayName("Should allow authenticated users with valid JWT")
    void shouldAllowAuthenticatedUser() throws Exception {

        when(request.getRequestURI()).thenReturn("/roadmaps");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "valid-token")
        });
        when(verifier.verify("valid-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request).setAttribute(JwtAuthFilter.ATTR_USER_ID, 123L);
        verify(request).setAttribute(JwtAuthFilter.ATTR_ROLE, "USER");
    }

    /**
     * Verifies that the filter finds the JWT when multiple cookies exist.
     */
    @Test
    @DisplayName("Should read token cookie when multiple cookies exist")
    void shouldReadTokenCookieAmongOtherCookies() throws Exception {

        when(request.getRequestURI()).thenReturn("/roadmaps");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("session", "abc"),
                new Cookie("token", "jwt-value"),
                new Cookie("theme", "dark")
        });
        when(verifier.verify("jwt-value")).thenReturn(
            new JwtVerifier.Identity(42L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(verifier).verify("jwt-value");
        verify(chain).doFilter(request, response);
    }


    // -------------------------------------------------------------------------
    // ADMIN-only routes – /users, /auth/logs, /llm/logs
    // -------------------------------------------------------------------------

    /**
     * Verifies that normal users cannot access ADMIN-only routes.
     */
    @Test
    @DisplayName("Should return 403 when USER accesses admin endpoint")
    void shouldRejectNonAdminAccess() throws Exception {

        when(request.getRequestURI()).thenReturn("/users");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(
            HttpServletResponse.SC_FORBIDDEN,
            "Admin role required"
        );
        verifyNoInteractions(chain);
    }

    /**
     * Verifies that administrators can access ADMIN-only routes.
     */
    @Test
    @DisplayName("Should allow ADMIN access to admin endpoints")
    void shouldAllowAdminAccess() throws Exception {

        when(request.getRequestURI()).thenReturn("/users");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "admin-token")
        });
        when(verifier.verify("admin-token")).thenReturn(
            new JwtVerifier.Identity(1L, "ADMIN")
        );

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }


    // -------------------------------------------------------------------------
    // /roadmaps/all – ADMIN only
    // -------------------------------------------------------------------------

    /**
     * Verifies that /roadmaps/all is blocked for regular users.
     */
    @Test
    @DisplayName("Should return 403 when USER accesses /roadmaps/all")
    void shouldRejectUserAccessToRoadmapsAll() throws Exception {

        when(request.getRequestURI()).thenReturn("/roadmaps/all");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(
            HttpServletResponse.SC_FORBIDDEN,
            "Admin role required"
        );
        verifyNoInteractions(chain);
    }

    /**
     * Verifies that /roadmaps/all is accessible to admins.
     */
    @Test
    @DisplayName("Should allow ADMIN access to /roadmaps/all")
    void shouldAllowAdminAccessToRoadmapsAll() throws Exception {

        when(request.getRequestURI()).thenReturn("/roadmaps/all");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "admin-token")
        });
        when(verifier.verify("admin-token")).thenReturn(
            new JwtVerifier.Identity(1L, "ADMIN")
        );

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    /**
     * Verifies that /roadmaps (without /all) remains accessible to regular users.
     */
    @Test
    @DisplayName("Should allow USER access to /roadmaps (not /roadmaps/all)")
    void shouldAllowUserAccessToRoadmaps() throws Exception {

        when(request.getRequestURI()).thenReturn("/roadmaps");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }


    // -------------------------------------------------------------------------
    // /features – GET is open to all authenticated users; writes are ADMIN only
    // -------------------------------------------------------------------------

    /**
     * Verifies that any authenticated user can GET /features.
     */
    @Test
    @DisplayName("Should allow USER to GET /features")
    void shouldAllowUserToGetFeatures() throws Exception {

        when(request.getRequestURI()).thenReturn("/features");
        when(request.getMethod()).thenReturn("GET");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    /**
     * Verifies that a USER cannot POST to /features.
     */
    @Test
    @DisplayName("Should return 403 when USER POSTs to /features")
    void shouldRejectUserPostToFeatures() throws Exception {

        when(request.getRequestURI()).thenReturn("/features");
        when(request.getMethod()).thenReturn("POST");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(
            HttpServletResponse.SC_FORBIDDEN,
            "Admin role required"
        );
        verifyNoInteractions(chain);
    }

    /**
     * Verifies that a USER cannot PUT to /features.
     */
    @Test
    @DisplayName("Should return 403 when USER PUTs to /features")
    void shouldRejectUserPutToFeatures() throws Exception {

        when(request.getRequestURI()).thenReturn("/features/dark-mode");
        when(request.getMethod()).thenReturn("PUT");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(
            HttpServletResponse.SC_FORBIDDEN,
            "Admin role required"
        );
        verifyNoInteractions(chain);
    }

    /**
     * Verifies that an ADMIN can POST to /features.
     */
    @Test
    @DisplayName("Should allow ADMIN to POST to /features")
    void shouldAllowAdminPostToFeatures() throws Exception {

        when(request.getRequestURI()).thenReturn("/features");
        when(request.getMethod()).thenReturn("POST");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "admin-token")
        });
        when(verifier.verify("admin-token")).thenReturn(
            new JwtVerifier.Identity(1L, "ADMIN")
        );

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }


    // -------------------------------------------------------------------------
    // /settings – GET is open to all authenticated users; writes are ADMIN only
    // -------------------------------------------------------------------------

    /**
     * Verifies that any authenticated user can GET /settings.
     */
    @Test
    @DisplayName("Should allow USER to GET /settings")
    void shouldAllowUserToGetSettings() throws Exception {

        when(request.getRequestURI()).thenReturn("/settings");
        when(request.getMethod()).thenReturn("GET");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    /**
     * Verifies that a USER cannot POST to /settings.
     */
    @Test
    @DisplayName("Should return 403 when USER POSTs to /settings")
    void shouldRejectUserPostToSettings() throws Exception {

        when(request.getRequestURI()).thenReturn("/settings");
        when(request.getMethod()).thenReturn("POST");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(
            HttpServletResponse.SC_FORBIDDEN,
            "Admin role required"
        );
        verifyNoInteractions(chain);
    }

    /**
     * Verifies that a USER cannot PATCH /settings.
     */
    @Test
    @DisplayName("Should return 403 when USER PATCHes /settings")
    void shouldRejectUserPatchToSettings() throws Exception {

        when(request.getRequestURI()).thenReturn("/settings/theme");
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "user-token")
        });
        when(verifier.verify("user-token")).thenReturn(
            new JwtVerifier.Identity(123L, "USER")
        );

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(
            HttpServletResponse.SC_FORBIDDEN,
            "Admin role required"
        );
        verifyNoInteractions(chain);
    }

    /**
     * Verifies that an ADMIN can POST to /settings.
     */
    @Test
    @DisplayName("Should allow ADMIN to POST to /settings")
    void shouldAllowAdminPostToSettings() throws Exception {

        when(request.getRequestURI()).thenReturn("/settings");
        when(request.getMethod()).thenReturn("POST");
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("token", "admin-token")
        });
        when(verifier.verify("admin-token")).thenReturn(
            new JwtVerifier.Identity(1L, "ADMIN")
        );

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}