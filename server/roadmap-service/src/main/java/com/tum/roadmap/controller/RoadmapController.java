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
    
    // Service layer dependency
    private final RoadmapService roadmapService;

    /**
     * POST /roadmaps/generate
     *
     * Receives a user goal from the frontend and generates a personalized roadmap.
     */
    @PostMapping("/generate")
    public ResponseEntity<Roadmap> generateRoadmap(
            @RequestParam("userId") Long userId,
            @RequestParam("goal") String goal) {
        Roadmap roadmap = roadmapService.generateRoadmap(userId, goal);
        return ResponseEntity.ok(roadmap);
    }

    /**
     * GET /roadmaps/{id}
     *
     * Returns a roadmap by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Roadmap> getRoadmap(@PathVariable("id") Long id) {
        Roadmap roadmap = roadmapService.getRoadmap(id);
        return ResponseEntity.ok(roadmap);
    }
}