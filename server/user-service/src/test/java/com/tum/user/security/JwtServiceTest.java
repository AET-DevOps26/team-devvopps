package com.tum.user.security;

import com.tum.user.model.Role;
import com.tum.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtService.
 *
 * This test class verifies that the JWT service correctly generates signed
 * JSON Web Tokens (JWTs) for authenticated users. The tests cover:
 * - Creating a valid signed JWT.
 * - Including the expected standard and custom claims.
 * - Setting the issued-at and expiration timestamps correctly.
 * - Returning the configured token lifetime.
 *
 * The generated tokens are parsed and verified using the same signing key
 * to ensure both their integrity and their contents are correct.
 */
class JwtServiceTest {

    /**
     * Test signing key.
     *
     * HS256 requires a sufficiently long secret (at least 256 bits / 32 bytes).
     */
    private static final String SIGNING_KEY =
            "this-is-a-test-signing-key-with-at-least-32-bytes";

    private static final long EXPIRATION_SECONDS = 3600;

    private JwtService jwtService;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SIGNING_KEY, EXPIRATION_SECONDS);
        secretKey = Keys.hmacShaKeyFor(
                SIGNING_KEY.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a test user with representative values used throughout the tests.
     */
    private User createUser() {
        User user = new User();
        user.setUser_id(42L);
        user.setEmail("alice@example.com");
        user.setRole(Role.USER);
        return user;
    }

    /**
     * Verifies that issuing a token produces a signed JWT containing the
     * expected subject and custom claims.
     */
    @Test
    @DisplayName("Should issue JWT containing the expected claims")
    void shouldIssueJwtWithExpectedClaims() {

        User user = createUser();

        String token = jwtService.issue(user);

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("42", claims.getSubject());
        assertEquals("alice@example.com", claims.get("email"));
        assertEquals("USER", claims.get("role"));
    }

    /**
     * Verifies that issued tokens contain both an issued-at timestamp and
     * an expiration timestamp, and that the configured lifetime is respected.
     */
    @Test
    @DisplayName("Should set issued-at and expiration timestamps")
    void shouldSetIssuedAtAndExpiration() {

        User user = createUser();

        Instant before = Instant.now().minusSeconds(1);

        String token = jwtService.issue(user);

        Instant after = Instant.now().plusSeconds(1);

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();

        assertNotNull(issuedAt);
        assertNotNull(expiration);

        // issuedAt should have been generated during token creation.
        assertFalse(issuedAt.toInstant().isBefore(before));
        assertFalse(issuedAt.toInstant().isAfter(after));

        // Expiration should equal issuedAt + configured lifetime.
        long actualLifetime =
                expiration.toInstant().getEpochSecond()
                        - issuedAt.toInstant().getEpochSecond();

        assertEquals(EXPIRATION_SECONDS, actualLifetime);
    }

    /**
     * Verifies that the generated JWT is correctly signed and therefore can
     * be parsed using the configured signing key without throwing exceptions.
     */
    @Test
    @DisplayName("Should generate a valid signed JWT")
    void shouldGenerateValidSignedJwt() {

        User user = createUser();

        String token = jwtService.issue(user);

        assertDoesNotThrow(() ->
                Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
        );
    }

    /**
     * Verifies that the configured expiration time is exposed through the
     * service so it can be reused for cookie configuration.
     */
    @Test
    @DisplayName("Should return the configured expiration time")
    void shouldReturnConfiguredExpirationSeconds() {
        assertEquals(EXPIRATION_SECONDS, jwtService.getExpirationSeconds());
    }
}