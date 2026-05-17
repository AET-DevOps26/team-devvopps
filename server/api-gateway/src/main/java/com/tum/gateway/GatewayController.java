package com.tum.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

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

    private ResponseEntity<byte[]> forward(HttpServletRequest request, HttpEntity<byte[]> entity, String targetBaseUrl) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String url = targetBaseUrl + path + (query != null ? "?" + query : "");
        return restTemplate.exchange(url, HttpMethod.valueOf(request.getMethod()), entity, byte[].class);
    }
}
