package com.tum.roadmap.service;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.tum.roadmap.model.Roadmap;
import com.tum.roadmap.model.Status;
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
    /* 
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
    */

    /**
     * Verifies that a milestone returned by the LLM without tasks is silently
     * skipped, and since that leaves no valid milestones, a 502 is thrown
     * and nothing is persisted.
     */
    @Test
    void generateRoadmap_throws502_whenAllLlmMilestonesHaveNoTasks() {
        stubUserServiceSuccess(1L);
        wireMock.stubFor(post(urlPathEqualTo("/recommend"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "milestones": [
                                    {
                                      "title": "Empty milestone",
                                      "description": "No tasks",
                                      "tasks": []
                                    }
                                  ]
                                }
                                """)));
 
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.generateRoadmap(1L, "Learn ML"));
 
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals(0, roadmapRepository.count());
    }

    /**
     * Verifies that milestones without tasks are skipped while milestones
     * with tasks are still saved normally.
     */
    @Test
    void generateRoadmap_skipsTasklessMilestones_andSavesRemainingOnes() {
        stubUserServiceSuccess(1L);
        wireMock.stubFor(post(urlPathEqualTo("/recommend"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "milestones": [
                                    {
                                      "title": "Valid milestone",
                                      "description": "Has tasks",
                                      "tasks": [
                                        {"title": "Do something", "completed": false}
                                      ]
                                    },
                                    {
                                      "title": "Empty milestone",
                                      "description": "No tasks",
                                      "tasks": []
                                    }
                                  ]
                                }
                                """)));

        Roadmap result = roadmapService.generateRoadmap(1L, "Learn ML");
 
        assertEquals(1, result.getMilestones().size());
        assertEquals("Valid milestone", result.getMilestones().get(0).getTitle());
    }

    // ---------------------------------------------------------------------------
    // user-service failure scenarios (generateRoadmap)
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
    // LLM service failure scenarios (generateRoadmap)
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

    /**
     * Verifies that a 429 from the LLM is propagated as TOO_MANY_REQUESTS.
     */
    @Test
    void generateRoadmap_throws429_whenLlmReturns429() {
        stubUserServiceSuccess(1L);
        wireMock.stubFor(post(urlPathEqualTo("/recommend"))
                .willReturn(aResponse().withStatus(429)));
 
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.generateRoadmap(1L, "Learn ML"));
 
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
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
    // toggleCompletionTask
    // ---------------------------------------------------------------------------
 
    /**
     * Verifies that toggling an incomplete task marks it as completed
     * and the change is persisted to the database.
     */
    @Test
    void toggleCompletionTask_marksTaskComplete_andPersists() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
        Long roadmapId = roadmap.getRoadmap_id();
        Long taskId = roadmap.getMilestones().get(0).getTasks().get(0).getTask_id();
 
        Roadmap updated = roadmapService.toggleCompletionTask(roadmapId, taskId);
 
        assertTrue(updated.getMilestones().get(0).getTasks().get(0).isCompleted());
 
        // Verify the state was actually persisted
        Roadmap reloaded = roadmapService.getRoadmap(roadmapId);
        assertTrue(reloaded.getMilestones().get(0).getTasks().get(0).isCompleted());
    }
    
    /**
     * Verifies that toggling the same task twice returns it to its original
     * incomplete state, and the change is persisted.
     */
    @Test
    void toggleCompletionTask_togglesBackToIncomplete_andPersists() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
        Long roadmapId = roadmap.getRoadmap_id();
        Long taskId = roadmap.getMilestones().get(0).getTasks().get(0).getTask_id();
 
        roadmapService.toggleCompletionTask(roadmapId, taskId); // complete
        roadmapService.toggleCompletionTask(roadmapId, taskId); // incomplete
 
        Roadmap reloaded = roadmapService.getRoadmap(roadmapId);
        assertFalse(reloaded.getMilestones().get(0).getTasks().get(0).isCompleted());
    }
 
    /**
     * Verifies that when all tasks in a milestone are completed,
     * the milestone's status is updated to COMPLETED.
     */
    @Test
    void toggleCompletionTask_updatesMilestoneStatusToCompleted_whenAllTasksDone() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
        Long roadmapId = roadmap.getRoadmap_id();
        Long taskId = roadmap.getMilestones().get(0).getTasks().get(0).getTask_id();
 
        // The milestone has only one task — completing it should complete the milestone
        Roadmap updated = roadmapService.toggleCompletionTask(roadmapId, taskId);
 
        assertEquals(Status.COMPLETED, updated.getMilestones().get(0).getStatus());
 
        Roadmap reloaded = roadmapService.getRoadmap(roadmapId);
        assertEquals(Status.COMPLETED, reloaded.getMilestones().get(0).getStatus());
    }

    /**
     * Verifies that when a completed task is toggled back to incomplete,
     * the milestone's status is not COMPLETED anymore.
     */
    @Test
    void toggleCompletionTask_revertsMilestoneStatus_whenTaskUncompleted() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
        Long roadmapId = roadmap.getRoadmap_id();
        Long taskId = roadmap.getMilestones().get(0).getTasks().get(0).getTask_id();
 
        roadmapService.toggleCompletionTask(roadmapId, taskId); // COMPLETED
        Roadmap updated = roadmapService.toggleCompletionTask(roadmapId, taskId); // back
 
        assertNotEquals(Status.COMPLETED, updated.getMilestones().get(0).getStatus());
    }
 
    /**
     * Verifies that completing some but not all tasks keeps the milestone
     * status as IN_PROGRESS.
     */
    @Test
    void toggleCompletionTask_doesNotCompleteMilestone_whenSomeTasksRemain() {
        stubUserServiceSuccess(1L);
        stubLlmServiceWithTwoTasks();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
        Long roadmapId = roadmap.getRoadmap_id();
        Long firstTaskId = roadmap.getMilestones().get(0).getTasks().get(0).getTask_id();
 
        // Complete only the first of two tasks
        Roadmap updated = roadmapService.toggleCompletionTask(roadmapId, firstTaskId);
 
        assertEquals(Status.IN_PROGRESS, updated.getMilestones().get(0).getStatus());
    }

    /**
     * Verifies that a 404 is thrown when the task ID does not belong to the roadmap.
     */
    @Test
    void toggleCompletionTask_throws404_whenTaskNotFound() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
 
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.toggleCompletionTask(roadmap.getRoadmap_id(), 999999L));
 
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
 
    /**
     * Verifies that a 404 is thrown when the roadmap itself does not exist.
     */
    @Test
    void toggleCompletionTask_throws404_whenRoadmapNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.toggleCompletionTask(999999L, 1L));
 
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ---------------------------------------------------------------------------
    // getProgress
    // ---------------------------------------------------------------------------
 
    /**
     * Verifies that completing all tasks is reflected in getProgress,
     * including roadmapCompleted=true.
     */
    @Test
    void getProgress_returnsFullCompletion_afterAllTasksToggled() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
        Long roadmapId = roadmap.getRoadmap_id();
        Long taskId = roadmap.getMilestones().get(0).getTasks().get(0).getTask_id();
 
        roadmapService.toggleCompletionTask(roadmapId, taskId);
 
        RoadmapService.RoadmapProgress progress = roadmapService.getProgress(roadmapId);
 
        assertEquals(1, progress.completedTasks());
        assertEquals(1, progress.totalTasks());
        assertEquals(1, progress.completedMilestones());
        assertEquals(1, progress.totalMilestones());
        assertTrue(progress.roadmapCompleted());
    }
 
    /**
     * Verifies partial progress when only some tasks have been completed.
     */
    @Test
    void getProgress_returnsPartialProgress_afterSomeTasksToggled() {
        stubUserServiceSuccess(1L);
        stubLlmServiceWithTwoTasks();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
        Long roadmapId = roadmap.getRoadmap_id();
        Long firstTaskId = roadmap.getMilestones().get(0).getTasks().get(0).getTask_id();
 
        roadmapService.toggleCompletionTask(roadmapId, firstTaskId);
 
        RoadmapService.RoadmapProgress progress = roadmapService.getProgress(roadmapId);
 
        assertEquals(1, progress.completedTasks());
        assertEquals(2, progress.totalTasks());
        assertEquals(0, progress.completedMilestones());
        assertFalse(progress.roadmapCompleted());
    }
 
    /**
     * Verifies that progress reverts correctly after un-completing a task.
     */
    @Test
    void getProgress_reflectsRevertedProgress_afterTaskToggledBack() {
        stubUserServiceSuccess(1L);
        stubLlmServiceSuccess();
 
        Roadmap roadmap = roadmapService.generateRoadmap(1L, "Learn ML");
        Long roadmapId = roadmap.getRoadmap_id();
        Long taskId = roadmap.getMilestones().get(0).getTasks().get(0).getTask_id();
 
        roadmapService.toggleCompletionTask(roadmapId, taskId); // complete
        roadmapService.toggleCompletionTask(roadmapId, taskId); // revert
 
        RoadmapService.RoadmapProgress progress = roadmapService.getProgress(roadmapId);
 
        assertEquals(0, progress.completedTasks());
        assertFalse(progress.roadmapCompleted());
    }
 
    /**
     * Verifies that getProgress throws 404 for a non-existent roadmap.
     */
    @Test
    void getProgress_throws404_whenRoadmapNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roadmapService.getProgress(999999L));
 
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
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

    private void stubLlmServiceWithTwoTasks() {
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
                                        {"title": "Study linear algebra", "completed": false},
                                        {"title": "Study statistics", "completed": false}
                                      ]
                                    }
                                  ]
                                }
                                """)));
    }

}
