package com.eventzone.dto.organiser;

import java.math.BigDecimal;
import java.util.UUID;

public record OrganiserTicketCategoryResponse(
        UUID id,
        String name,
        BigDecimal price,
        int totalSeats,
        int availableSeats,
        long totalBooked
) {
}
