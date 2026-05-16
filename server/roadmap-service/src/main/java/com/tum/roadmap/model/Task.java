package com.tum.roadmap.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Task entity representing an individual action item within a milestone.
 * Tasks are the smallest unit of work in a roadmap.
 * Students mark tasks as completed to track progress through milestones.
 */
@Entity
@Table(name = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    /** Unique identifier for the task (auto-generated) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long task_id;

    /** The milestone this task belongs to (required) */
    @ManyToOne
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    /** Task title/description (required) */
    @Column(nullable = false)
    private String title;

    /** Whether this task has been completed by the student (required) */
    @Column(nullable = false)
    private boolean completed;
}


