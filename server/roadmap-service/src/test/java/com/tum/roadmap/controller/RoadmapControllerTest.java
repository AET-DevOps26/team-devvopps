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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

 /**
  * Unit tests for RoadmapController.
  *
  * Uses @WebMvcTest to test only the web layer without loading the full Spring context.
  * The RoadmapService dependency is mocked using MockitoBean.
  *
  * These tests verify:
  * - HTTP endpoints return the correct status codes for successful requests
  * - Missing required headers and parameters are handled with 400 BAD_REQUEST
  * - Controller correctly delegates requests to RoadmapService
  * - Service exceptions (e.g. 404 NOT_FOUND) are correctly translated into HTTP responses
  * - Request parameters and headers are correctly passed to the service layer
  * - JSON responses contain the expected data
  */
@WebMvcTest(RoadmapController.class)
class RoadmapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoadmapService service;


    /**
     * Verifies that authenticated users can retrieve their own roadmaps.
     */
    @Test
    void getMyRoadmaps_returns200() throws Exception {

        when(service.getRoadmapsForUser(1L))
                .thenReturn(List.of(new Roadmap()));

        mockMvc.perform(get("/roadmaps")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }


    /**
     * Verifies that the request is rejected when the user ID header is missing.
     */
    @Test
    void getMyRoadmaps_returns400_withoutHeader() throws Exception {

        mockMvc.perform(get("/roadmaps"))
                .andExpect(status().isBadRequest());
    }


    /**
     * Verifies that all roadmaps can be retrieved successfully.
     */
    @Test
    void getAllRoadmaps_returns200() throws Exception {

        when(service.getAllRoadmaps())
                .thenReturn(List.of());

        mockMvc.perform(get("/roadmaps/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }


    /**
     * Verifies that a roadmap can be generated when user ID and goal are provided.
     */
    @Test
    void generateRoadmap_returns200() throws Exception {

        when(service.generateRoadmap(1L, "ML"))
                .thenReturn(new Roadmap());

        mockMvc.perform(post("/roadmaps/generate")
                        .header("X-User-Id", "1")
                        .param("goal", "ML"))
                .andExpect(status().isOk());
    }


    /**
     * Verifies that generating a roadmap fails when the goal parameter is missing.
     */
    @Test
    void generateRoadmap_returns400_withoutGoal() throws Exception {

        mockMvc.perform(post("/roadmaps/generate")
                        .header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());
    }


    /**
     * Verifies that generating a roadmap fails when the user ID header is missing.
     */
    @Test
    void generateRoadmap_returns400_withoutUserHeader() throws Exception {

        mockMvc.perform(post("/roadmaps/generate")
                        .param("goal", "ML"))
                .andExpect(status().isBadRequest());
    }


    /**
     * Verifies that a user can retrieve an existing roadmap they have access to.
     */
    @Test
    void getRoadmap_returns200() throws Exception {

        when(service.getRoadmapForUser(1L, 10L))
                .thenReturn(new Roadmap());

        mockMvc.perform(get("/roadmaps/1")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());
    }


    /**
     * Verifies that a missing roadmap results in a 404 response.
     */
    @Test
    void getRoadmap_returns404_whenNotFound() throws Exception {

        when(service.getRoadmapForUser(99L, 10L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Roadmap not found"
                ));

        mockMvc.perform(get("/roadmaps/99")
                        .header("X-User-Id", "10"))
                .andExpect(status().isNotFound());
    }


    /**
     * Verifies that completing a task successfully returns the updated roadmap.
     */
    @Test
    void toggleCompletionTask_returns200() throws Exception {

        when(service.toggleCompletionTask(1L, 10L, 5L))
                .thenReturn(new Roadmap());

        mockMvc.perform(
                patch("/roadmaps/1/tasks/10/complete")
                        .header("X-User-Id", "5")
        )
        .andExpect(status().isOk());
    }


    /**
     * Verifies that completing a task from a roadmap owned by another user returns 404.
     */
    @Test
    void toggleCompletionTask_returns404_whenUnauthorizedOwner() throws Exception {

        when(service.toggleCompletionTask(1L, 10L, 5L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND
                ));

        mockMvc.perform(
                patch("/roadmaps/1/tasks/10/complete")
                        .header("X-User-Id", "5")
        )
        .andExpect(status().isNotFound());
    }


    /**
     * Verifies that roadmap progress can be retrieved successfully.
     */
    @Test
    void getProgress_returns200() throws Exception {

        RoadmapService.RoadmapProgress progress =
                new RoadmapService.RoadmapProgress(
                        1,
                        2,
                        3,
                        5,
                        false
                );

        when(service.getProgress(1L))
                .thenReturn(progress);

        mockMvc.perform(get("/roadmaps/1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedTasks").value(3));
    }
}