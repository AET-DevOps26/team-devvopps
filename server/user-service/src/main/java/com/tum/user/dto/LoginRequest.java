package com.tum.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /auth/login.
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
