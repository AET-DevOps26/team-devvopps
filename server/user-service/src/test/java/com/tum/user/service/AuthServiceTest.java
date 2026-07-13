package com.tum.user.service;

import com.tum.user.dto.LoginRequest;
import com.tum.user.dto.SignupRequest;
import com.tum.user.metrics.AuthMetrics;
import com.tum.user.model.Role;
import com.tum.user.model.User;
import com.tum.user.repository.UserRepository;
import com.tum.user.security.AuthEventLog;
import com.tum.user.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 *
 * Dependencies such as UserRepository, PasswordEncoder, JwtService,
 * AuthMetrics, and AuthEventLog are mocked to isolate service logic.
 *
 * These tests verify:
 * - User signup flow and duplicate email handling
 * - Login authentication and failure cases
 * - Password encoding and validation behavior
 * - JWT authentication cookie creation and deletion
 * - User response mapping
 * - Authentication metrics and event logging
 *
 */
class AuthServiceTest {

    private UserRepository repo;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthMetrics metrics;
    private AuthEventLog eventLog;

    private AuthService authService;


    @BeforeEach
    void setup() {

        repo = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        metrics = mock(AuthMetrics.class);
        eventLog = mock(AuthEventLog.class);

        authService = new AuthService(
                repo,
                passwordEncoder,
                jwtService,
                metrics,
                eventLog,
                false
        );
    }


    /**
     * Verifies that signup creates a new user when the email does not already exist.
     *
     * The test checks:
     * - Password is encoded
     * - Default USER role is assigned
     * - User is persisted
     * - Signup metric and audit event are recorded
     */
    @Test
    void signup_shouldCreateNewUser() {

        SignupRequest request = new SignupRequest(
                "Test@Test.com",
                "password123"
        );

        when(repo.findByEmail("test@test.com"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("password123"))
                .thenReturn("hashed-password");

        User savedUser = new User();
        savedUser.setUser_id(1L);
        savedUser.setEmail("test@test.com");
        savedUser.setPassword("hashed-password");
        savedUser.setRole(Role.USER);

        when(repo.save(any(User.class)))
                .thenReturn(savedUser);


        User result = authService.signup(request);


        assertEquals("test@test.com", result.getEmail());
        assertEquals(Role.USER, result.getRole());

        verify(repo).save(any(User.class));
        verify(metrics).signup();
        verify(eventLog).record("signup", "test@test.com", "success");
    }


    /**
     * Verifies that signup rejects registration when the email is already registered.
     *
     * The service should return HTTP 409 and avoid saving a duplicate user.
     */
    @Test
    void signup_shouldRejectDuplicateEmail() {

        SignupRequest request = new SignupRequest(
                "test@test.com",
                "password123"
        );

        User existing = new User();

        when(repo.findByEmail("test@test.com"))
                .thenReturn(Optional.of(existing));


        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class,
                        () -> authService.signup(request));


        assertEquals(409, exception.getStatusCode().value());

        verify(repo, never()).save(any());
        verify(eventLog)
                .record("signup", "test@test.com", "conflict");
    }


    /**
     * Verifies that login succeeds when the user exists and the password matches.
     *
     * The test checks that:
     * - The correct user is returned
     * - Successful login metrics are recorded
     * - A success audit event is created
     */
    @Test
    void login_shouldReturnUserWithValidCredentials() {

        LoginRequest request = new LoginRequest(
                "TEST@test.com",
                "password123"
        );

        User user = new User();
        user.setEmail("test@test.com");
        user.setPassword("hashed-password");

        when(repo.findByEmail("test@test.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "hashed-password"))
                .thenReturn(true);


        User result = authService.login(request);


        assertSame(user, result);

        verify(metrics).loginSuccess();
        verify(eventLog)
                .record("login", "test@test.com", "success");
    }


    /**
     * Verifies that login fails when the provided password does not match.
     *
     * The test checks that:
     * - HTTP 401 is returned
     * - Failed login metrics are recorded
     * - Failure event is logged
     */
    @Test
    void login_shouldFailWithWrongPassword() {

        LoginRequest request = new LoginRequest(
                "test@test.com",
                "wrongpassword"
        );

        User user = new User();
        user.setEmail("test@test.com");
        user.setPassword("hashed-password");


        when(repo.findByEmail("test@test.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrongpassword",
                "hashed-password"))
                .thenReturn(false);


        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class,
                        () -> authService.login(request));


        assertEquals(401, exception.getStatusCode().value());

        verify(metrics).loginFailure();
        verify(eventLog)
                .record("login", "test@test.com", "failure");
    }


    /**
     * Verifies that login fails when no user exists with the given email.
     *
     * The test ensures failed authentication is tracked.
     */
    @Test
    void login_shouldFailWhenUserDoesNotExist() {

        LoginRequest request = new LoginRequest(
                "missing@test.com",
                "password123"
        );

        when(repo.findByEmail("missing@test.com"))
                .thenReturn(Optional.empty());


        assertThrows(ResponseStatusException.class,
                () -> authService.login(request));


        verify(metrics).loginFailure();
    }


    /**
     * Verifies that a valid JWT authentication cookie is created for a user.
     *
     * The cookie should:
     * - Contain the generated JWT token
     * - Be HttpOnly
     * - Use the root path
     * - Use SameSite Strict
     */
    @Test
    void buildAuthCookie_shouldCreateJwtCookie() {

        User user = new User();
        user.setUser_id(1L);
        user.setEmail("test@test.com");


        when(jwtService.issue(user))
                .thenReturn("jwt-token");

        when(jwtService.getExpirationSeconds())
                .thenReturn(3600L);


        ResponseCookie cookie =
                authService.buildAuthCookie(user);


        assertEquals("token", cookie.getName());
        assertEquals("jwt-token", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertEquals("/", cookie.getPath());
        assertEquals("Strict", cookie.getSameSite());
    }


    /**
     * Verifies that logout creates an expired authentication cookie.
     *
     * This ensures the authentication token is removed from the client.
     */
    @Test
    void buildLogoutCookie_shouldExpireCookie() {

        ResponseCookie cookie =
                authService.buildLogoutCookie();


        assertEquals("token", cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(0, cookie.getMaxAge().getSeconds());

        verify(metrics).logout();
    }


    /**
     * Verifies that a User entity is correctly converted into an AuthResponse DTO.
     *
     * The mapping should preserve:
     * - User ID
     * - Email
     * - Role
     */
    @Test
    void toResponse_shouldMapUserToAuthResponse() {

        User user = new User();
        user.setUser_id(10L);
        user.setEmail("test@test.com");
        user.setRole(Role.USER);


        var response = authService.toResponse(user);


        assertEquals(10L, response.userId());
        assertEquals("test@test.com", response.email());
        assertEquals(Role.USER, response.role());
    }
}