package com.eventzone.service;

import com.eventzone.dto.event.EventCreateRequest;
import com.eventzone.dto.event.EventDetailResponse;
import com.eventzone.dto.event.EventSummaryResponse; 	         	
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

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    public static final String ROLE_ADMIN = "ADMIN";

    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final BookingRepository bookingRepository;

    public List<EventSummaryResponse> listActive(String categoryName) {
        List<Event> events = (categoryName == null || categoryName.isBlank())
                ? eventRepository.findByActiveTrue()
                : eventRepository.findByActiveTrueAndCategory_NameIgnoreCase(categoryName);

        return events.stream()
                .sorted(Comparator.comparing(Event::getEventDate))
                .map(this::toSummary)
                .toList();
    }

    public EventDetailResponse getDetail(UUID id) {
        Event event = findEventOrThrow(id);
        return toDetail(event);
    }

    @Transactional
    public EventDetailResponse create(EventCreateRequest request, User organiser) {
        EventCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Event event = Event.builder()
                .title(request.title())
                .description(request.description())
                .eventDate(request.eventDate())
                .venue(request.venue())
                .coverImageUrl(request.coverImageUrl())
                .organiser(organiser)
                .category(category)
                .active(true)
                .build();

        Event saved = eventRepository.save(event);
        return toDetail(saved);
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
        // no bookings exist -> delete event
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

        // treat unknown/null status as an existing confirmed booking -> conflict
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

    private EventSummaryResponse toSummary(Event event) {
        BigDecimal min = null;
        BigDecimal max = null;
        for (TicketCategory tc : event.getTicketCategories()) {
            if (min == null || tc.getPrice().compareTo(min) < 0) {
                min = tc.getPrice();
            }
            if (max == null || tc.getPrice().compareTo(max) > 0) {
                max = tc.getPrice();
            }
        }
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getCategory().getName(),
                event.getEventDate(),
                event.getVenue(),
                event.isActive(),
                event.getCoverImageUrl(),
                min,
                max
        );
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

    public List<EventSummaryResponse> listAllEvents(String categoryName) {
        List<Event> events = (categoryName == null || categoryName.isBlank())
                ? eventRepository.findAll()
                : eventRepository.findByActiveTrueAndCategory_NameIgnoreCase(categoryName);

        return events.stream()
                .sorted(Comparator.comparing(Event::getEventDate))
                .map(this::toSummary)
                .toList();
    }
}
