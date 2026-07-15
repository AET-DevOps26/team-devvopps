package com.tum.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtVerifier.
 *
 * This test class verifies the JWT validation logic used by the API gateway.
 * 
 * The tests cover:
 * - Successful verification of valid JWTs.
 * - Extraction of userId and role claims.
 * - Rejection of JWTs with invalid signatures.
 * - Rejection of expired JWTs.
 * - Failure when required claims are missing or malformed.
 *
 * Unlike JwtAuthFilterTest, this class does not mock JWT handling
 * because the purpose of this component is to test the actual cryptographic
 * verification and claim extraction.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtVerifierTest {

    /**
     * HS256 requires a sufficiently long signing key.
     * This key corresponds to the JWT signing key configured in application.properties of user-service.
     */
    private static final String SIGNING_KEY =
            "dev-only-insecure-key-change-me-0123456789";

    private JwtVerifier verifier;
    private SecretKey secretKey;


    /**
     * Creates a real JwtVerifier instance using the shared test signing
     * key, and derives a SecretKey from the same key so individual tests
     * can mint their own valid (or intentionally malformed) JWTs without going
     * through any application context.
     * 
     * Using a real JwtVerifier here (rather than a mock) means this suite
     * exercises actual cryptographic verification logic, complementing the
     * unit tests in JwtAuthFilterTest which mock it out.
     */
    @BeforeEach
    void setUp() {
        verifier = new JwtVerifier(SIGNING_KEY);

        secretKey = Keys.hmacShaKeyFor(
                SIGNING_KEY.getBytes(StandardCharsets.UTF_8)
        );
    }


    /**
     * Verifies that a correctly signed JWT is accepted and the identity
     * information is extracted correctly.
     */
    @Test
    @DisplayName("Should verify valid JWT and extract identity")
    void shouldVerifyValidJwt() {

        String token = createToken(
                "123",
                "USER",
                Instant.now().plusSeconds(3600)
        );

        JwtVerifier.Identity identity =
                verifier.verify(token);

        assertEquals(123L, identity.userId());
        assertEquals("USER", identity.role());
    }


    /**
     * Verifies that tokens signed with another key are rejected.
     */
    @Test
    @DisplayName("Should reject JWT with invalid signature")
    void shouldRejectInvalidSignature() {

        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "another-test-signing-key-with-at-least-32-bytes"
                        .getBytes(StandardCharsets.UTF_8)
        );


        String token = Jwts.builder()
                .subject("123")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(
                        Date.from(
                                Instant.now().plusSeconds(3600)
                        )
                )
                .signWith(wrongKey)
                .compact();


        assertThrows(Exception.class, () -> 
            verifier.verify(token)
        );
    }


    /**
     * Verifies that expired JWTs cannot be verified.
     */
    @Test
    @DisplayName("Should reject expired JWT")
    void shouldRejectExpiredJwt() {

        String token = createToken(
                "123",
                "USER",
                Instant.now().minusSeconds(60)
        );


        assertThrows(Exception.class,() -> 
            verifier.verify(token)
        );
    }


    /**
     * Verifies that missing subject claim causes verification failure because
     * the verifier expects the subject to contain the user ID.
     */
    @Test
    @DisplayName("Should reject JWT without user id subject")
    void shouldRejectMissingUserId() {

        String token = Jwts.builder()
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(
                        Date.from(
                                Instant.now().plusSeconds(3600)
                        )
                )
                .signWith(secretKey)
                .compact();


        assertThrows(Exception.class, () -> 
            verifier.verify(token)
        );
    }


    /**
     * Verifies that the role claim is extracted correctly for administrators.
     */
    @Test
    @DisplayName("Should extract ADMIN role correctly")
    void shouldExtractAdminRole() {

        String token = createToken(
                "1",
                "ADMIN",
                Instant.now().plusSeconds(3600)
        );

        JwtVerifier.Identity identity =
                verifier.verify(token);

        assertEquals(1L, identity.userId());
        assertEquals("ADMIN", identity.role());
    }


    /**
     * Helper method that creates a signed JWT for testing.
     *
     * @param userId user id stored in the subject claim
     * @param role user role claim
     * @param expiration expiration timestamp
     */
    private String createToken(
            String userId,
            String role,
            Instant expiration
    ) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }
}