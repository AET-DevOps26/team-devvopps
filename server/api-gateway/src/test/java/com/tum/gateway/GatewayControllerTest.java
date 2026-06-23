package com.tum.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GatewayController.
 *
 * Tests focus on the actual forwarding logic: URL construction, header filtering,
 * and HTTP method propagation. RestTemplate is mocked to avoid real HTTP calls.
 * @Value fields are injected manually via ReflectionTestUtils.
 */
class GatewayControllerTest {

    // Helper to create a controller with mocked RestTemplate and injected URLs
    private GatewayController createController(RestTemplate restTemplate) {
        GatewayController controller = new GatewayController(restTemplate);
        ReflectionTestUtils.setField(controller, "userServiceUrl", "http://user:8081");
        ReflectionTestUtils.setField(controller, "courseServiceUrl", "http://course:8082");
        ReflectionTestUtils.setField(controller, "roadmapServiceUrl", "http://roadmap:8083");
        return controller;
    }

    // Helper to create a mock HttpServletRequest
    private HttpServletRequest mockRequest(String uri, String query, String method) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getQueryString()).thenReturn(query);
        when(request.getMethod()).thenReturn(method);
        return request;
    }

    /**
     * Verifies that response headers 
     * are passed through to the client unchanged.
     */
    @Test
    void forward_preservesOtherResponseHeaders() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Content-Type", "application/json");
        responseHeaders.add("X-Custom-Header", "some-value");

        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(new byte[]{}, responseHeaders, HttpStatus.OK));

        ResponseEntity<byte[]> response = controller.forwardUser(
                mockRequest("/users/1", null, "GET"),
                new HttpEntity<>(new byte[]{})
        );

        assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));
        assertEquals("some-value", response.getHeaders().getFirst("X-Custom-Header"));
    }

    /**
     * Verifies that the query string is appended to the forwarded URL when present.
     */
    @Test
    void forward_appendsQueryString_whenPresent() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardCourse(
                mockRequest("/courses/search", "title=ml", "GET"),
                new HttpEntity<>(new byte[]{})
        );

        verify(restTemplate).exchange(
                eq("http://course:8082/courses/search?title=ml"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)
        );
    }

    /**
     * Verifies that no query string is appended when getQueryString() returns null.
     */
    @Test
    void forward_omitsQueryString_whenNull() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardUser(
                mockRequest("/users/1", null, "GET"),
                new HttpEntity<>(new byte[]{})
        );

        verify(restTemplate).exchange(
                eq("http://user:8081/users/1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class)
        );
    }

    /**
     * Verifies that the HTTP method from the original request is forwarded correctly.
     * Here POST is used to test that method propagation is not hardcoded to GET.
     */
    @Test
    void forward_propagatesHttpMethod() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardRoadmap(
                mockRequest("/roadmaps/generate", "userId=1&goal=ML", "POST"),
                new HttpEntity<>(new byte[]{})
        );

        verify(restTemplate).exchange(
                eq("http://roadmap:8083/roadmaps/generate?userId=1&goal=ML"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(byte[].class)
        );
    }

    /**
     * Verifies that the response status code from the downstream service
     * is passed through to the client unchanged.
     */
    @Test
    void forward_propagatesResponseStatusCode() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(new byte[]{}, HttpStatus.CREATED));

        ResponseEntity<byte[]> response = controller.forwardUser(
                mockRequest("/users", null, "POST"),
                new HttpEntity<>(new byte[]{})
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    /**
     * Verifies that the response body from the downstream service
     * is passed through to the client unchanged.
     */
    @Test
    void forward_propagatesResponseBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        byte[] expectedBody = "{\"id\":1}".getBytes();

        when(restTemplate.exchange(anyString(), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(expectedBody));

        ResponseEntity<byte[]> response = controller.forwardUser(
                mockRequest("/users/1", null, "GET"),
                new HttpEntity<>(new byte[]{})
        );

        assertArrayEquals(expectedBody, response.getBody());
    }
}