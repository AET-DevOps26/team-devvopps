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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
     * Verifies that POST /roadmaps/generate returns 400 when all params are missing.
     */
    @Test
    void generateRoadmap_returns400_whenParamsMissing() throws Exception {
        // Missing required params causes a 400
        mockMvc.perform(post("/roadmaps/generate"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that POST /roadmaps/generate returns 400 when userId is missing.
     */
    @Test
    void generateRoadmap_returns400_whenUserIdMissing() throws Exception {
        mockMvc.perform(post("/roadmaps/generate")
                        .param("goal", "ML"))
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
}