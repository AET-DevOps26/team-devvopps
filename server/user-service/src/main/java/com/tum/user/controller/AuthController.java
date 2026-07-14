package com.tum.user.controller;

import com.tum.user.dto.AuthResponse;
import com.tum.user.dto.LoginRequest;
import com.tum.user.dto.SignupRequest;
import com.tum.user.model.User;
import com.tum.user.repository.UserRepository;
import com.tum.user.security.AuthEventLog;
import com.tum.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Authentication endpoints.
 *
 * /auth/signup and /auth/login are PUBLIC (allow-listed at the gateway).
 * /auth/me, /auth/logout and /auth/logs sit behind the gateway JWT filter,
 * which injects the trusted X-User-Id / X-User-Role headers.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository repo;
    private final AuthEventLog eventLog;

    public AuthController(AuthService authService, UserRepository repo, AuthEventLog eventLog) {
        this.authService = authService;
        this.repo = repo;
        this.eventLog = eventLog;
    }

    /** POST /auth/signup — register and start a session. */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest req) {
        User user = authService.signup(req);
        ResponseCookie cookie = authService.buildAuthCookie(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authService.toResponse(user));
    }

    /** POST /auth/login — verify credentials and start a session. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = authService.login(req);
        ResponseCookie cookie = authService.buildAuthCookie(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authService.toResponse(user));
    }

    /** POST /auth/logout — clear the session cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = authService.buildLogoutCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /**
     * GET /auth/me — returns the currently authenticated user.
     * Identity comes from the gateway-injected X-User-Id header.
     */
    @GetMapping("/me")
    public AuthResponse me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        User user = repo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        return authService.toResponse(user);
    }

    /**
     * GET /auth/logs — recent auth events for the admin panel.
     * ADMIN-only (enforced at the gateway).
     */
    @GetMapping("/logs")
    public Map<String, List<AuthEventLog.AuthEvent>> logs() {
        return Map.of("logs", eventLog.recent());
    }
}
