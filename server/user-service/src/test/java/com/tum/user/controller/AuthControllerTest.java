package com.tum.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tum.user.dto.LoginRequest;
import com.tum.user.dto.SignupRequest;
import com.tum.user.model.User;
import com.tum.user.repository.UserRepository;
import com.tum.user.security.AuthEventLog;
import com.tum.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController.
 *
 * Uses MockMvc with a standalone controller setup to test only the controller layer.
 * All dependencies (AuthService, UserRepository, AuthEventLog) are mocked using Mockito.
 *
 * These tests verify:
 * - Authentication endpoints return the correct HTTP status codes
 * - Signup and login correctly process requests and return authentication cookies
 * - Logout correctly clears the authentication cookie
 * - Missing or invalid authentication headers are handled correctly
 * - User information can be retrieved for authenticated users
 * - User lookup failures return the correct unauthorized response
 * - Authentication event logs are returned correctly
 */
class AuthControllerTest {

    private MockMvc mockMvc;

    private AuthService authService;
    private UserRepository repo;
    private AuthEventLog eventLog;

    private ObjectMapper objectMapper;

    private AuthController controller;


    @BeforeEach
    void setup() {
        authService = Mockito.mock(AuthService.class);
        repo = Mockito.mock(UserRepository.class);
        eventLog = Mockito.mock(AuthEventLog.class);

        controller = new AuthController(
                authService,
                repo,
                eventLog
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper();
    }


    /**
     * Verifies that a valid signup request creates a user
     * and returns an authentication cookie.
     */
    @Test
    void signup_shouldCreateUserAndReturnCookie() throws Exception {

        SignupRequest request = new SignupRequest(
                "test@test.com",
                "password123"
        );

        User user = new User();
        user.setUser_id(1L);
        user.setEmail("test@test.com");

        when(authService.signup(any(SignupRequest.class)))
                .thenReturn(user);

        when(authService.buildAuthCookie(user))
                .thenReturn(ResponseCookie.from("token", "jwt-token")
                        .httpOnly(true)
                        .build());

        when(authService.toResponse(user))
                .thenReturn(null);


        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"));
    }


    /**
     * Verifies that valid login credentials authenticate the user
     * and return an authentication cookie.
     */
    @Test
    void login_shouldAuthenticateAndReturnCookie() throws Exception {

        LoginRequest request = new LoginRequest(
                "test@test.com",
                "password123"
        );

        User user = new User();
        user.setUser_id(1L);

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(user);

        when(authService.buildAuthCookie(user))
                .thenReturn(ResponseCookie.from("token", "jwt")
                        .build());

        when(authService.toResponse(user))
                .thenReturn(null);


        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
    }


    /**
     * Verifies that logout clears the authentication cookie
     * and returns the expected no-content response.
     */
    @Test
    void logout_shouldClearCookie() throws Exception {

        when(authService.buildLogoutCookie())
                .thenReturn(ResponseCookie.from("token", "")
                        .maxAge(0)
                        .build());


        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"));
    }


    /**
     * Verifies that an authenticated user can retrieve
     * their own user information.
     */
    @Test
    void me_shouldReturnUser_whenAuthenticated() throws Exception {

        User user = new User();
        user.setUser_id(1L);
        user.setEmail("test@test.com");


        when(repo.findById(1L))
                .thenReturn(Optional.of(user));

        when(authService.toResponse(user))
                .thenReturn(null);


        mockMvc.perform(get("/auth/me")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }


    /**
     * Verifies that requesting the current user without
     * an authentication header returns unauthorized.
     */
    @Test
    void me_shouldReturn401_whenNoUserHeader() throws Exception {

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }


    /**
     * Verifies that requesting a user that does not exist
     * returns unauthorized.
     */
    @Test
    void me_shouldReturn401_whenUserNotFound() throws Exception {

        when(repo.findById(99L))
                .thenReturn(Optional.empty());


        mockMvc.perform(get("/auth/me")
                        .header("X-User-Id", "99"))
                .andExpect(status().isUnauthorized());
    }


    /**
     * Verifies that authentication event logs are returned successfully.
     */
    @Test
    void logs_shouldReturnRecentEvents() throws Exception {

        when(eventLog.recent())
                .thenReturn(List.of());


        mockMvc.perform(get("/auth/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs").isArray());
    }
}