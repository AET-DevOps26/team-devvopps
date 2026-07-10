package com.tum.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifies JWTs issued by user-service, using the shared HS256 signing key.
 * Throws (via jjwt) on invalid signature or expiry.
 */
@Component
public class JwtVerifier {

    /** Identity extracted from a verified token. */
    public record Identity(Long userId, String role) {
    }

    private final SecretKey key;

    public JwtVerifier(@Value("${app.jwt.signing-key}") String signingKey) {
        this.key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
    }

    /** Verifies the token and returns its identity, or throws if invalid/expired. */
    public Identity verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        return new Identity(userId, role);
    }
}
