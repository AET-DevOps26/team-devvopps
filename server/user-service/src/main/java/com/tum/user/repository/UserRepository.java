package com.tum.user.repository;

import com.tum.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for User entities.
 * 
 * JpaRepository automatically provides CRUD operations
 * for the User table.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}