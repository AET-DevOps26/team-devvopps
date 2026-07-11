package com.tum.roadmap.service;

import com.tum.roadmap.dto.*;
import com.tum.roadmap.model.Milestone;
import com.tum.roadmap.model.Roadmap;
import com.tum.roadmap.model.Status;
import com.tum.roadmap.model.Task;
import com.tum.roadmap.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    // ---------------------------------------------------------------------------
    // generateRoadmap
    // ---------------------------------------------------------------------------

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
     * Verifies that a 429 from the LLM service propagates as TOO_MANY_REQUESTS.
     */
    @Test
    void generateRoadmap_throwsTooManyRequests_whenLlmReturns429() {
        when(restTemplate.getForObject(anyString(), eq(Object.class)))
                .thenReturn(new Object());
        when(restTemplate.postForObject(anyString(), any(), eq(RoadmapResponse.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null));
 
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateRoadmap(1L, "ML"));
 
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    /**
     * Verifies that a null LLM response (no milestones) throws 502 BAD_GATEWAY
     * rather than saving an empty roadmap.
     */
    @Test
    void generateRoadmap_throws502_whenLlmReturnsNull() {
        when(restTemplate.getForObject(anyString(), eq(Object.class)))
                .thenReturn(new Object());
        when(restTemplate.postForObject(anyString(), any(), eq(RoadmapResponse.class)))
                .thenReturn(null);
        when(goalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
 
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateRoadmap(1L, "ML"));
 
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    /**
     * Verifies that milestones returned by the LLM without any tasks are silently
     * skipped, and if that leaves zero milestones, a 502 is thrown.
     */
    @Test
    void generateRoadmap_throws502_whenAllMilestonesHaveNoTasks() {
        MilestoneDto emptyMilestone = new MilestoneDto("Empty", "No tasks", List.of());
        RoadmapResponse response = new RoadmapResponse(List.of(emptyMilestone));
 
        when(restTemplate.getForObject(anyString(), eq(Object.class)))
                .thenReturn(new Object());
        when(restTemplate.postForObject(anyString(), any(), eq(RoadmapResponse.class)))
                .thenReturn(response);
        when(goalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
 
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateRoadmap(1L, "ML"));
 
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    
    // ---------------------------------------------------------------------------
    // getRoadmap
    // ---------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------
    // toggleCompletionTask
    // ---------------------------------------------------------------------------

    /**
     * Verifies that toggling an incomplete task marks it as completed,
     * and the updated roadmap is saved.
     */
    @Test
    void toggleCompletionTask_marksTaskComplete_whenWasIncomplete() {
        Roadmap roadmap = buildRoadmapWithOneTask(false);
        Long roadmapId = 1L;
        Long taskId = 10L;
 
        when(roadmapRepository.findById(roadmapId)).thenReturn(Optional.of(roadmap));
        when(roadmapRepository.save(any())).thenAnswer(i -> i.getArgument(0));
 
        Roadmap result = service.toggleCompletionTask(roadmapId, taskId);
 
        Task task = result.getMilestones().get(0).getTasks().get(0);
        assertTrue(task.isCompleted());
        verify(roadmapRepository).save(roadmap);
    }

    /**
     * Verifies that toggling a completed task marks it as incomplete.
     */
    @Test
    void toggleCompletionTask_marksTaskIncomplete_whenWasComplete() {
        Roadmap roadmap = buildRoadmapWithOneTask(true);
        Long roadmapId = 1L;
        Long taskId = 10L;
 
        when(roadmapRepository.findById(roadmapId)).thenReturn(Optional.of(roadmap));
        when(roadmapRepository.save(any())).thenAnswer(i -> i.getArgument(0));
 
        Roadmap result = service.toggleCompletionTask(roadmapId, taskId);
 
        Task task = result.getMilestones().get(0).getTasks().get(0);
        assertFalse(task.isCompleted());
    }

    /**
     * Verifies that a 404 is thrown when the taskId does not exist in the roadmap.
     */
    @Test
    void toggleCompletionTask_throws404_whenTaskNotFound() {
        Roadmap roadmap = buildRoadmapWithOneTask(false);
 
        when(roadmapRepository.findById(1L)).thenReturn(Optional.of(roadmap));
 
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.toggleCompletionTask(1L, 999L));
 
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    /**
     * Verifies that a 404 is thrown when the roadmap itself does not exist.
     */
    @Test
    void toggleCompletionTask_throws404_whenRoadmapNotFound() {
        when(roadmapRepository.findById(99L)).thenReturn(Optional.empty());
 
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.toggleCompletionTask(99L, 10L));
 
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ---------------------------------------------------------------------------
    // computeProgress
    // ---------------------------------------------------------------------------
 
    /**
     * Verifies progress when no tasks have been completed.
     */
    @Test
    void computeProgress_returnsZero_whenNoTasksCompleted() {
        Roadmap roadmap = buildRoadmapWithOneTask(false);
 
        RoadmapService.RoadmapProgress progress = service.computeProgress(roadmap);
 
        assertEquals(0, progress.completedTasks());
        assertEquals(1, progress.totalTasks());
        assertEquals(0, progress.completedMilestones());
        assertEquals(1, progress.totalMilestones());
        assertFalse(progress.roadmapCompleted());
    }
 
    /**
     * Verifies progress when all tasks in all milestones are completed.
     */
    @Test
    void computeProgress_returnsFullProgress_whenAllTasksCompleted() {
        Roadmap roadmap = buildRoadmapWithOneTask(true);
        // Manually drive milestone to COMPLETED to reflect what updateStatus() would do
        roadmap.getMilestones().get(0).setStatus(Status.COMPLETED);
 
        RoadmapService.RoadmapProgress progress = service.computeProgress(roadmap);
 
        assertEquals(1, progress.completedTasks());
        assertEquals(1, progress.totalTasks());
        assertEquals(1, progress.completedMilestones());
        assertEquals(1, progress.totalMilestones());
        assertTrue(progress.roadmapCompleted());
    }
 
 
    /**
     * Verifies partial progress: some tasks done, milestone not yet completed.
     */
    @Test
    void computeProgress_returnsPartialProgress_whenSomeTasksCompleted() {
        Task t1 = new Task(); t1.setTask_id(1L); t1.setCompleted(true);
        Task t2 = new Task(); t2.setTask_id(2L); t2.setCompleted(false);
 
        Milestone milestone = new Milestone();
        milestone.setStatus(Status.IN_PROGRESS);
        List<Task> tasks = new ArrayList<>();
        tasks.add(t1);
        tasks.add(t2);
        milestone.setTasks(tasks);
        t1.setMilestone(milestone);
        t2.setMilestone(milestone);
 
        Roadmap roadmap = new Roadmap();
        roadmap.setMilestones(List.of(milestone));
 
        RoadmapService.RoadmapProgress progress = service.computeProgress(roadmap);
 
        assertEquals(1, progress.completedTasks());
        assertEquals(2, progress.totalTasks());
        assertEquals(0, progress.completedMilestones());
        assertEquals(1, progress.totalMilestones());
        assertFalse(progress.roadmapCompleted());
    }
 
    /**
     * Verifies progress across multiple milestones where only some are fully done.
     */
    @Test
    void computeProgress_countsAcrossMultipleMilestones() {
        Milestone m1 = buildMilestone(Status.COMPLETED, true, true);   // 2 tasks, both done
        Milestone m2 = buildMilestone(Status.NOT_STARTED, false, false); // 2 tasks, none done
 
        Roadmap roadmap = new Roadmap();
        roadmap.setMilestones(new ArrayList<>(List.of(m1, m2)));
 
        RoadmapService.RoadmapProgress progress = service.computeProgress(roadmap);
 
        assertEquals(2, progress.completedTasks());
        assertEquals(4, progress.totalTasks());
        assertEquals(1, progress.completedMilestones());
        assertEquals(2, progress.totalMilestones());
        assertFalse(progress.roadmapCompleted());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------
 
    private Roadmap buildRoadmapWithOneTask(boolean taskCompleted) {
        Task task = new Task();
        task.setTask_id(10L);
        task.setCompleted(taskCompleted);
 
        Milestone milestone = new Milestone();
        milestone.setStatus(taskCompleted ? Status.COMPLETED : Status.NOT_STARTED);
        List<Task> tasks = new ArrayList<>();
        tasks.add(task);
        milestone.setTasks(tasks);
        task.setMilestone(milestone);
 
        Roadmap roadmap = new Roadmap();
        roadmap.setMilestones(new ArrayList<>(List.of(milestone)));
        milestone.setRoadmap(roadmap);
        return roadmap;
    }
 
    private Milestone buildMilestone(Status status, boolean... taskCompletions) {
        Milestone milestone = new Milestone();
        milestone.setStatus(status);
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < taskCompletions.length; i++) {
            Task t = new Task();
            t.setTask_id((long) (100 + i));
            t.setCompleted(taskCompletions[i]);
            t.setMilestone(milestone);
            tasks.add(t);
        }
        milestone.setTasks(tasks);
        return milestone;
    }
}