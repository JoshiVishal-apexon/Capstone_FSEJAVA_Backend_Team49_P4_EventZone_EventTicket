package com.eventzone.service;

import com.eventzone.dto.event.EventDetailResponse;
import com.eventzone.dto.event.EventUpdateRequest;
import com.eventzone.dto.ticketcategory.TicketCategoryResponse;
import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.Booking;
import com.eventzone.entity.User;
import com.eventzone.exception.ConflictException;
import com.eventzone.exception.ForbiddenException;
import com.eventzone.exception.ResourceNotFoundException;
import com.eventzone.repository.BookingRepository;
import com.eventzone.repository.EventCategoryRepository;
import com.eventzone.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    public static final String ROLE_ADMIN = "ADMIN";

    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final BookingRepository bookingRepository;

    public EventDetailResponse getDetail(UUID id) {
        Event event = findEventOrThrow(id);
        return toDetail(event);
    }

    @Transactional
    public EventDetailResponse update(UUID id, EventUpdateRequest request, User currentUser) {
        Event event = findEventOrThrow(id);
        assertOwnerOrAdmin(event, currentUser);

        EventCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setEventDate(request.eventDate());
        event.setVenue(request.venue());
        event.setCoverImageUrl(request.coverImageUrl());
        event.setCategory(category);

        Event saved = eventRepository.save(event);
        return toDetail(saved);
    }

    @Transactional
    public void delete(UUID id, User currentUser) {
        Event event = findEventOrThrow(id);
        assertOwnerOrAdmin(event, currentUser);

        Booking existingBooking = bookingRepository.findFirstByTicketCategory_Event_Id(id);
        if (existingBooking == null) {
            eventRepository.delete(event);
            return;
        }

        if ("CANCELLED".equals(existingBooking.getStatus())) {
            existingBooking.setStatus("DELETED");
            event.setActive(false);
            bookingRepository.saveAndFlush(existingBooking);
            eventRepository.saveAndFlush(event);
            return;
        }

        if (existingBooking.getStatus() == null || "CONFIRMED".equals(existingBooking.getStatus())) {
            throw new ConflictException("Cannot delete event '" + event.getTitle() + "' because tickets have already been booked for it (booking ref: " + existingBooking.getBookingRef() + ")");
        }
    }

    @Transactional
    public void setActive(UUID id, boolean active) {
        Event event = findEventOrThrow(id);
        event.setActive(active);
        eventRepository.save(event);
    }

    public void assertOwnerOrAdmin(Event event, User currentUser) {
        boolean isAdmin = ROLE_ADMIN.equals(currentUser.getRole());
        boolean isOwner = event.getOrganiser().getId().equals(currentUser.getId());
        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("You do not have permission to modify this event");
        }
    }

    Event findEventOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
    }

    private EventDetailResponse toDetail(Event event) {
        List<TicketCategoryResponse> ticketCategories = event.getTicketCategories().stream()
                .map(tc -> new TicketCategoryResponse(tc.getId(), tc.getName(), tc.getPrice(), tc.getTotalSeats(), tc.getAvailableSeats()))
                .toList();

        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory().getName(),
                event.getEventDate(),
                event.getVenue(),
                event.getCoverImageUrl(),
                event.isActive(),
                event.getOrganiser().getName(),
                ticketCategories
        );
    }
}
