package com.tum.user.dto;

import com.tum.user.model.Role;

/**
 * Response body for auth endpoints. The JWT itself is NOT included here —
 * it is delivered as an httpOnly cookie so client-side JavaScript cannot read it.
 */
public record AuthResponse(
        Long userId,
        String email,
        Role role
) {
}
