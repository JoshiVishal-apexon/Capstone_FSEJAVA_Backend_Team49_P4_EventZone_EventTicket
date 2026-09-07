package com.eventzone.dto.auth;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String role
) {
}
