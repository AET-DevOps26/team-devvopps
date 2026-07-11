package com.tum.roadmap.controller;

import com.tum.roadmap.model.Roadmap;
import com.tum.roadmap.service.RoadmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roadmaps")
@RequiredArgsConstructor
@CrossOrigin
public class RoadmapController {

    private final RoadmapService roadmapService;

    /**
     * Retrieves all available roadmaps.
     *
     * @return a list of all roadmaps
     */
    @GetMapping
    public ResponseEntity<List<Roadmap>> getAllRoadmaps() {
        return ResponseEntity.ok(roadmapService.getAllRoadmaps());
    }

    /**
     * Generates a new roadmap for a user based on a given goal.
     * The request is forwarded to the roadmap service, which verifies the user
     * and uses the LLM service to generate milestones and tasks.
     *
     * @param userId the ID of the user requesting the roadmap
     * @param goal the goal description used to generate the roadmap
     * @return the generated roadmap
     */
    @PostMapping("/generate")
    public ResponseEntity<Roadmap> generateRoadmap(
            @RequestParam(name = "userId", defaultValue = "1") Long userId,
            @RequestParam(name = "goal") String goal) {
        return ResponseEntity.ok(roadmapService.generateRoadmap(userId, goal));
    }

    /**
     * Retrieves a roadmap by its ID.
     *
     * @param id the roadmap identifier
     * @return the requested roadmap
     */
    @GetMapping("/{id}")
    public ResponseEntity<Roadmap> getRoadmap(@PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(roadmapService.getRoadmap(id));
    }

    /**
     * Toggles the completion state of a task inside a roadmap.
     *
     * @param roadmapId the ID of the roadmap containing the task
     * @param taskId the ID of the task to update
     * @return the updated roadmap
     */
    @PatchMapping("/{roadmapId}/tasks/{taskId}/complete")
    public ResponseEntity<Roadmap> toggleCompletionTask(
            @PathVariable(name = "roadmapId") Long roadmapId,
            @PathVariable(name = "taskId") Long taskId) {
        return ResponseEntity.ok(roadmapService.toggleCompletionTask(roadmapId, taskId));
    }

    /**
     * Calculates and returns the current progress of a roadmap.
     *
     * @param roadmapId the roadmap identifier
     * @return progress information including completed milestones and tasks
     */
    @GetMapping("/{roadmapId}/progress")
    public ResponseEntity<RoadmapService.RoadmapProgress> getProgress(@PathVariable(name = "roadmapId") Long roadmapId) {
        return ResponseEntity.ok(roadmapService.getProgress(roadmapId));
    }
}
