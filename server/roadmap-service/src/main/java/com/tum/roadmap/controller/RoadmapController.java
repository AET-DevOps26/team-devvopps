package com.tum.roadmap.controller;

import com.tum.roadmap.service.RoadmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roadmaps")
@RequiredArgsConstructor
public class RoadmapController {
    
    private final RoadmapService roadmapService;

    // add endpoints
}