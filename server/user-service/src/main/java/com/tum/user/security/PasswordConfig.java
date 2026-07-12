package com.tum.user.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the BCrypt password encoder. Uses spring-security-crypto only
 * (no full security auto-configuration / filter chain).
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Default strength 10; BCrypt includes a per-hash random salt.
        return new BCryptPasswordEncoder();
    }
}
