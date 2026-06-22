package com.tum.roadmap.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Roadmap entity representing a personalized learning path.
 * A roadmap is generated based on a student's goal and contains milestones with tasks.
 * Each roadmap belongs to exactly one user (identified by user_id) and one goal.
 * A roadmap must contain at least one milestone.
 *
 * Note: user_id is a simple foreign key reference to user-service.
 * User details are retrieved via API call to user-service when needed.
 */
@Entity
@Table(name = "roadmaps")
@Getter 
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "milestones")
@EqualsAndHashCode(exclude = "milestones")
public class Roadmap {

    /** Unique identifier for the roadmap (auto-generated) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roadmap_id;

    /** ID of the user who owns this roadmap (foreign key to user-service, required) */
    @Column(nullable = false)
    private Long user_id;

    /** The goal this roadmap is designed to achieve (required, one-to-one, unique) */
    @OneToOne
    @JoinColumn(name = "goal_id", nullable = false, unique = true)
    private Goal goal;

    /** Roadmap title/name (required) */
    @Column(nullable = false)
    private String title;

    /** When the roadmap was created (required) */
    @Column(nullable = false)
    private LocalDateTime created_date;

    /** Progress percentage (0-100, required) */
    @Column(nullable = false)
    private int progress;

    /** List of milestones in this roadmap (must have at least 1) */
    @OneToMany(mappedBy = "roadmap", cascade = CascadeType.ALL)
    private List<Milestone> milestones = new ArrayList<>();

    /**
     * Validates that roadmap has at least one milestone before persisting.
     * Called automatically before save/update operations.
     */
    @PrePersist
    @PreUpdate
    private void validateMilestones() {
        if (milestones == null || milestones.isEmpty()) {
            throw new IllegalArgumentException("Roadmap must have at least 1 milestone");
        }
    }

    /**
     * Calculate overall progress percentage based on completed tasks.
     * Progress = (completed tasks across all milestones / total tasks) * 100
     * This gives a granular view of progress at the task level, not milestone level.
     */
    public void calculateProgress() {
        if (milestones == null || milestones.isEmpty()) {
            progress = 0;
            return;
        }

        long totalTasks = 0;
        long completedTasks = 0;

        for (Milestone milestone : milestones) {
            if (milestone.getTasks() != null) {
                totalTasks += milestone.getTasks().size();
                completedTasks += milestone.getTasks().stream()
                    .filter(Task::isCompleted)
                    .count();
            }
        }

        if (totalTasks == 0) {
            progress = 0;
        } else {
            progress = (int) ((completedTasks * 100) / totalTasks);
        }
    }
}


