package com.tum.roadmap.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roadmaps")
public class RoadmapController {

    @GetMapping("/test")
    public String test() {
        return "Backend is running!";
    }
}
