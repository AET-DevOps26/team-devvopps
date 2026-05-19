package com.tum.course.controller;

import com.tum.course.model.Course;
import com.tum.course.service.CourseService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Course endpoints.
 * 
 * Handles incoming HTTP requests related to courses.
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
@CrossOrigin
public class CourseController {

    // Service layer dependency
    private final CourseService service;

    /**
     * GET /courses
     * 
     * Returns all available courses.
     * 
     * FUTURE AI USE:
     * - AI service may fetch all courses for global filtering
     *
     * @return list of courses
     */
    @GetMapping
    public List<Course> all() {
        return service.getAllCourses();
    }

    /**
     * GET /courses/{id}
     * 
     * Returns a course by its ID.
     * 
     * FUTURE AI USE:
     * - Validate course references
     * - Enrich roadmap with full course metadata
     *
     * @param id course ID
     * @return matching course
     */
    @GetMapping("/{id}")
    public Course get(@PathVariable Long id) {
        return service.getCourse(id);
    }

    /**
     * GET /courses/search?title=...
     * 
     * Searches for a course by title.
     * 
     * FUTURE AI USE:
     * - AI service extracts keywords. Example: "Learn Machine Learning" → ["machine learning", "python", "statistics"]
     * - AI service queries course-service: /courses/search?title=machine learning
     * - Course-service returns matching courses
     * 
     * @param title course title query
     * @return matching course
     */
    @GetMapping("/search")
    public Course search(@RequestParam String title) {
        return service.findByTitle(title);
    }
}