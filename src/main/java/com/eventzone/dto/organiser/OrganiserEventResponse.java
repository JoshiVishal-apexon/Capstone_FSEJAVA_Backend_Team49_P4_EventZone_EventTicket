package com.eventzone.dto.organiser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrganiserEventResponse(
        UUID id,
        String title,
        String categoryName,
        LocalDateTime eventDate,
        String venue,
        boolean active,
        List<OrganiserTicketCategoryResponse> ticketCategories
) {
}
