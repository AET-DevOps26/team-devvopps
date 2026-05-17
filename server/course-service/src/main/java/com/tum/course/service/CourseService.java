package com.tum.course.service;

import com.tum.course.model.Course;
import com.tum.course.repository.CourseRepository;

import lombok.AllArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service layer for Course-related business logic.
 */
@Service
@AllArgsConstructor
public class CourseService {

    // Repository used for database operations
    private final CourseRepository repo;

    /**
     * Returns all courses from the database.
     *
     * @return list of all courses
     */
    public List<Course> getAllCourses() {
        return repo.findAll();
    }

    /**
     * Returns a single course by its ID.
     *
     * Throws an exception if no course exists with the given ID.
     *
     * @param id course ID
     * @return matching Course
     */
    public Course getCourse(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found"));
    }

    /**
     * Searches for a course by title.
     *
     * @param title course title or partial title
     * @return matching Course
     */
    public Course findByTitle(String title) {
        return repo.findByTitleContainingIgnoreCase(title);
    }
}