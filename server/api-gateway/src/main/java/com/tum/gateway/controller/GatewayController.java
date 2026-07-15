package com.tum.gateway.controller;

import com.tum.gateway.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * API Gateway controller that proxies all incoming requests to the appropriate downstream service.
 *
 * Routes:
 *   /auth/**     → user-service
 *   /users/**    → user-service
 *   /courses/**  → course-service
 *   /roadmaps/** → roadmap-service
 *   /llm/**      → llm-service (the /llm prefix is stripped before forwarding)
 *
 * Authentication/authorization is handled by {@link JwtAuthFilter} before this
 * controller runs. Here we forward the request and — for authenticated requests —
 * strip any client-supplied X-User-* headers and replace them with the values the
 * filter derived from the verified JWT, so downstream services can trust them.
 */
@RestController
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST,
        RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class GatewayController {

    private static final String LLM_PREFIX = "/llm";

    @Value("${services.user.url}")
    private String userServiceUrl;

    @Value("${services.course.url}")
    private String courseServiceUrl;

    @Value("${services.roadmap.url}")
    private String roadmapServiceUrl;

    @Value("${services.llm.url}")
    private String llmServiceUrl;

    private final RestTemplate restTemplate;

    public GatewayController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RequestMapping("/auth/**")
    public ResponseEntity<byte[]> forwardAuth(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, userServiceUrl, request.getRequestURI());
    }

    @RequestMapping("/users/**")
    public ResponseEntity<byte[]> forwardUser(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, userServiceUrl, request.getRequestURI());
    }

    @RequestMapping("/courses/**")
    public ResponseEntity<byte[]> forwardCourse(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, courseServiceUrl, request.getRequestURI());
    }

    // Feature flags live in user-service; GET is any signed-in user,
    // PUT is ADMIN-only (enforced in JwtAuthFilter).
    @RequestMapping("/features/**")
    public ResponseEntity<byte[]> forwardFeatures(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, userServiceUrl, request.getRequestURI());
    }

    // Runtime settings (prompt sections, token limit) — same auth model as features.
    @RequestMapping("/settings/**")
    public ResponseEntity<byte[]> forwardSettings(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, userServiceUrl, request.getRequestURI());
    }

    @RequestMapping(
        value = "/roadmaps/**",
        method = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE
        }
    )
    public ResponseEntity<byte[]> forwardRoadmap(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, roadmapServiceUrl, request.getRequestURI());
    }

    @RequestMapping("/llm/**")
    public ResponseEntity<byte[]> forwardLlm(HttpServletRequest request, HttpEntity<byte[]> entity) {
        // llm-service endpoints live at the root (/usage, /logs), so drop the /llm prefix.
        String downstreamPath = request.getRequestURI().substring(LLM_PREFIX.length());
        return forward(request, entity, llmServiceUrl, downstreamPath);
    }

    /**
     * Forwards the incoming HTTP request to the target service and returns its response.
     *
     * Client-supplied X-User-Id / X-User-Role headers are removed and replaced with the
     * identity the auth filter derived from the verified JWT (present as request attributes),
     * so a caller cannot spoof another user. Transfer-Encoding is stripped from the response
     * to avoid chunked-encoding conflicts with Spring's response writing.
     *
     * @param request        the original incoming HTTP request
     * @param entity         the request body and headers
     * @param targetBaseUrl  the base URL of the downstream service to forward to
     * @param downstreamPath the path to append to the base URL (prefix already adjusted)
     */
    private ResponseEntity<byte[]> forward(HttpServletRequest request, HttpEntity<byte[]> entity,
                                           String targetBaseUrl, String downstreamPath) {
        String query = request.getQueryString();
        String url = targetBaseUrl + downstreamPath + (query != null ? "?" + query : "");

        HttpEntity<byte[]> forwardEntity = withTrustedIdentity(request, entity);

        // Pass an already-encoded URI (not a String) so RestTemplate does NOT
        // re-encode '%'. Otherwise a param like goal=learn%20docker would be
        // double-encoded to learn%2520docker and stored/decoded as "learn%20docker".
        URI targetUri = URI.create(url);

        ResponseEntity<byte[]> response;
        try {
            response = restTemplate.exchange(targetUri, HttpMethod.valueOf(request.getMethod()), forwardEntity, byte[].class);
        } catch (HttpStatusCodeException e) {
            // RestTemplate throws on non-2xx responses instead of returning them.
            // Re-wrap so the downstream service's real status/body reaches the caller
            // instead of being flattened into a 500 by Spring's default error handling.
            response = ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsByteArray());
        }

        // Copy response headers, excluding Transfer-Encoding to avoid chunked encoding conflicts.
        HttpHeaders headers = new HttpHeaders();
        response.getHeaders().forEach((key, values) -> {
            if (!key.equalsIgnoreCase("Transfer-Encoding")) {
                headers.put(key, values);
            }
        });
        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
    }

    /**
     * Rebuilds the forwarded headers: drops any incoming X-User-* (anti-spoofing) and
     * injects the verified identity from the auth filter, when present.
     */
    private HttpEntity<byte[]> withTrustedIdentity(HttpServletRequest request, HttpEntity<byte[]> entity) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(entity.getHeaders());
        headers.remove("X-User-Id");
        headers.remove("X-User-Role");

        Object userId = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        Object role = request.getAttribute(JwtAuthFilter.ATTR_ROLE);
        if (userId != null) {
            headers.set("X-User-Id", String.valueOf(userId));
            headers.set("X-User-Role", String.valueOf(role));
        }
        return new HttpEntity<>(entity.getBody(), headers);
    }
}
