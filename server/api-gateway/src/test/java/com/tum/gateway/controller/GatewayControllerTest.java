package com.tum.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;

import com.tum.gateway.controller.GatewayController;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GatewayController.
 *
 * These tests verify the gateway forwarding behavior without starting real downstream services.
 * RestTemplate is mocked to isolate the controller logic while still validating:
 * - Correct construction of forwarded service URLs
 * - Preservation of HTTP methods (GET, POST, DELETE, etc.)
 * - Forwarding of request headers and query parameters
 * - Propagation of downstream response status codes, headers, and bodies
 * - Correct handling of downstream HTTP error responses
 * 
 * @Value fields are injected manually via ReflectionTestUtils.
 */
class GatewayControllerTest {

    // Helper to create a controller with mocked RestTemplate and injected URLs
    private GatewayController createController(RestTemplate restTemplate) {
        GatewayController controller = new GatewayController(restTemplate);
        
        /*
        * ReflectionTestUtils allows tests to access and modify private fields
        * without using public setters.
        *
        * In the real application, Spring injects these values from configuration:
        *
        * userServiceUrl=${services.user.url}
        * courseServiceUrl=${services.course.url}
        * roadmapServiceUrl=${services.roadmap.url}
        *
        * Because this is a unit test and Spring is not running, we inject
        * representative test values manually.
        */
        ReflectionTestUtils.setField(controller, "userServiceUrl", "http://user-service:8081");
        ReflectionTestUtils.setField(controller, "courseServiceUrl", "http://course-service:8082");
        ReflectionTestUtils.setField(controller, "roadmapServiceUrl", "http://roadmap-service:8083");
        
        return controller;
    }

    /**
     * Creates a mocked HttpServletRequest containing only the information
     * required by GatewayController.
     * The real servlet container normally creates HttpServletRequest objects
     * during an HTTP request. Since this is a unit test without a running server,
     * Mockito is used to create a fake request object.
     * Only the methods used by the controller are stubbed:
     * - getRequestURI(): provides the endpoint path
     * - getQueryString(): provides optional query parameters
     * - getMethod(): provides the HTTP method (GET, POST, PUT, DELETE, ...)
     */
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

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
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

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardCourse(
                mockRequest("/courses/search", "title=ml", "GET"),
                new HttpEntity<>(new byte[]{})
        );

        verify(restTemplate).exchange(
                eq(URI.create("http://course-service:8082/courses/search?title=ml")),
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

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardUser(
                mockRequest("/users/1", null, "GET"),
                new HttpEntity<>(new byte[]{})
        );

        verify(restTemplate).exchange(
                eq(URI.create("http://user-service:8081/users/1")),
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

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardRoadmap(
                mockRequest("/roadmaps/generate", "userId=1&goal=ML", "POST"),
                new HttpEntity<>(new byte[]{})
        );

        verify(restTemplate).exchange(
                eq(URI.create("http://roadmap-service:8083/roadmaps/generate?userId=1&goal=ML")),
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

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
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

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(expectedBody));

        ResponseEntity<byte[]> response = controller.forwardUser(
                mockRequest("/users/1", null, "GET"),
                new HttpEntity<>(new byte[]{})
        );

        assertArrayEquals(expectedBody, response.getBody());
    }

    /**
     * Verifies that downstream HTTP errors are forwarded with the original
     * status code and response body instead of becoming a 500.
     */
    @Test
    void forward_propagatesDownstreamErrorResponse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        byte[] errorBody = "{\"error\":\"User not found\"}".getBytes();

        HttpStatusCodeException exception =
            new org.springframework.web.client.HttpClientErrorException(
                    HttpStatus.NOT_FOUND,
                    "Not Found",
                    errorBody,
                    null
            );

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
            .thenThrow(exception);

        ResponseEntity<byte[]> response = controller.forwardUser(
            mockRequest("/users/99", null, "GET"),
            new HttpEntity<>(new byte[]{})
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertArrayEquals(errorBody, response.getBody());
    }

    /**
     * Verifies that Transfer-Encoding is removed from downstream responses.
     */
     @Test
     void forward_removesTransferEncodingHeader() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Transfer-Encoding", "chunked");
        responseHeaders.add("Content-Type", "application/json");

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(
                        new byte[]{},
                        responseHeaders,
                        HttpStatus.OK
                ));

        ResponseEntity<byte[]> response = controller.forwardUser(
                mockRequest("/users/1", null, "GET"),
                new HttpEntity<>(new byte[]{})
        );

        assertNull(response.getHeaders().getFirst("Transfer-Encoding"));
        assertEquals(
                "application/json",
                response.getHeaders().getFirst("Content-Type")
        );
     }

     /**
      * Verifies that incoming request headers are forwarded.
      */
      @Test
      void forward_preservesRequestHeaders() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add("Authorization", "Bearer token");

        HttpEntity<byte[]> entity = new HttpEntity<>(
                new byte[]{},
                requestHeaders
        );

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardUser(
                mockRequest("/users/1", null, "GET"),
                entity
        );

        verify(restTemplate).exchange(
                eq(URI.create("http://user-service:8081/users/1")),
                eq(HttpMethod.GET),
                eq(entity),
                eq(byte[].class)
        );
     }

     /**
     * Verifies that /features/** is forwarded to user-service.
     */
     @Test
     void forwardFeatures_routesToUserService() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
           .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardFeatures(
            mockRequest("/features", null, "GET"),
            new HttpEntity<>(new byte[]{})
        );

        verify(restTemplate).exchange(
            eq(URI.create("http://user-service:8081/features")),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(byte[].class)
        );
    }

    /**
    * Verifies that /settings/** is forwarded to user-service.
    */
   @Test
   void forwardSettings_routesToUserService() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GatewayController controller = createController(restTemplate);

        when(restTemplate.exchange(any(URI.class), any(), any(), eq(byte[].class)))
            .thenReturn(ResponseEntity.ok(new byte[]{}));

        controller.forwardSettings(
            mockRequest("/settings", null, "GET"),
            new HttpEntity<>(new byte[]{})
        );

        verify(restTemplate).exchange(
            eq(URI.create("http://user-service:8081/settings")),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(byte[].class)
        );
    }

    @Test
void forwardFeatures_putMethodIsPreserved() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    GatewayController controller = createController(restTemplate);

    when(restTemplate.exchange(any(), any(), any(), eq(byte[].class)))
            .thenReturn(ResponseEntity.ok(new byte[]{}));

    controller.forwardFeatures(
            mockRequest("/features/llm", null, "PUT"),
            new HttpEntity<>(new byte[]{})
    );

    verify(restTemplate).exchange(
            eq(URI.create("http://user-service:8081/features/llm")),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            eq(byte[].class)
    );
}

@Test
void forwardSettings_putMethodIsPreserved() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    GatewayController controller = createController(restTemplate);

    when(restTemplate.exchange(any(), any(), any(), eq(byte[].class)))
            .thenReturn(ResponseEntity.ok(new byte[]{}));

    controller.forwardFeatures(
            mockRequest("/settings", null, "PUT"),
            new HttpEntity<>(new byte[]{})
    );

    verify(restTemplate).exchange(
            eq(URI.create("http://user-service:8081/settings")),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            eq(byte[].class)
    );
}
}