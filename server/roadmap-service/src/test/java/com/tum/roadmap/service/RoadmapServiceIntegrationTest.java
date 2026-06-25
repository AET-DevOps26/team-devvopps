package com.tum.roadmap.service;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.tum.roadmap.model.Roadmap;
import com.tum.roadmap.repository.GoalRepository;
import com.tum.roadmap.repository.RoadmapRepository;
import com.tum.roadmap.service.RoadmapService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RoadmapService.
 *
 * Uses WireMock to simulate real HTTP calls to user-service and LLM service.
 * Uses H2 in-memory database via @SpringBootTest for real database interactions.
 *
 * These tests verify:
 * - The real RestTemplate correctly builds and sends HTTP requests
 * - Real HTTP error responses (404, 503) are correctly handled
 * - Real network failures are caught and wrapped as ResponseStatusException
 * - Generated roadmaps are actually persisted to the database
 */

@SpringBootTest
@ActiveProfiles("test")
public class RoadmapServiceIntegrationTest {
    
    static WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    /**
     * Dynamically point user-service and LLM service properties
     * at the WireMock server port before the Spring context starts.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMock.start();
        registry.add("user.service.host", () -> "localhost");
        registry.add("user.service.port", () -> String.valueOf(wireMock.port()));
        registry.add("llm.service.host", () -> "localhost");
        registry.add("llm.service.port", () -> String.valueOf(wireMock.port()));
    }

    @Autowired
    private RoadmapService roadmapService;

    @Autowired
    private RoadmapRepository roadmapRepository;

    @Autowired
    private GoalRepository goalRepository;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        roadmapRepository.deleteAll();
        goalRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        wireMock.resetAll();
    }

    // ---------------------------------------------------------------------------
    // 200 (OK)
    // ---------------------------------------------------------------------------    

    /**
     * User exists, LLM responds, roadmap is saved to the database.
     */
    @Test
    void generateRoadmap_success_persistsToDatabase() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();

        Roadmap result = roadmapService.generateRoadmap(1L, "Learn machine learning");

        assertNotNull(result);
        assertNotNull(result.getRoadmap_id());
        assertEquals(1, result.getMilestones().size());
        assertEquals("Learn ML basics", result.getMilestones().get(0).getTitle());

        // Verify roadmap was actually saved in roadmap repository
        assertTrue(roadmapRepository.findById(result.getRoadmap_id()).isPresent());
    }

    /**
     * Verifies that a generated roadmap can be retrieved by ID from the database.
     */
    @Test
    void generateAndRetrieveRoadmap_returnsPersistedData() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();

        Roadmap generated = roadmapService.generateRoadmap(1L, "Learn ML");
        Roadmap retrieved = roadmapService.getRoadmap(generated.getRoadmap_id());

        assertNotNull(retrieved);
        assertEquals(generated.getRoadmap_id(), retrieved.getRoadmap_id());
        assertEquals(1, retrieved.getMilestones().size());
    }

    /**
     * Verifies that an LLM response with empty milestones results in
     * a roadmap with no milestones being saved to the database.
     */
    @Test
    void generateRoadmap_withEmptyMilestones_persistsEmptyRoadmap() {
        stubUserServiceSuccess(1L);
        wireMock.stubFor(post(urlPathEqualTo("/recommend"))
                .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"milestones\": []}")));
        
        Roadmap result = roadmapService.generateRoadmap(1L, "Learn ML");

        assertNotNull(result);
        assertTrue(result.getMilestones().isEmpty());
        assertTrue(roadmapRepository.findById(result.getRoadmap_id()).isPresent());
    }

    // ---------------------------------------------------------------------------
    // user-service failure scenarios
    // ---------------------------------------------------------------------------

    /**
     * Verifies that a real 404 HTTP response from user-service
     * is correctly caught and wrapped as a 404 ResponseStatusException.
     */
    @Test
    void generateRoadmap_throws404_whenUserServiceReturns404() {
        wireMock.stubFor(get(urlPathMatching("/users/99"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Not Found")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.generateRoadmap(99L, "Learn ML"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    /**
     * Verifies that a real network failure to user-service
     * is caught and wrapped as a 503 ResponseStatusException.
     */
    @Test
    void generateRoadmap_throws503_whenUserServiceConnectionFails() {
        wireMock.stubFor(get(urlPathMatching("/users/1"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.generateRoadmap(1L, "Learn ML"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    // ---------------------------------------------------------------------------
    // LLM service failure scenarios
    // ---------------------------------------------------------------------------

    /**
     * Verifies that a real network failure to the LLM service
     * is caught and wrapped as a 503 ResponseStatusException,
     * even when user-service succeeds.
     */
    @Test
    void generateRoadmap_throws503_whenLlmServiceConnectionFails() {
        stubUserServiceSuccess(1L);
        wireMock.stubFor(post(urlPathEqualTo("/recommend"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.generateRoadmap(1L, "Learn ML"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    // ---------------------------------------------------------------------------
    // getRoadmap
    // ---------------------------------------------------------------------------

    /**
     * Verifies that getRoadmap throws 404 when the roadmap does not exist in the database.
     */
    @Test
    void getRoadmap_throws404_whenNotInDatabase() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.getRoadmap(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Roadmap not found", ex.getReason());
    }

    

    // ---------------------------------------------------------------------------
    // WireMock helpers
    // ---------------------------------------------------------------------------

    private void stubUserServiceSuccess(Long userId) {
        wireMock.stubFor(get(urlPathMatching("/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"user_id\": " + userId + ", \"name\": \"Alice\"}")));            
    }

    private void stubLlmServiceSuccess() {
        wireMock.stubFor(post(urlPathEqualTo("/recommend"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "milestones": [
                                    {
                                      "title": "Learn ML basics",
                                      "description": "Foundation",
                                      "tasks": [
                                        {"title": "Study linear algebra", "completed": false}
                                      ]
                                    }
                                  ]
                                }
                                """)));
    }

}
