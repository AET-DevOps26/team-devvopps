package com.tum.roadmap.dto;
 
import com.fasterxml.jackson.annotation.JsonProperty;
 
public record RoadmapRequest(
        @JsonProperty("goal") String goal
) {}
