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
 * These tests verify:
 * - Controller endpoints are correctly mapped and return expected HTTP status codes
 * - Request headers and parameters are validated correctly
 * - Roadmap generation works through the complete stack and persists data
 * - User-service and LLM-service failures are correctly propagated as HTTP errors
 * - Generated roadmaps can be retrieved through the API
 * - Task completion toggling updates persisted roadmap state correctly
 * - Progress calculation is correctly exposed through the REST API
 * - Unauthorized access and missing resources return the correct error responses 
 *
 * Nothing is mocked here except the downstream services via WireMock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RoadmapControllerIntegrationTest {
    
    static WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Configures dynamic properties for the Spring test context.
     *
     * Starts a WireMock server on a random available port and overrides the
     * application properties so that external service calls (user-service and
     * LLM-service) are redirected to the mock server instead of real services.
     *
     * This allows the integration test to run independently without requiring
     * the actual microservices to be running.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMock.start();
        // Redirect user-service calls to the WireMock instance
        registry.add("user.service.host", () -> "localhost");
        registry.add("user.service.port", () -> String.valueOf(wireMock.port()));
        // Redirect LLM-service calls to the WireMock instance
        registry.add("llm.service.host", () -> "localhost");
        registry.add("llm.service.port", () -> String.valueOf(wireMock.port()));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoadmapRepository roadmapRepository;

    @Autowired
    private GoalRepository goalRepository;

    /**
     * Runs before each test.
     *
     * Resets WireMock to remove any previously configured stubs or recorded
     * requests, and clears the database tables to ensure every test starts with
     * a clean state.
     *
     * This prevents tests from affecting each other and makes test results
     * deterministic.
     */
    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        roadmapRepository.deleteAll();
        goalRepository.deleteAll();
    }

    /**
     * Runs after each test.
     *
     * Cleans up WireMock state after the test has finished, ensuring that
     * stubs, mappings, and request history do not leak into following tests.
     *
     * This provides additional isolation between test cases.
     */
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
                        .param("goal", "Learn machine learning")
                        .header("X-User-Id", "1"))
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
                        .header("X-User-Id", "1")
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
                        .header("X-User-Id", "1")
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
                        .header("X-User-Id", "1")
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
                        .header("X-User-Id", "1")
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

        mockMvc.perform(get("/roadmaps/" + id).header("X-User-Id","1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roadmap_id").value(id));
    }

    /**
     * Verifies that requesting a non-existent roadmap returns 404.
     */
    @Test
    void getRoadmap_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/roadmaps/999").header("X-User-Id","1"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that a non-numeric ID returns 400.
     */
    @Test
    void getRoadmap_returns400_whenIdNotANumber() throws Exception {
        mockMvc.perform(get("/roadmaps/abc").header("X-User-Id","1"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // PATCH /roadmaps/{roadmapId}/tasks/{taskId}/complete
    // ---------------------------------------------------------------------------

    /**
     * Verifies that toggling a task changes its completion state and returns the
     * updated roadmap with status 200.
     */
    @Test
    void toggleCompletionTask_returns200_andFlipsTaskStatus() throws Exception {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        // Create a roadmap first
        String generateResponse = mockMvc.perform(post("/roadmaps/generate")
                        .header("X-User-Id", "1")
                        .param("goal", "Learn ML"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
 
        Long roadmapId = objectMapper.readTree(generateResponse).get("roadmap_id").asLong();
        Long taskId = objectMapper.readTree(generateResponse)
                .get("milestones").get(0)
                .get("tasks").get(0)
                .get("task_id").asLong();
 
        // Toggle the task to completed
        mockMvc.perform(patch("/roadmaps/" + roadmapId + "/tasks/" + taskId + "/complete").header("X-User-Id","1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                    "$.milestones[0].tasks[?(@.task_id == " + taskId + ")].completed"
                ).value(true));
    }

    /**
     * Verifies that toggling the same task twice returns it to its original state.
     */
    @Test
    void toggleCompletionTask_returns200_andTogglesBackToIncomplete() throws Exception {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        String generateResponse = mockMvc.perform(post("/roadmaps/generate")
                        .header("X-User-Id", "1")
                        .param("goal", "Learn ML"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
 
        Long roadmapId = objectMapper.readTree(generateResponse).get("roadmap_id").asLong();
        Long taskId = objectMapper.readTree(generateResponse)
                .get("milestones").get(0)
                .get("tasks").get(0)
                .get("task_id").asLong();
 
        String patchUrl = "/roadmaps/" + roadmapId + "/tasks/" + taskId + "/complete";
 
        // First toggle → completed
        mockMvc.perform(patch(patchUrl).header("X-User-Id","1")).andExpect(status().isOk());
 
        // Second toggle → back to incomplete
        mockMvc.perform(patch(patchUrl).header("X-User-Id","1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                    "$.milestones[0].tasks[?(@.task_id == " + taskId + ")].completed"
                ).value(false));
    }

    /**
     * Verifies that toggling a task that doesn't exist in the roadmap returns 404.
     */
    @Test
    void toggleCompletionTask_returns404_whenTaskNotFound() throws Exception {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        String generateResponse = mockMvc.perform(post("/roadmaps/generate")
                        .header("X-User-Id", "1")
                        .param("goal", "Learn ML"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
 
        Long roadmapId = objectMapper.readTree(generateResponse).get("roadmap_id").asLong();
 
        mockMvc.perform(patch("/roadmaps/" + roadmapId + "/tasks/999999/complete").header("X-User-Id","1"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that using a non-existent roadmap ID returns 404.
     */
    @Test
    void toggleCompletionTask_returns404_whenRoadmapNotFound() throws Exception {
        mockMvc.perform(patch("/roadmaps/999999/tasks/1/complete").header("X-User-Id","1"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------
    // GET /roadmaps/{roadmapId}/progress
    // ---------------------------------------------------------------------------
 
    /**
     * Verifies that after completing all tasks, the progress endpoint
     * reports the roadmap as fully completed.
     */
    @Test
    void getProgress_returns200_withFullCompletion_afterAllTasksToggled() throws Exception {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        String generateResponse = mockMvc.perform(post("/roadmaps/generate")
                        .header("X-User-Id", "1")
                        .param("goal", "Learn ML"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
 
        Long roadmapId = objectMapper.readTree(generateResponse).get("roadmap_id").asLong();
        Long taskId = objectMapper.readTree(generateResponse)
                .get("milestones").get(0)
                .get("tasks").get(0)
                .get("task_id").asLong();
 
        // Complete the only task
        mockMvc.perform(patch("/roadmaps/" + roadmapId + "/tasks/" + taskId + "/complete").header("X-User-Id","1"))
                .andExpect(status().isOk());
 
        mockMvc.perform(get("/roadmaps/" + roadmapId + "/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.completedMilestones").value(1))
                .andExpect(jsonPath("$.totalMilestones").value(1))
                .andExpect(jsonPath("$.roadmapCompleted").value(true));
    }
 
    /**
     * Verifies that progress reflects partial completion after toggling some tasks.
     */
    @Test
    void getProgress_returns200_withPartialCompletion_afterSomeTasksToggled() throws Exception {
        stubUserServiceSuccess(1L);
        stubLlmServiceWithTwoTasks();
 
        String generateResponse = mockMvc.perform(post("/roadmaps/generate")
                        .header("X-User-Id", "1")
                        .param("goal", "Learn ML"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
 
        Long roadmapId = objectMapper.readTree(generateResponse).get("roadmap_id").asLong();
        Long firstTaskId = objectMapper.readTree(generateResponse)
                .get("milestones").get(0)
                .get("tasks").get(0)
                .get("task_id").asLong();
 
        // Complete only the first task
        mockMvc.perform(patch("/roadmaps/" + roadmapId + "/tasks/" + firstTaskId + "/complete").header("X-User-Id","1"))
                .andExpect(status().isOk());
 
        mockMvc.perform(get("/roadmaps/" + roadmapId + "/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.totalTasks").value(2))
                .andExpect(jsonPath("$.completedMilestones").value(0))
                .andExpect(jsonPath("$.roadmapCompleted").value(false));
    }
 
    /**
     * Verifies that GET .../progress returns 404 for a non-existent roadmap.
     */
    @Test
    void getProgress_returns404_whenRoadmapNotFound() throws Exception {
        mockMvc.perform(get("/roadmaps/999999/progress"))
                .andExpect(status().isNotFound());
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

    private void stubLlmServiceWithTwoTasks() {
        wireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/recommend"))
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
                                        {"title": "Study linear algebra", "completed": false},
                                        {"title": "Study statistics", "completed": false}
                                      ]
                                    }
                                  ]
                                }
                                """))
        );
    }
}
