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

    /** Returns the authenticated user's own roadmaps, newest first. */
    @GetMapping
    public ResponseEntity<List<Roadmap>> getMyRoadmaps(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(roadmapService.getRoadmapsForUser(userId));
    }

    /** Returns every roadmap (admin only — enforced at the gateway). */
    @GetMapping("/all")
    public ResponseEntity<List<Roadmap>> getAllRoadmaps() {
        return ResponseEntity.ok(roadmapService.getAllRoadmaps());
    }

    @PostMapping("/generate")
    public ResponseEntity<Roadmap> generateRoadmap(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam String goal) {
        // userId comes from the gateway-injected, JWT-verified X-User-Id header.
        return ResponseEntity.ok(roadmapService.generateRoadmap(userId, goal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Roadmap> getRoadmap(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(roadmapService.getRoadmapForUser(id, userId));
    }

    @PatchMapping("/{roadmapId}/tasks/{taskId}/complete")
    public ResponseEntity<Roadmap> toggleCompletionTask(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long roadmapId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(roadmapService.toggleCompletionTask(roadmapId, taskId, userId));
    }

    @GetMapping("/{roadmapId}/progress")
    public ResponseEntity<RoadmapService.RoadmapProgress> getProgress(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(roadmapService.getProgress(roadmapId));
    }
}
