package com.eventzone.service;

import com.eventzone.dto.ticketcategory.TicketCategoryRequest;
import com.eventzone.dto.ticketcategory.TicketCategoryResponse;
import com.eventzone.entity.Event;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.exception.ForbiddenException;
import com.eventzone.exception.ResourceNotFoundException;
import com.eventzone.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketCategoryService {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventService eventService;

    @Transactional
    public TicketCategoryResponse create(UUID eventId, TicketCategoryRequest request, User currentUser) {
        Event event = eventService.findEventOrThrow(eventId);
        assertOwner(event, currentUser);

        TicketCategory ticketCategory = TicketCategory.builder()
                .event(event)
                .name(request.name())
                .price(request.price())
                .totalSeats(request.totalSeats())
                .availableSeats(request.totalSeats())
                .build();

        TicketCategory saved = ticketCategoryRepository.save(ticketCategory);
        return toResponse(saved);
    }

    @Transactional
    public TicketCategoryResponse update(UUID ticketCategoryId, TicketCategoryRequest request, User currentUser) {
        TicketCategory ticketCategory = findOrThrow(ticketCategoryId);
        assertOwner(ticketCategory.getEvent(), currentUser);

        int delta = request.totalSeats() - ticketCategory.getTotalSeats();
        ticketCategory.setName(request.name());
        ticketCategory.setPrice(request.price());
        ticketCategory.setTotalSeats(request.totalSeats());
        // keep availableSeats consistent when totalSeats changes
        ticketCategory.setAvailableSeats(Math.max(0, ticketCategory.getAvailableSeats() + delta));

        TicketCategory saved = ticketCategoryRepository.save(ticketCategory);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID ticketCategoryId, User currentUser) {
        TicketCategory ticketCategory = findOrThrow(ticketCategoryId);
        assertOwner(ticketCategory.getEvent(), currentUser);
        ticketCategoryRepository.delete(ticketCategory);
    }

    private void assertOwner(Event event, User currentUser) {
        if (!event.getOrganiser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You do not have permission to manage ticket categories for this event");
        }
    }

    private TicketCategory findOrThrow(UUID id) {
        return ticketCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket category not found"));
    }

    private TicketCategoryResponse toResponse(TicketCategory tc) {
        return new TicketCategoryResponse(tc.getId(), tc.getName(), tc.getPrice(), tc.getTotalSeats(), tc.getAvailableSeats());
    }
}
