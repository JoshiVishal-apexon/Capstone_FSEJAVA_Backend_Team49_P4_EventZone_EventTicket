package com.eventzone.dto.auth;

public record AuthResponse(
        String token,
        String name,
        String role,
        String email
) {
}
