package com.tum.roadmap.controller;

import com.tum.roadmap.model.Roadmap;
import com.tum.roadmap.service.RoadmapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

/**
 * Unit tests for RoadmapController.
 *
 * Uses @WebMvcTest to load only the MVC layer.
 * RoadmapService is mocked to isolate controller behavior.
 */
@WebMvcTest(RoadmapController.class)
class RoadmapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoadmapService service;

    // ---------------------------------------------------------------------------
    // GET /roadmaps
    // ---------------------------------------------------------------------------

    /**
     * Verifies that GET /roadmaps returns 200 with an empty list when no roadmaps exist.
     */
    @Test
    void getAllRoadmaps_returns200_withEmptyList() throws Exception {
        when(service.getAllRoadmaps()).thenReturn(List.of());
 
        mockMvc.perform(get("/roadmaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
 
    /**
     * Verifies that GET /roadmaps returns 200 with all roadmaps in the list.
     */
    @Test
    void getAllRoadmaps_returns200_withRoadmaps() throws Exception {
        when(service.getAllRoadmaps()).thenReturn(List.of(new Roadmap(), new Roadmap()));
 
        mockMvc.perform(get("/roadmaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
    
    // ---------------------------------------------------------------------------
    // POST /roadmaps/generate
    // ---------------------------------------------------------------------------

    /**
     * Verifies that POST /roadmaps/generate returns 200 when both params are provided
     * and the service succeeds.
     */
    @Test
    void generateRoadmap_returns200() throws Exception {
        Roadmap r = new Roadmap();

        when(service.generateRoadmap(1L, "ML")).thenReturn(r);

        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "1")
                        .param("goal", "ML"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that POST /roadmaps/generate returns 400 when all params are missing.
     */
    @Test
    void generateRoadmap_returns400_whenParamsMissing() throws Exception {
        // Missing required params causes a 400
        mockMvc.perform(post("/roadmaps/generate"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that POST /roadmaps/generate returns 400 when goal is missing.
     */
    @Test
    void generateRoadmap_returns400_whenGoalMissing() throws Exception {
        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "1"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that POST /roadmaps/generate returns 503 when the LLM or user service
     * is unreachable.
     */
    @Test
    void generateRoadmap_returns503_whenServiceUnavailable() throws Exception {
        when(service.generateRoadmap(1L, "ML"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LLM error"));

        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "1")
                        .param("goal", "ML"))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * Verifies that POST /roadmaps/generate returns 404 when the user is not found.
     */
    @Test
    void generateRoadmap_returns404_whenUserNotFound() throws Exception {
        when(service.generateRoadmap(99L, "ML"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
 
        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "99")
                        .param("goal", "ML"))
                .andExpect(status().isNotFound());
    }
 
    /**
     * Verifies that POST /roadmaps/generate returns 502 when the LLM returns no milestones.
     */
    @Test
    void generateRoadmap_returns502_whenLlmReturnsNoMilestones() throws Exception {
        when(service.generateRoadmap(1L, "ML"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM returned no milestones"));
 
        mockMvc.perform(post("/roadmaps/generate")
                        .param("userId", "1")
                        .param("goal", "ML"))
                .andExpect(status().isBadGateway());
    }

    // ---------------------------------------------------------------------------
    // GET /roadmaps/{id}
    // ---------------------------------------------------------------------------

     /**
     * Verifies that GET /roadmaps/{id} returns 200 when the roadmap exists.
     */
    @Test
    void getRoadmap_returns200() throws Exception {
        Roadmap r = new Roadmap();

        when(service.getRoadmap(1L)).thenReturn(r);

        mockMvc.perform(get("/roadmaps/1"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that GET /roadmaps/{id} returns 400 when the ID is not a valid number.
     */
    @Test
    void getRoadmap_returns400_whenIdNotANumber() throws Exception {
        mockMvc.perform(get("/roadmaps/abc"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that GET /roadmaps/{id} returns 404 when no roadmap exists with that ID.
     */
    @Test
    void getRoadmap_returns404_whenNotFound() throws Exception {
        when(service.getRoadmap(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap not found"));

        mockMvc.perform(get("/roadmaps/99"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------
    // PATCH /roadmaps/{roadmapId}/tasks/{taskId}/complete
    // ---------------------------------------------------------------------------

    /**
     * Verifies that PATCH .../complete returns 200 and the updated roadmap
     * when the task and roadmap both exist.
     */
    @Test
    void toggleCompletionTask_returns200() throws Exception {
        when(service.toggleCompletionTask(1L, 10L)).thenReturn(new Roadmap());
 
        mockMvc.perform(patch("/roadmaps/1/tasks/10/complete"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that PATCH .../complete returns 404 when the roadmap does not exist.
     */
    @Test
    void toggleCompletionTask_returns404_whenRoadmapNotFound() throws Exception {
        when(service.toggleCompletionTask(99L, 10L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap not found"));
 
        mockMvc.perform(patch("/roadmaps/99/tasks/10/complete"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that PATCH .../complete returns 404 when the task does not belong
     * to the specified roadmap.
     */
    @Test
    void toggleCompletionTask_returns404_whenTaskNotFound() throws Exception {
        when(service.toggleCompletionTask(1L, 999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Task 999 not found in roadmap 1"));
 
        mockMvc.perform(patch("/roadmaps/1/tasks/999/complete"))
                .andExpect(status().isNotFound());
    }
 
    /**
     * Verifies that PATCH .../complete returns 400 when the roadmap ID is not a number.
     */
    @Test
    void toggleCompletionTask_returns400_whenRoadmapIdNotANumber() throws Exception {
        mockMvc.perform(patch("/roadmaps/abc/tasks/10/complete"))
                .andExpect(status().isBadRequest());
    }
 
    /**
     * Verifies that PATCH .../complete returns 400 when the task ID is not a number.
     */
    @Test
    void toggleCompletionTask_returns400_whenTaskIdNotANumber() throws Exception {
        mockMvc.perform(patch("/roadmaps/1/tasks/abc/complete"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // GET /roadmaps/{roadmapId}/progress
    // ---------------------------------------------------------------------------
 
    /**
     * Verifies that GET .../progress returns 200 with the correct structure.
     */
    @Test
    void getProgress_returns200() throws Exception {
        RoadmapService.RoadmapProgress progress =
                new RoadmapService.RoadmapProgress(1, 3, 5, 10, false);
 
        when(service.getProgress(1L)).thenReturn(progress);
 
        mockMvc.perform(get("/roadmaps/1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedMilestones").value(1))
                .andExpect(jsonPath("$.totalMilestones").value(3))
                .andExpect(jsonPath("$.completedTasks").value(5))
                .andExpect(jsonPath("$.totalTasks").value(10))
                .andExpect(jsonPath("$.roadmapCompleted").value(false));
    }
 
    /**
     * Verifies that GET .../progress returns 200 with roadmapCompleted=true
     * when all milestones are done.
     */
    @Test
    void getProgress_returns200_whenRoadmapCompleted() throws Exception {
        RoadmapService.RoadmapProgress progress =
                new RoadmapService.RoadmapProgress(3, 3, 10, 10, true);
 
        when(service.getProgress(1L)).thenReturn(progress);
 
        mockMvc.perform(get("/roadmaps/1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roadmapCompleted").value(true));
    }
 
    /**
     * Verifies that GET .../progress returns 404 when the roadmap does not exist.
     */
    @Test
    void getProgress_returns404_whenRoadmapNotFound() throws Exception {
        when(service.getProgress(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap not found"));
 
        mockMvc.perform(get("/roadmaps/99/progress"))
                .andExpect(status().isNotFound());
    }
 
    /**
     * Verifies that GET .../progress returns 400 when the ID is not a number.
     */
    @Test
    void getProgress_returns400_whenIdNotANumber() throws Exception {
        mockMvc.perform(get("/roadmaps/abc/progress"))
                .andExpect(status().isBadRequest());
    }
}