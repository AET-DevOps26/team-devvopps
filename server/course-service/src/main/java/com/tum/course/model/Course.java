package com.tum.course.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Course entity representing a TUM course in the mock database.
 * Contains course details that can be included in the prompt to the AI system for possible recommendations.
 * Fields extracted from TUMonline.
 */
@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    /** Unique identifier for the course (auto-generated) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long course_id;

    /** Course title (required) */
    @Column(nullable = false)
    private String title;

    /** Detailed course content and description */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** Prerequisites required */
    @Column(columnDefinition = "TEXT")
    private String previous_knowledge;

    /** Learning objectives and expected outcomes */
    @Column(columnDefinition = "TEXT")
    private String objective;

    /** Teaching methods and approach */
    @Column(columnDefinition = "TEXT")
    private String teaching_method;

    /** Registration information and enrollment details */
    @Column(columnDefinition = "TEXT")
    private String registration;
}

