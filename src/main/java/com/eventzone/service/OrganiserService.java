package com.eventzone.service;

import com.eventzone.dto.organiser.OrganiserEventResponse;
import com.eventzone.dto.organiser.OrganiserTicketCategoryResponse;
import com.eventzone.entity.Booking;
import com.eventzone.entity.Event;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.repository.BookingRepository;
import com.eventzone.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.eventzone.service.BookingService.STATUS_CANCELLED;

@Service
@RequiredArgsConstructor
public class OrganiserService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public List<OrganiserEventResponse> myEvents(User organiser) {
        return eventRepository.findByOrganiser_Id(organiser.getId()).stream()
                .map(this::toOrganiserEventResponse)
                .toList();
    }

    private OrganiserEventResponse toOrganiserEventResponse(Event event) {
        List<OrganiserTicketCategoryResponse> ticketCategories = event.getTicketCategories().stream()
                .map(this::toOrganiserTicketCategoryResponse)
                .toList();

        return new OrganiserEventResponse(
                event.getId(),
                event.getTitle(),
                event.getCategory().getName(),
                event.getEventDate(),
                event.getVenue(),
                event.isActive(),
                ticketCategories
        );
    }

    private OrganiserTicketCategoryResponse toOrganiserTicketCategoryResponse(TicketCategory tc) {
        long totalBooked = bookingRepository.findByTicketCategory_IdAndStatusNot(tc.getId(), STATUS_CANCELLED)
                .stream()
                .mapToLong(Booking::getQuantity)
                .sum();

        return new OrganiserTicketCategoryResponse(
                tc.getId(),
                tc.getName(),
                tc.getPrice(),
                tc.getTotalSeats(),
                tc.getAvailableSeats(),
                totalBooked
        );
    }
}
