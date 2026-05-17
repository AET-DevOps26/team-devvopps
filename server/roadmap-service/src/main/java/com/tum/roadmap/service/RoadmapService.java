package com.tum.roadmap.service;

import com.tum.roadmap.model.*;
import com.tum.roadmap.repository.GoalRepository;
import com.tum.roadmap.repository.RoadmapRepository;

import lombok.AllArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * Service layer for Roadmap-related business logic.
 */
@Service
@AllArgsConstructor
public class RoadmapService {
    
    private final RoadmapRepository roadmapRepository;
    private final GoalRepository goalRepository;
    private final RestTemplate restTemplate;

    // External microservice URLs
    private final String USER_URL = "http://localhost:8081/users/";

    /**
     * Calls user-service to verify that the user exists.
     */
    private Object getUser(Long userId) {
        try {
            return restTemplate.getForObject(USER_URL + userId, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("User service not reachable");
        }
    }

    /**
      * Generates a roadmap from a user request.
      */
    public Roadmap generateRoadmap(Long userId, String user_goal) {

        // Verify user exists via user-service
        Object user = getUser(userId);

        Roadmap roadmap = new Roadmap();

        Goal goal = new Goal();
        goal.setCreated_date(LocalDateTime.now());
        goal.setDescription(user_goal);
        goalRepository.save(goal);

        roadmap.setGoal(goal);
        roadmap.setCreated_date(LocalDateTime.now());

        /*
         * FUTURE AI SERVICE COMMUNICATION
         *
         * The roadmap-service should send the user's goal to the AI microservice.
         * Example: POST http://localhost:8084/ai/generate
         *
         * Request body:
         * {
         *   "goal": "Learn Machine Learning"
         * }
         *
         * The AI service should:
         *
         * 1. Extract keywords from the goal
         *    Example:
         *    ["machine learning", "python", "statistics"]
         *
         * 2. Search matching courses from course-service
         *
         * 3. Generate milestones and tasks
         *
         * 4. Return structured roadmap data
         *
         * Example response:
         * {
         *   "milestones": [...],
         *   "tasks": [...],
         *   "recommendedCourses": [...]
         * }
         *
         */

        /*
         * TODO:
         * Add generated milestones
         *
         * roadmap.setMilestones(...)
         */

        /*
         * TODO:
         * Add generated tasks
         */

        /*
         * TODO:
         * Add recommended courses 
         */

        return roadmapRepository.save(roadmap);
    }
    
    /**
     * Returns roadmap by ID.
     */
    public Roadmap getRoadmap(Long id) {

        return roadmapRepository.findById(id).orElseThrow(() -> new RuntimeException("Roadmap not found"));
    }
}
