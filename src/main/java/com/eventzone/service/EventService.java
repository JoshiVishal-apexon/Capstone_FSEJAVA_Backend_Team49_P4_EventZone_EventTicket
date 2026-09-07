package com.eventzone.service;

import com.eventzone.dto.event.EventDetailResponse;
import com.eventzone.dto.event.EventSummaryResponse;
import com.eventzone.dto.ticketcategory.TicketCategoryResponse;
import com.eventzone.entity.Event;
import com.eventzone.entity.TicketCategory;
import com.eventzone.exception.ResourceNotFoundException;
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

    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listActive(String categoryName) {
        List<Event> events = (categoryName == null || categoryName.isBlank())
                ? eventRepository.findByActiveTrue()
                : eventRepository.findByActiveTrueAndCategory_NameIgnoreCase(categoryName);

        return events.stream()
                .sorted(Comparator.comparing(Event::getEventDate))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getDetail(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        return toDetail(event);
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
}
