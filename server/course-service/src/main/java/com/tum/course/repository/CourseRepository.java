package com.tum.course.repository;

import com.tum.course.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for Course entities.
 * 
 * JpaRepository provides built-in CRUD operations.
 * 
 * Spring Data JPA automatically implements this interface at runtime.
 */
public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * Finds a course whose title contains the given text,
     * ignoring uppercase/lowercase differences.
     * 
     * Example:
     * Searching for "machine" could match
     * "Machine Learning".
     *
     * @param title part of the course title
     * @return matching Course object
     */
    Course findByTitleContainingIgnoreCase(String title);
}