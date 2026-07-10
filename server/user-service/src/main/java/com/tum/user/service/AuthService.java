package com.tum.user.service;

import com.tum.user.dto.AuthResponse;
import com.tum.user.dto.LoginRequest;
import com.tum.user.dto.SignupRequest;
import com.tum.user.metrics.AuthMetrics;
import com.tum.user.model.Role;
import com.tum.user.model.User;
import com.tum.user.repository.UserRepository;
import com.tum.user.security.AuthEventLog;
import com.tum.user.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authentication business logic: signup, login, and building the JWT cookie.
 *
 * The JWT is delivered as an httpOnly, SameSite=Strict cookie so it is not
 * readable by client-side JavaScript (XSS-resistant). Passwords are hashed with
 * BCrypt and never logged.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Cookie name holding the JWT. */
    public static final String COOKIE_NAME = "token";

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMetrics metrics;
    private final AuthEventLog eventLog;
    private final boolean cookieSecure;

    public AuthService(UserRepository repo,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthMetrics metrics,
                       AuthEventLog eventLog,
                       @Value("${app.cookie.secure:false}") boolean cookieSecure) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.metrics = metrics;
        this.eventLog = eventLog;
        this.cookieSecure = cookieSecure;
    }

    /** Registers a new user (USER role) and returns the created user. */
    public User signup(SignupRequest req) {
        String email = req.email().trim().toLowerCase();
        if (repo.findByEmail(email).isPresent()) {
            eventLog.record("signup", email, "conflict");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        User saved = repo.save(user);

        metrics.signup();
        eventLog.record("signup", email, "success");
        log.info("[Auth] signup email={} result=success", email);
        return saved;
    }

    /** Verifies credentials and returns the matching user, or 401 on failure. */
    public User login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = repo.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            metrics.loginFailure();
            eventLog.record("login", email, "failure");
            log.info("[Auth] login email={} result=failure", email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        metrics.loginSuccess();
        eventLog.record("login", email, "success");
        log.info("[Auth] login email={} result=success", email);
        return user;
    }

    /** Builds the httpOnly JWT cookie for an authenticated user. */
    public ResponseCookie buildAuthCookie(User user) {
        return ResponseCookie.from(COOKIE_NAME, jwtService.issue(user))
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(jwtService.getExpirationSeconds())
                .build();
    }

    /** Builds an expired cookie that clears the session on logout. */
    public ResponseCookie buildLogoutCookie() {
        metrics.logout();
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }

    public AuthResponse toResponse(User user) {
        return new AuthResponse(user.getUser_id(), user.getEmail(), user.getRole());
    }
}
