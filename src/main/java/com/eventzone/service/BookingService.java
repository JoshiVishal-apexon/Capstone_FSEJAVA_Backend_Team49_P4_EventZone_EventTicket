package com.eventzone.service;

import com.eventzone.dto.booking.BookingRequest;
import com.eventzone.dto.booking.BookingResponse;
import com.eventzone.entity.Booking;
import com.eventzone.entity.Event;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.exception.BadRequestException;
import com.eventzone.exception.ForbiddenException;
import com.eventzone.exception.ResourceNotFoundException;
import com.eventzone.repository.BookingRepository;
import com.eventzone.repository.EventRepository;
import com.eventzone.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 5;

    private final BookingRepository bookingRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventRepository eventRepository;

    @Transactional
    public BookingResponse book(BookingRequest request, User user) {
        int quantity = request.quantity() == null ? 0 : request.quantity();
        log.info("Booking request received for userId={} ticketCategoryId={} quantity={}", user.getId(), request.ticketCategoryId(), quantity);

        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            log.warn("Booking rejected for userId={} ticketCategoryId={} due to invalid quantity={}", user.getId(), request.ticketCategoryId(), quantity);
            throw new BadRequestException("Quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY);
        }

        TicketCategory ticketCategory = ticketCategoryRepository.findById(request.ticketCategoryId())
                .orElseThrow(() -> {
                    log.warn("Booking rejected: ticket category not found for userId={} ticketCategoryId={}", user.getId(), request.ticketCategoryId());
                    return new ResourceNotFoundException("Ticket category not found");
                });

        if (!ticketCategory.getEvent().isActive()) {
            log.warn("Booking rejected: event inactive for userId={} ticketCategoryId={} eventId={}", user.getId(), request.ticketCategoryId(), ticketCategory.getEvent().getId());
            throw new BadRequestException("This event is no longer active");
        }

        if (quantity > ticketCategory.getAvailableSeats()) {
            log.warn("Booking rejected: insufficient seats for userId={} ticketCategoryId={} requested={} available={}", user.getId(), request.ticketCategoryId(), quantity, ticketCategory.getAvailableSeats());
            throw new BadRequestException("Not enough seats available");
        }

        ticketCategory.setAvailableSeats(ticketCategory.getAvailableSeats() - quantity);
        ticketCategoryRepository.save(ticketCategory);

        Booking booking = Booking.builder()
                .user(user)
                .ticketCategory(ticketCategory)
                .quantity(quantity)
                .status(STATUS_CONFIRMED)
                .bookingRef(generateBookingRef())
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Booking confirmed bookingId={} bookingRef={} userId={} ticketCategoryId={} quantity={}", saved.getId(), saved.getBookingRef(), user.getId(), ticketCategory.getId(), quantity);
        return toResponse(saved);
    }

    public List<BookingResponse> findMine(User user) {
        log.debug("Fetching bookings for userId={}", user.getId());
        return bookingRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<BookingResponse> cancelAllForEvent(UUID eventId, User user) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        boolean isAdmin = "ADMIN".equals(user.getRole());
        boolean isOwner = event.getOrganiser() != null && event.getOrganiser().getId().equals(user.getId());
        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("You do not have permission to modify this event");
        }

        List<Booking> bookings = bookingRepository.findByTicketCategory_Event_IdAndStatusNot(eventId, STATUS_CANCELLED);
        if (bookings.isEmpty()) {
            return List.of();
        }

        for (Booking booking : bookings) {
            booking.setStatus(STATUS_CANCELLED);
            TicketCategory ticketCategory = booking.getTicketCategory();
            ticketCategory.setAvailableSeats(ticketCategory.getAvailableSeats() + booking.getQuantity());
            ticketCategoryRepository.save(ticketCategory);
        }

        bookingRepository.saveAll(bookings);
        return bookings.stream().map(this::toResponse).toList();
    }

    @Transactional
    public BookingResponse cancel(UUID bookingId, User user) {
        log.info("Cancellation request received bookingId={} userId={}", bookingId, user.getId());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> {
                    log.warn("Cancellation failed: booking not found bookingId={} userId={}", bookingId, user.getId());
                    return new ResourceNotFoundException("Booking not found");
                });

        if (!booking.getUser().getId().equals(user.getId())) {
            log.warn("Cancellation forbidden: userId={} attempted to cancel bookingId={} owned by userId={}", user.getId(), bookingId, booking.getUser().getId());
            throw new ForbiddenException("You can only cancel your own bookings");
        }

        if (STATUS_CANCELLED.equals(booking.getStatus())) {
            log.warn("Cancellation rejected: bookingId={} already cancelled", bookingId);
            throw new BadRequestException("This booking is already cancelled");
        }

        booking.setStatus(STATUS_CANCELLED);
        TicketCategory ticketCategory = booking.getTicketCategory();
        ticketCategory.setAvailableSeats(ticketCategory.getAvailableSeats() + booking.getQuantity());
        ticketCategoryRepository.save(ticketCategory);

        Booking saved = bookingRepository.save(booking);
        log.info("Booking cancelled bookingId={} bookingRef={} userId={} quantity={}", saved.getId(), saved.getBookingRef(), user.getId(), saved.getQuantity());
        return toResponse(saved);
    }

    private String generateBookingRef() {
        String candidate;
        do {
            candidate = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (bookingRepository.existsByBookingRef(candidate));
        return candidate;
    }

    private BookingResponse toResponse(Booking booking) {
        TicketCategory tc = booking.getTicketCategory();
        return new BookingResponse(
                booking.getId(),
                booking.getBookingRef(),
                tc.getId(),
                tc.getName(),
                tc.getEvent().getId(),
                tc.getEvent().getTitle(),
                booking.getQuantity(),
                tc.getPrice(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }
}
