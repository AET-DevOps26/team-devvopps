package com.tum.roadmap.dto;

import java.util.List;

public record MilestoneDto(
        String title,
        String description,
        List<TaskDto> tasks
) {}
