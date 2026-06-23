package com.tum.course.controller;

import com.tum.course.model.Course;
import com.tum.course.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for CourseController.
 *
 * Uses @WebMvcTest to load only the MVC layer.
 * CourseService is mocked to isolate controller behavior.
 */
@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService service;

    /**
     * Verifies that GET /courses returns 200 with a list of courses.
     */
    @Test
    void getAllCourses_returns200() throws Exception {
        when(service.getAllCourses()).thenReturn(List.of(new Course()));

        mockMvc.perform(get("/courses"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that GET /courses/{id} returns 200 when the course exists.
     */
    @Test
    void getCourseById_returns200() throws Exception {
        Course c = new Course();
        c.setCourse_id(1L);

        when(service.getCourse(1L)).thenReturn(c);

        mockMvc.perform(get("/courses/1"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that GET /courses/search?title=... returns 200 when a match is found.
     */
    @Test
    void searchCourse_returns200() throws Exception {
        Course c = new Course();
        when(service.findByTitle("ml")).thenReturn(c);

        mockMvc.perform(get("/courses/search").param("title", "ml"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that GET /courses/{id} returns 404 when no course exists with that ID.
     */
    @Test
    void getCourseById_returns404_whenNotFound() throws Exception {
        when(service.getCourse(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        mockMvc.perform(get("/courses/99"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that GET /courses/search returns 404 when no course matches the title.
     */
    @Test
    void searchCourse_returns404_whenNotFound() throws Exception {
        when(service.findByTitle("unknown"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        mockMvc.perform(get("/courses/search").param("title", "unknown"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that GET /courses/search returns 400 when the required title param is missing.
     */
    @Test
    void searchCourse_returns400_whenTitleParamMissing() throws Exception {
        mockMvc.perform(get("/courses/search"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that GET /courses/{id} returns 400 when the ID cannot be parsed as a number.
     */
    @Test
    void getCourseById_returns400_whenIdNotANumber() throws Exception {
        mockMvc.perform(get("/courses/abc"))
                .andExpect(status().isBadRequest());
    }
}