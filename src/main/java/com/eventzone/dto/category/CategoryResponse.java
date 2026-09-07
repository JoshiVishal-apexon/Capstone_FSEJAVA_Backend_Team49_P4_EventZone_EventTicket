package com.eventzone.dto.category;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name
) {
}
