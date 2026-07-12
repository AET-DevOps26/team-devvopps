package com.tum.user.security;

import com.tum.user.model.Role;
import com.tum.user.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues signed JWTs for authenticated users.
 *
 * Tokens are signed with HS256 using a shared secret ({@code app.jwt.signing-key})
 * that the api-gateway also holds so it can verify them. Claims:
 *   sub   = userId
 *   email = user email
 *   role  = USER | ADMIN
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.jwt.signing-key}") String signingKey,
            @Value("${app.jwt.expiration-seconds:86400}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    /** Builds a signed JWT for the given user. */
    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getUser_id()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    /** Cookie max-age (seconds), matching the token lifetime. */
    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
