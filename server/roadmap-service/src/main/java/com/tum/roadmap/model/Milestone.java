package com.tum.roadmap.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Milestone entity representing a node in a roadmap.
 * Each milestone is either a recommended TUM course or a generic learning recommendation.
 * For tracebility, a milestone can be optionally linked to a specific course (via course_id) in case it was a TUM course recommendation.
 * A milestone must contain at least one task and belongs to exactly one roadmap.
 */
@Entity
@Table(name = "milestones")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Milestone {

    /** Unique identifier for the milestone (auto-generated) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long milestone_id;

    /** The roadmap this milestone belongs to (required) */
    @ManyToOne
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    /** Title of the milestone (required) */
    @Column(nullable = false)
    private String title;

    /** Optional detailed description of the milestone */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Current status of the milestone (required) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** Order index for displaying milestones in sequence (required) */
    @Column(nullable = false)
    private int orderIndex;

    /** Reference to a course (if this milestone is a TUM course recommendation, otherwise null) */
    private Long course_id;

    /** List of tasks in this milestone (must have at least 1) */
    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL)
    private List<Task> tasks = new ArrayList<>();

    /**
     * Validates that milestone has at least one task before persisting.
     * Called automatically before save/update operations.
     */
    @PrePersist
    @PreUpdate
    private void validateTasks() {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Milestone must have at least 1 task");
        }
    }

    /**
     * Update milestone status based on task completion of the milestone.
     */
    public void updateStatus() {
        if (tasks == null || tasks.isEmpty()) {
            status = Status.NOT_STARTED;
            return;
        }

        long completedTasks = tasks.stream()
            .filter(Task::isCompleted)
            .count();

        if (completedTasks == tasks.size()) {
            status = Status.COMPLETED;
        } else if (completedTasks > 0) {
            status = Status.IN_PROGRESS;
        } else {
            status = Status.NOT_STARTED;
        }
    }
}


