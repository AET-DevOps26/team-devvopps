package com.tum.roadmap.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Goal entity representing a student's academic or career objective input to the system.
 * Each goal is uniquely tied to exactly one roadmap.
 */
@Entity
@Table(name = "goals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Goal {

    /** Unique identifier for the goal (auto-generated) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goal_id;

    /** Description of the goal in detail (required) */
    @Column(nullable = false)
    private String description;

    /** Timestamp when the goal was created (required) */
    @Column(nullable = false)
    private LocalDateTime created_date;
}

