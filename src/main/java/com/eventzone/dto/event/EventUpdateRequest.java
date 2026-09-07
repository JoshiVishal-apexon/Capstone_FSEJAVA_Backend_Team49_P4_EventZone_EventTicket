package com.eventzone.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventUpdateRequest(
        @NotBlank(message = "Title is required") String title,
        String description,
        @NotNull(message = "Event date is required") LocalDateTime eventDate,
        @NotBlank(message = "Venue is required") String venue,
        String coverImageUrl,
        @NotNull(message = "Category id is required") UUID categoryId
) {
}
