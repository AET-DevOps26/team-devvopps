package com.tum.gateway.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The single authentication/authorization boundary for the platform.
 *
 * - PUBLIC paths (/auth/login, /auth/signup, /actuator/**) pass through.
 * - Everything else requires a valid JWT in the "token" cookie → otherwise 401.
 * - ADMIN-only paths (/users/**, /auth/logs, /llm/logs) require role ADMIN → otherwise 403.
 *
 * On success the verified identity is stored as request attributes so
 * GatewayController can inject trusted X-User-Id / X-User-Role headers downstream.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "authUserId";
    public static final String ATTR_ROLE = "authRole";
    private static final String COOKIE_NAME = "token";

    private final JwtVerifier verifier;
    private final Counter unauthorized;
    private final Counter forbidden;

    public JwtAuthFilter(JwtVerifier verifier, MeterRegistry registry) {
        this.verifier = verifier;
        this.unauthorized = Counter.builder("auth.rejections").tag("reason", "unauthorized").register(registry);
        this.forbidden = Counter.builder("auth.rejections").tag("reason", "forbidden").register(registry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        String token = readCookie(request);
        JwtVerifier.Identity identity;
        try {
            if (token == null) {
                throw new IllegalStateException("missing token");
            }
            identity = verifier.verify(token);
        } catch (Exception e) {
            unauthorized.increment();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        if (isAdminOnly(path) && !"ADMIN".equals(identity.role())) {
            forbidden.increment();
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin role required");
            return;
        }

        request.setAttribute(ATTR_USER_ID, identity.userId());
        request.setAttribute(ATTR_ROLE, identity.role());
        chain.doFilter(request, response);
    }

    /** Endpoints reachable without a session. */
    private boolean isPublic(String path) {
        return path.equals("/auth/login")
                || path.equals("/auth/signup")
                || path.startsWith("/actuator/");
    }

    /** Endpoints that require the ADMIN role. */
    private boolean isAdminOnly(String path) {
        return path.startsWith("/users")
                || path.equals("/auth/logs")
                || path.equals("/llm/logs")
                || path.equals("/roadmaps/all");
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
