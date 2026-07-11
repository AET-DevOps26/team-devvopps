package com.tum.course.service;

import com.tum.course.model.Course;
import com.tum.course.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


/**
 * Unit tests for CourseService.
 *
 * CourseRepository is mocked to avoid database access.
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository repo;

    @InjectMocks
    private CourseService service;

    /**
     * Verifies that getAllCourses returns the full list from the repository.
     */
    @Test
    void getAllCourses_returnsList() {
        when(repo.findAll()).thenReturn(List.of(new Course()));

        List<Course> result = service.getAllCourses();

        assertEquals(1, result.size());
        verify(repo).findAll();
    }

    /**
     * Verifies that getCourse returns the correct course when it exists.
     */
    @Test
    void getCourse_returnsCourse() {
        Course c = new Course();
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        Course result = service.getCourse(1L);

        assertNotNull(result);
    }

    /**
     * Verifies that getCourse throws a 404 when no course exists with the given ID.
     */
    @Test
    void getCourse_throwsIfMissing() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getCourse(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Course not found", ex.getReason());
    }

    /**
     * Verifies that findByTitle returns the matching course from the repository.
     */
    @Test
    void findByTitle_returnsCourse() {
        Course c = new Course();
        when(repo.findByTitleContainingIgnoreCase("ml")).thenReturn(c);

        Course result = service.findByTitle("ml");

        assertEquals(c, result);
    }
}