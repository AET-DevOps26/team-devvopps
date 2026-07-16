package com.tum.roadmap.service;

import com.tum.roadmap.dto.MilestoneDto;
import com.tum.roadmap.dto.RoadmapRequest;
import com.tum.roadmap.dto.RoadmapResponse;
import com.tum.roadmap.dto.TaskDto;
import com.tum.roadmap.model.*;
import com.tum.roadmap.repository.GoalRepository;
import com.tum.roadmap.repository.RoadmapRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service layer for Roadmap-related business logic.
 */
@Service
@RequiredArgsConstructor
public class RoadmapService {

    private static final Logger log = LoggerFactory.getLogger(RoadmapService.class);

    private final RoadmapRepository roadmapRepository;
    private final GoalRepository goalRepository;
    private final RestTemplate restTemplate;

    @Value("${llm.service.host:llm-service}")
    private String llmHost;

    @Value("${llm.service.port:8084}")
    private String llmPort;

    @Value("${user.service.host:user-service}")
    private String userHost;

    @Value("${user.service.port:8081}")
    private String userPort;
    

    private String getUserUrl(Long userId) {
        return "http://" + userHost + ":" + userPort + "/users/" + userId;
    }

    private String getLlmUrl() {
        return "http://" + llmHost + ":" + llmPort;
    }

    /**
     * Calls user-service to verify that the user exists.
     */
    private Object getUser(Long userId) {
        try {
            return restTemplate.getForObject(getUserUrl(userId), Object.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + userId + " not found");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service not reachable");
        }
    }

    /**
     * Returns all roadmaps (admin only — enforced at the gateway).
     */
    public List<Roadmap> getAllRoadmaps() {
        return roadmapRepository.findAll();
    }

    /**
     * Returns the given user's roadmaps, newest first, with progress refreshed
     * so the list reflects current task completion.
     */
    public List<Roadmap> getRoadmapsForUser(Long userId) {
        List<Roadmap> roadmaps = roadmapRepository.findByUserIdNewestFirst(userId);
        roadmaps.forEach(Roadmap::calculateProgress);
        return roadmaps;
    }

    /**
     * Generates a roadmap from a user request.
     */
    public Roadmap generateRoadmap(Long userId, String user_goal) {
        log.info("[Roadmap] generate — userId={} goal='{}'", userId, user_goal);
        long t0 = System.currentTimeMillis();

        // Verify user exists via user-service
        Object user = getUser(userId);
        
        // Create Goal
        Goal goal = new Goal();
        goal.setCreated_date(LocalDateTime.now());
        goal.setDescription(user_goal);
        goalRepository.save(goal);

        // Create Roadmap
        Roadmap roadmap = new Roadmap();
        roadmap.setUser_id(userId);
        roadmap.setGoal(goal);
        roadmap.setTitle(user_goal);
        roadmap.setProgress(0);
        roadmap.setCreated_date(LocalDateTime.now());

        // Call LLM
        log.info("[LLM] Calling llm-service at {} for goal='{}'", getLlmUrl(), user_goal);
        long tLlm = System.currentTimeMillis();
        RoadmapResponse llmResponse = callLLM(user_goal, userId);
        log.info("[LLM] Response received in {}ms", System.currentTimeMillis() - tLlm);

        List<Milestone> milestones = new ArrayList<>();

        if (llmResponse != null && llmResponse.milestones() != null) {
            int index = 0;
            for (MilestoneDto m : llmResponse.milestones()) {
                // Milestone.validateTasks() rejects task-less milestones at persist
                // time, so saving one would fail the whole roadmap with a 500.
                if (m.tasks() == null || m.tasks().isEmpty()) {
                    log.warn("[LLM] Skipping milestone '{}' — no tasks returned", m.title());
                    continue;
                }
                Milestone milestone = new Milestone();
                milestone.setTitle(m.title());
                milestone.setDescription(m.description());
                milestone.setStatus(Status.NOT_STARTED);
                milestone.setOrderIndex(index++);
                milestone.setRoadmap(roadmap);

                List<Task> tasks = new ArrayList<>();
                
                if (m.tasks() != null) {
                    for (TaskDto t : m.tasks()) {
                        Task task = new Task();
                        task.setTitle(t.title());
                        task.setCompleted(false);
                        task.setMilestone(milestone);

                        tasks.add(task);
                    }
                }

                milestone.setTasks(tasks);
                milestones.add(milestone);
            }
        }

        if (milestones.isEmpty()) {
            log.error("[LLM] Returned no milestones for goal='{}' — not saving roadmap", user_goal);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "LLM returned no milestones — please try again");
        }

        roadmap.setMilestones(milestones);

        Roadmap saved = roadmapRepository.save(roadmap);
        log.info("[Roadmap] Saved roadmapId={} in {}ms — {} milestones",
                saved.getRoadmap_id(), System.currentTimeMillis() - t0, saved.getMilestones().size());
        return saved;
    }
    
    /**
     * Returns roadmap by ID.
     */
    public Roadmap getRoadmap(Long id) {
        return roadmapRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap not found"));
    }

    /**
     * Returns a roadmap only if it belongs to the given user; otherwise 404
     * (not 403, to avoid revealing that another user's roadmap exists).
     */
    public Roadmap getRoadmapForUser(Long id, Long userId) {
        Roadmap roadmap = getRoadmap(id);
        if (!roadmap.getUser_id().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap not found");
        }
        return roadmap;
    }

    @Transactional
    public Roadmap toggleCompletionTask(Long roadmapId, Long taskId, Long userId) {
        Roadmap roadmap = getRoadmapForUser(roadmapId, userId);

        Task task = roadmap.getMilestones().stream()
                .flatMap(m -> m.getTasks().stream())
                .filter(t -> t.getTask_id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Task " + taskId + " not found in roadmap " + roadmapId
                ));
        task.setCompleted(!task.isCompleted());

        Milestone milestone = task.getMilestone();
        milestone.updateStatus();

        // Keep the stored progress percentage in sync with task completion.
        roadmap.calculateProgress();

        return roadmapRepository.save(roadmap);
    }

    public RoadmapProgress getProgress(Long roadmapId) {
        Roadmap roadmap = getRoadmap(roadmapId);
        return computeProgress(roadmap);
    }

    public RoadmapProgress computeProgress(Roadmap roadmap) {
        List<Milestone> milestones = roadmap.getMilestones();
        if (milestones == null || milestones.isEmpty()) {
            return new RoadmapProgress(0, 0, 0, 0, false);
        }
        int totalMilestones = milestones.size();
        long completedMilestones = milestones.stream()
                .filter(m -> m.getStatus() == Status.COMPLETED)
                .count();
        
        int totalTasks = milestones.stream()
                .mapToInt(m -> m.getTasks() == null ? 0 : m.getTasks().size())
                .sum();
        long completedTasks = milestones.stream()
                .flatMap(m -> m.getTasks() == null ? java.util.stream.Stream.<Task>empty() : m.getTasks().stream())
                .filter(t -> t != null && t.isCompleted())
                .count();
        
        boolean roadmapCompleted = completedMilestones == totalMilestones;
        
        return new RoadmapProgress((int) completedMilestones, totalMilestones, (int) completedTasks, totalTasks, roadmapCompleted);
    }

    // Private Helper
    private RoadmapResponse callLLM(String goal, Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", String.valueOf(userId));
            HttpEntity<RoadmapRequest> entity = new HttpEntity<>(new RoadmapRequest(goal), headers);

            return restTemplate.exchange(
                    getLlmUrl() + "/recommend",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    RoadmapResponse.class
            ).getBody();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Token quota exceeded");
            }
            log.error("[LLM] HTTP error {}: {}", e.getStatusCode(), e.getMessage());
            throw new ResponseStatusException(e.getStatusCode(), "LLM service returned an error: " + e.getMessage());
        } catch (Exception e) {
            log.error("[LLM] Unreachable: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LLM service not reachable");
        }
    }

    public record RoadmapProgress(
        int completedMilestones,
        int totalMilestones,
        int completedTasks,
        long totalTasks,
        boolean roadmapCompleted
    ) {}
}