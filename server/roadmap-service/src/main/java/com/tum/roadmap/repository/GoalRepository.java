package com.tum.roadmap.repository;

import com.tum.roadmap.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for Goal entities.
 * 
 * JpaRepository automatically provides CRUD operations
 * for the Goal table.
 */
public interface GoalRepository extends JpaRepository<Goal, Long> {
}
