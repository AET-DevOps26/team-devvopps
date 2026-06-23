package com.tum.user.controller;

import com.tum.user.model.User;
import com.tum.user.service.UserService;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for User endpoints.
 * 
 * Handles HTTP requests related to users.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@CrossOrigin
public class UserController {

    // Service layer dependency
    private final UserService service;

    /**
     * POST /users
     * 
     * Creates and stores a new user in the database.
     *
     * @param user request body containing user data
     * @return created user
     */
    @PostMapping
    public User create(@RequestBody User user) {
        return service.createUser(user);
    }

    /**
     * GET /users/{id}
     * 
     * Returns a user by ID.
     *
     * @param id user ID
     * @return matching user
     */
    @GetMapping("/{id}")
    public User getUser(@PathVariable("id") Long id) {
        return service.getUser(id);
    }

    /**
     * GET /users
     * 
     * Returns all users.
     *
     * @return list of users
     */
    @GetMapping
    public List<User> getAll() {
        return service.getAllUsers();
    }
}
