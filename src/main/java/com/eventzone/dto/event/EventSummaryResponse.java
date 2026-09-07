package com.eventzone.dto.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventSummaryResponse(
        UUID id,
        String title,
        String categoryName,
        LocalDateTime eventDate,
        String venue,
        boolean active,
        String coverImageUrl,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
