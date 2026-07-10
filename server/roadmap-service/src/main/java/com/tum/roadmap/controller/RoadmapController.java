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

    @GetMapping
    public ResponseEntity<List<Roadmap>> getAllRoadmaps() {
        return ResponseEntity.ok(roadmapService.getAllRoadmaps());
    }

    @PostMapping("/generate")
    public ResponseEntity<Roadmap> generateRoadmap(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam String goal) {
        return ResponseEntity.ok(roadmapService.generateRoadmap(userId, goal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Roadmap> getRoadmap(@PathVariable Long id) {
        return ResponseEntity.ok(roadmapService.getRoadmap(id));
    }

    @PatchMapping("/{roadmapId}/tasks/{taskId}/complete")
    public ResponseEntity<Roadmap> toggleCompletionTask(
            @PathVariable Long roadmapId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(roadmapService.toggleCompletionTask(roadmapId, taskId));
    }

    @GetMapping("/{roadmapId}/progress")
    public ResponseEntity<RoadmapService.RoadmapProgress> getProgress(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(roadmapService.getProgress(roadmapId));
    }
}
