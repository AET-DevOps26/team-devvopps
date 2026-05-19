package com.tum.roadmap.model;

/**
 * Status enum representing the completion state of milestones.
 * Used to track progress through a roadmap.
 */
public enum Status {
    /** No task in the milestone has been completed */
    NOT_STARTED,

    /** At least one task in the milestone has been completed */
    IN_PROGRESS,

    /** All tasks in the milestone have been completed */
    COMPLETED
}

