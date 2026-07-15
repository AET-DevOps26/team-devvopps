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
     * @param id course ID
     * @return matching course
     */
    @GetMapping("/{id}")
    public Course get(@PathVariable(name = "id") Long id) {
        return service.getCourse(id);
    }

    /**
     * GET /courses/search?title=...
     * 
     * Searches for a course by title.
     * 
     * @param title course title query
     * @return matching course
     */
    @GetMapping("/search")
    public Course search(@RequestParam(name = "title") String title) {
        return service.findByTitle(title);
    }
}