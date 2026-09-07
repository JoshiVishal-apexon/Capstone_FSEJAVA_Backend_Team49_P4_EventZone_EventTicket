package com.eventzone.dto.event;

import com.eventzone.dto.ticketcategory.TicketCategoryResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventDetailResponse(
        UUID id,
        String title,
        String description,
        String categoryName,
        LocalDateTime eventDate,
        String venue,
        String coverImageUrl,
        boolean active,
        String organiserName,
        List<TicketCategoryResponse> ticketCategories
) {
}
