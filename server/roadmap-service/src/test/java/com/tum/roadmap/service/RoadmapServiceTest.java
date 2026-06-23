package com.tum.roadmap.service;

import com.tum.roadmap.dto.*;
import com.tum.roadmap.model.Roadmap;
import com.tum.roadmap.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoadmapService.
 *
 * RestTemplate is mocked to simulate calls to the user-service and LLM service
 * without making real HTTP requests. Repositories are mocked to avoid database access.
 */
@ExtendWith(MockitoExtension.class)
class RoadmapServiceTest {

    @Mock RoadmapRepository roadmapRepository;
    @Mock GoalRepository goalRepository;
    @Mock RestTemplate restTemplate;

    @InjectMocks RoadmapService service;

    /**
     * User exists, LLM returns a valid response,
     * roadmap is built and saved successfully.
     */
    @Test
    void generateRoadmap_success() {
        TaskDto task = new TaskDto("Learn basics", false);
        MilestoneDto milestone = new MilestoneDto("Start", "Intro", List.of(task));
        RoadmapResponse response = new RoadmapResponse(List.of(milestone));

        when(restTemplate.getForObject(anyString(), eq(Object.class)))
                .thenReturn(new Object());

        when(restTemplate.postForObject(anyString(), any(), eq(RoadmapResponse.class)))
                .thenReturn(response);

        when(roadmapRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(goalRepository.save(any())).thenAnswer(i -> i.getArgument(0));  

        Roadmap result = service.generateRoadmap(1L, "ML");

        assertNotNull(result);
        assertEquals(1, result.getMilestones().size());
    }

    /**
     * Verifies that a roadmap can be retrieved by ID.
     */
    @Test
    void getRoadmap_returnsRoadmap() {
        Roadmap r = new Roadmap();
        when(roadmapRepository.findById(1L)).thenReturn(java.util.Optional.of(r));

        assertEquals(r, service.getRoadmap(1L));
    }

    /**
     * Verifies that a 404 is thrown when the requested roadmap does not exist.
     */
    @Test
    void getRoadmap_throwsIfMissing() {
        when(roadmapRepository.findById(1L)).thenReturn(java.util.Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getRoadmap(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Roadmap not found", ex.getReason());
    }

    /**
     * Verifies that a 503 is thrown when the user-service is unreachable.
     */
    @Test
    void generateRoadmap_throwsServiceUnavailable_whenUserServiceDown() {
        when(restTemplate.getForObject(anyString(), eq(Object.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateRoadmap(1L, "ML"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    /**
     * Verifies that a 404 is thrown when the user-service reports the user does not exist.
     */
    @Test
    void generateRoadmap_throwsNotFound_whenUserDoesNotExist() {
        when(restTemplate.getForObject(anyString(), eq(Object.class)))
                .thenThrow(HttpClientErrorException.NotFound.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateRoadmap(1L, "ML"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    /**
     * Verifies that a 503 is thrown when the LLM service is unreachable,
     * even if the user-service call succeeds.
     */
    @Test
    void generateRoadmap_throwsServiceUnavailable_whenLlmServiceDown() {
        when(restTemplate.getForObject(anyString(), eq(Object.class)))
                .thenReturn(new Object()); 

        when(restTemplate.postForObject(anyString(), any(), eq(RoadmapResponse.class)))
                .thenThrow(new RuntimeException("Connection refused")); // LLM fails

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.generateRoadmap(1L, "ML")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    /**
     * Verifies that a null LLM response is handled gracefully,
     * resulting in a roadmap with no milestones rather than a crash.
     */
    @Test
    void generateRoadmap_handlesNullLlmResponse() {
        when(restTemplate.getForObject(anyString(), eq(Object.class)))
                .thenReturn(new Object());

        when(restTemplate.postForObject(anyString(), any(), eq(RoadmapResponse.class)))
                .thenReturn(null); // LLM returns null

        when(roadmapRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(goalRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Roadmap result = service.generateRoadmap(1L, "ML");

        assertNotNull(result);
        assertTrue(result.getMilestones().isEmpty());
    }
}