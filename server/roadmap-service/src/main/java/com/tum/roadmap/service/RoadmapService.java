package com.tum.roadmap.service;

import com.tum.roadmap.dto.MilestoneDto;
import com.tum.roadmap.dto.RoadmapRequest;
import com.tum.roadmap.dto.RoadmapResponse;
import com.tum.roadmap.dto.TaskDto;
import com.tum.roadmap.model.*;
import com.tum.roadmap.repository.GoalRepository;
import com.tum.roadmap.repository.RoadmapRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for Roadmap-related business logic.
 */
@Service
@RequiredArgsConstructor
public class RoadmapService {

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
      * Generates a roadmap from a user request.
      */
    public Roadmap generateRoadmap(Long userId, String user_goal) {

        // Verify user exists via user-service
        Object user = getUser(userId);
        
        // Create Goal
        Goal goal = new Goal();
        goal.setCreated_date(LocalDateTime.now());
        goal.setDescription(user_goal);
        goalRepository.save(goal);

        // Create Roadmap
        Roadmap roadmap = new Roadmap();
        roadmap.setGoal(goal);
        roadmap.setCreated_date(LocalDateTime.now());

        // Call LLM
        RoadmapResponse llmResponse = callLLM(user_goal);

        List<Milestone> milestones = new ArrayList<>();
        
        if (llmResponse != null && llmResponse.milestones() != null) {
            
            for (MilestoneDto m : llmResponse.milestones()) {
                Milestone milestone = new Milestone();
                milestone.setTitle(m.title());
                milestone.setDescription(m.description());
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

        roadmap.setMilestones(milestones);

        return roadmapRepository.save(roadmap);
    }
    
    /**
     * Returns roadmap by ID.
     */
    public Roadmap getRoadmap(Long id) {
        return roadmapRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap not found"));
    }

    // Private Helper
    private RoadmapResponse callLLM(String goal) {
        try {
            return restTemplate.postForObject(  // ← use injected restTemplate, not new RestTemplate()
                    getLlmUrl() + "/recommend",
                    new RoadmapRequest(goal),
                    RoadmapResponse.class
            );
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), "LLM service returned an error: " + e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LLM service not reachable");
        }
    }
}
