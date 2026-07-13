package com.tum.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User entity representing an account in the system.
 * Each user authenticates with an email + password and can create roadmaps.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** Unique identifier for the user (auto-generated). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long user_id;

    /** Login identity — unique, required. */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * BCrypt hash of the user's password. Never returned in API responses
     * (write-only via @JsonIgnore) and never logged.
     */
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    /** Authorization role; defaults to USER. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    /** Optional display name. */
    @Column
    private String name;
}
