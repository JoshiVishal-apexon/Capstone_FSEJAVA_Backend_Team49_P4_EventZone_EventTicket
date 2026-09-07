package com.eventzone.dto.booking;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookingRequest(
        @NotNull(message = "Ticket category id is required") UUID ticketCategoryId,
        @NotNull(message = "Quantity is required") Integer quantity
) {
}
