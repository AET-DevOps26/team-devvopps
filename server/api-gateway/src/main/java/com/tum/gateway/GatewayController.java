package com.tum.gateway;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * API Gateway controller that proxies all incoming requests to the appropriate downstream service.
 *
 * Routes:
 *   /users/**    → user-service
 *   /courses/**  → course-service
 *   /roadmaps/** → roadmap-service
 *
 * The full request (method, headers, body, query params) is forwarded as-is.
 * Responses are passed back to the caller unchanged, except Transfer-Encoding
 * which is stripped to avoid chunked-encoding conflicts with Spring's response writing.
 */
@RestController
@CrossOrigin
public class GatewayController {

    @Value("${services.user.url}")
    private String userServiceUrl;

    @Value("${services.course.url}")
    private String courseServiceUrl;

    @Value("${services.roadmap.url}")
    private String roadmapServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @RequestMapping("/users/**")
    public ResponseEntity<byte[]> forwardUser(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, userServiceUrl);
    }

    @RequestMapping("/courses/**")
    public ResponseEntity<byte[]> forwardCourse(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, courseServiceUrl);
    }

    @RequestMapping("/roadmaps/**")
    public ResponseEntity<byte[]> forwardRoadmap(HttpServletRequest request, HttpEntity<byte[]> entity) {
        return forward(request, entity, roadmapServiceUrl);
    }

    /**
     * Forwards the incoming HTTP request to the target service and returns its response.
     *
     * Transfer-Encoding is removed from the response headers because Spring sets its own
     * transfer encoding when writing the response body, and keeping the upstream value
     * causes encoding conflicts on the client side.
     *
     * @param request       the original incoming HTTP request
     * @param entity        the request body and headers
     * @param targetBaseUrl the base URL of the downstream service to forward to
     */
    private ResponseEntity<byte[]> forward(HttpServletRequest request, HttpEntity<byte[]> entity, String targetBaseUrl) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String url = targetBaseUrl + path + (query != null ? "?" + query : "");
        // getRequestURI()/getQueryString() return the RAW (already percent-encoded)
        // values. Pass a URI object so RestTemplate forwards them as-is — the
        // String overload would encode a second time (%20 → %2520), making
        // downstream services see literal "%20" in parameter values.
        
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(URI.create(url), HttpMethod.valueOf(request.getMethod()), entity, byte[].class);
            // Copy response headers, excluding Transfer-Encoding to avoid chunked encoding conflicts.

            HttpHeaders headers = new HttpHeaders();
            response.getHeaders().forEach((key, values) -> {
                if (!key.equalsIgnoreCase("Transfer-Encoding")) {
                    headers.put(key, values);
                }
            });
            return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
        } catch (HttpStatusCodeException e) {
            HttpHeaders headers = new HttpHeaders();

            e.getResponseHeaders().forEach((key, values) -> {
                if (!key.equalsIgnoreCase("Transfer-Encoding")) {
                    headers.put(key, values);
                }
            });
            
            return ResponseEntity.status(e.getStatusCode()).headers(headers).body(e.getResponseBodyAsByteArray());
        }
        
        
    }
}
