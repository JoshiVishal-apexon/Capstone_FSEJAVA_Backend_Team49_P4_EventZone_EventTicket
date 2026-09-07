package com.eventzone.dto.ticketcategory;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketCategoryResponse(
        UUID id,
        String name,
        BigDecimal price,
        int totalSeats,
        int availableSeats
) {
}
