package com.tum.roadmap.dto;
 
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
 
public record RoadmapResponse(
        @JsonProperty("milestones") List<MilestoneDto> milestones
) {}