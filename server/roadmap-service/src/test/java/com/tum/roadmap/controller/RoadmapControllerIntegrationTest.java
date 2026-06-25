package com.tum.roadmap.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.tum.roadmap.repository.GoalRepository;
import com.tum.roadmap.repository.RoadmapRepository;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for RoadmapController.
 *
 * Uses @SpringBootTest with @AutoConfigureMockMvc to boot the full application
 * context and test the complete request/response cycle including:
 * - Real HTTP routing through the controller
 * - Real service layer logic
 * - Real database interactions (H2)
 * - Real HTTP calls to user-service and LLM service (via WireMock)
 *
 * Nothing is mocked here except the downstream services via WireMock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RoadmapControllerIntegrationTest {
    
    static WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    static ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMock.start();
        registry.add("user.service.host", () -> "localhost");
        registry.add("user.service.port", () -> String.valueOf(wireMock.port()));
        registry.add("llm.service.host", () -> "localhost");
        registry.add("llm.service.port", () -> String.valueOf(wireMock.port()));
    }

    @Autowired
    private MockMvc mockMvc;

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
    // POST /roadmaps/generate
    // ---------------------------------------------------------------------------

    /**
     * Full path through the controller with status code 200: valid request, user exists,
     * LLM responds, roadmap saved and returned as JSON.
     */
    @Test
    void generateRoadmap_returns200_withFullStack() throws Exception {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();

        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "1")
                        .param("goal", "Learn machine learning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestones").isArray())
                .andExpect(jsonPath("$.milestones[0].title").value("Learn ML basics"));
    }

    /**
     * Verifies that a 404 from user-service propagates as 404 through the controller.
     */
    @Test
    void generateRoadmap_returns404_whenUserNotFound() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathMatching("/users/99")
            )
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Not Found"))
        );

        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "99")
                        .param("goal", "Learn ML"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that a network failure to user-service propagates as 503.
     */
    @Test
    void generateRoadmap_returns503_whenUserServiceDown() throws Exception {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathMatching("/users/1")
            )
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER))
        );

        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "1")
                        .param("goal", "Learn ML"))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * Verifies that a network failure to LLM service propagates as 503.
     */
    @Test
    void generateRoadmap_returns503_whenLlmServiceDown() throws Exception {
        stubUserServiceSuccess(1L);
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/recommend")
            )
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER))
        );

        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "1")
                        .param("goal", "Learn ML"))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * Verifies that missing required params return 400 before hitting any service.
     */
    @Test
    void generateRoadmap_returns400_whenParamsMissing() throws Exception {
        mockMvc.perform(post("/roadmaps/generate"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // GET /roadmaps/{id}
    // ---------------------------------------------------------------------------

    /**
     * Verifies that a previously generated roadmap can be retrieved by ID.
     */
    @Test
    void getRoadmap_returns200_withPersistedRoadmap() throws Exception {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();

        // Generate first so we have a real ID
        String response = mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "1")
                        .param("goal", "Learn ML"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println(response);

        // Extract the ID from the response
        Long id = objectMapper.readTree(response)
                .get("roadmap_id")
                .asLong();

        mockMvc.perform(get("/roadmaps/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roadmap_id").value(id));
    }

    /**
     * Verifies that requesting a non-existent roadmap returns 404.
     */
    @Test
    void getRoadmap_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/roadmaps/999"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that a non-numeric ID returns 400.
     */
    @Test
    void getRoadmap_returns400_whenIdNotANumber() throws Exception {
        mockMvc.perform(get("/roadmaps/abc"))
                .andExpect(status().isBadRequest());
    }

    

    // ---------------------------------------------------------------------------
    // WireMock helpers
    // ---------------------------------------------------------------------------

    private void stubUserServiceSuccess(Long userId) {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathMatching("/users/" + userId)
            )
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"user_id\": " + userId + ", \"name\": \"Alice\"}"))
        );
    }

    private void stubLlmServiceSuccess() {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/recommend")
            )
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
                                """))
        );
    }
}
