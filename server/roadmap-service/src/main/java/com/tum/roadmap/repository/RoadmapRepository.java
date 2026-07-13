package com.tum.roadmap.repository;

import com.tum.roadmap.model.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository interface for Roadmap entities.
 *
 * JpaRepository automatically provides CRUD operations for the Roadmap table.
 */
public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {

    /**
     * Returns a user's roadmaps, newest first. Uses an explicit JPQL query rather
     * than a derived method name because the snake_case {@code user_id} property
     * would otherwise be parsed as a {@code user.id} path and fail at startup.
     */
    @Query("SELECT r FROM Roadmap r WHERE r.user_id = :userId ORDER BY r.created_date DESC")
    List<Roadmap> findByUserIdNewestFirst(@Param("userId") Long userId);
}
