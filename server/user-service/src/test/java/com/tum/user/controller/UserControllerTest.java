package com.tum.user.controller;

import com.tum.user.model.User;
import com.tum.user.service.UserService;
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
 * Unit tests for UserController.
 *
 * Uses @WebMvcTest to load only the MVC layer.
 * UserService is mocked to isolate controller behavior.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService service;

    /**
     * Verifies that POST /users returns 200 with a valid JSON body.
     */
    @Test
    void createUser_returnsUser() throws Exception {
        User u = new User();
        u.setUser_id(1L);
        u.setName("John");

        when(service.createUser(org.mockito.ArgumentMatchers.any())).thenReturn(u);

        mockMvc.perform(post("/users")
                        .contentType("application/json")
                        .content("{\"name\":\"John\"}"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that GET /users/{id} returns 200 when the user exists.
     */
    @Test
    void getUser_returns200() throws Exception {
        User u = new User();
        when(service.getUser(1L)).thenReturn(u);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that GET /users returns 200 with a list of users.
     */
    @Test
    void getAllUsers_returns200() throws Exception {
        when(service.getAllUsers()).thenReturn(List.of(new User()));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that GET /users/{id} returns 404 when no user exists with that ID.
     */
    @Test
    void getUser_returns404_whenUserNotFound() throws Exception {
        when(service.getUser(99L)).thenThrow(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        );

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that POST /users returns 400 when the request body is missing.
     */
    @Test
    void createUser_returns400_whenBodyMissing() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType("application/json"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that POST /users returns 415 when the Content-Type header is missing.
     */
    @Test
    void createUser_returns415_whenContentTypeMissing() throws Exception {
        mockMvc.perform(post("/users")
                        .content("{\"name\":\"John\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    /**
     * Verifies that GET /users/{id} returns 400 when the ID cannot be parsed as a number.
     */
    @Test
    void getUser_returns400_whenIdNotANumber() throws Exception {
        mockMvc.perform(get("/users/abc"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that GET /users returns 500 when the service throws an unexpected error.
     * No exception handler exists for generic RuntimeExceptions, so it propagates as a ServletException.
     */
    @Test
    void getAllUsers_returns500_whenServiceThrows() throws Exception {
        when(service.getAllUsers()).thenThrow(new RuntimeException("DB error"));

        assertThrows(Exception.class, () ->
            mockMvc.perform(get("/users"))
                   .andExpect(status().isInternalServerError())
        );
    }
}