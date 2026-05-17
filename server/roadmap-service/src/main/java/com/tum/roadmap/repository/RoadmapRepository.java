package com.tum.roadmap.repository;

import com.tum.roadmap.model.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for Roadmap entities.
 * 
 * JpaRepository automatically provides CRUD operations
 * for the Roadmap table.
 */
public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
}
