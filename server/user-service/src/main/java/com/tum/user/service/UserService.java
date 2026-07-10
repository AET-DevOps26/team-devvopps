package com.tum.user.service;

import com.tum.user.model.User;
import com.tum.user.repository.UserRepository;

import lombok.AllArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service layer for User-related business logic.
 * 
 * Handles communication between controller and repository.
 */
@Service
@AllArgsConstructor
public class UserService {

    // Repository dependency for database access
    private final UserRepository repo;

    /**
     * Creates and stores a new user.
     *
     * @param user user object to save
     * @return saved user
     */
    public User createUser(User user) {
        return repo.save(user);
    }

    /**
     * Returns a user by ID.
     *
     * Throws an exception if the user does not exist.
     *
     * @param id user ID
     * @return matching user
     */
    public User getUser(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /**
     * Returns all users stored in the database.
     *
     * @return list of users
     */
    public List<User> getAllUsers() {
        return repo.findAll();
    }

    /**
     * Deletes a user by ID.
     *
     * @param id user ID
     */
    public void deleteUser(Long id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        repo.deleteById(id);
    }
}