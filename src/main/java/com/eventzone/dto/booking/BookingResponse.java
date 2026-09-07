package com.eventzone.dto.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String bookingRef,
        UUID ticketCategoryId,
        String ticketCategoryName,
        UUID eventId,
        String eventTitle,
        int quantity,
        BigDecimal unitPrice,
        String status,
        LocalDateTime createdAt
) {
}
