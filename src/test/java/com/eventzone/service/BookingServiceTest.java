package com.eventzone.service;

import com.eventzone.dto.booking.BookingRequest;
import com.eventzone.dto.booking.BookingResponse;
import com.eventzone.entity.Booking;
import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.exception.BadRequestException;
import com.eventzone.exception.ForbiddenException;
import com.eventzone.exception.ResourceNotFoundException;
import com.eventzone.repository.BookingRepository;
import com.eventzone.repository.EventRepository;
import com.eventzone.repository.TicketCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    @Mock
    private EventRepository eventRepository;

    private BookingService bookingService;

    private User attendee;
    private TicketCategory ticketCategory;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, ticketCategoryRepository, eventRepository);

        attendee = User.builder()
                .id(UUID.randomUUID())
                .email("attendee1@eventzone.com")
                .passwordHash("hashed")
                .role("ATTENDEE")
                .name("Aarav")
                .build();

        User organiser = User.builder()
                .id(UUID.randomUUID())
                .email("organiser1@eventzone.com")
                .passwordHash("hashed")
                .role("ORGANISER")
                .name("Skyline Events")
                .build();

        Event event = Event.builder()
                .id(UUID.randomUUID())
                .title("Sunburn Arena")
                .description("desc")
                .venue("Delhi")
                .organiser(organiser)
                .category(EventCategory.builder().id(UUID.randomUUID()).name("Concert").build())
                .active(true)
                .build();

        ticketCategory = TicketCategory.builder()
                .id(UUID.randomUUID())
                .event(event)
                .name("General")
                .price(new BigDecimal("999.00"))
                .totalSeats(100)
                .availableSeats(10)
                .build();
    }

    @Test
    void booking_success_decrementsSeatsAndGeneratesBookingRef() {
        when(ticketCategoryRepository.findById(ticketCategory.getId())).thenReturn(Optional.of(ticketCategory));
        when(bookingRepository.existsByBookingRef(any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BookingResponse response = bookingService.book(new BookingRequest(ticketCategory.getId(), 3), attendee);

        assertThat(response.quantity()).isEqualTo(3);
        assertThat(response.bookingRef()).startsWith("BK-");
        assertThat(response.status()).isEqualTo(BookingService.STATUS_CONFIRMED);
        assertThat(ticketCategory.getAvailableSeats()).isEqualTo(7);

        ArgumentCaptor<TicketCategory> tcCaptor = ArgumentCaptor.forClass(TicketCategory.class);
        verify(ticketCategoryRepository).save(tcCaptor.capture());
        assertThat(tcCaptor.getValue().getAvailableSeats()).isEqualTo(7);
    }

    @Test
    void booking_moreThanAvailableSeats_throwsBadRequest() {
        ticketCategory.setAvailableSeats(2);
        when(ticketCategoryRepository.findById(ticketCategory.getId())).thenReturn(Optional.of(ticketCategory));

        // quantity (3) is within the valid 1-5 range but exceeds the 2 available seats
        assertThatThrownBy(() -> bookingService.book(new BookingRequest(ticketCategory.getId(), 3), attendee))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void booking_quantityAboveMax_throwsBadRequest() {
        assertThatThrownBy(() -> bookingService.book(new BookingRequest(ticketCategory.getId(), 6), attendee))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(ticketCategoryRepository);
    }

    @Test
    void booking_quantityBelowMin_throwsBadRequest() {
        assertThatThrownBy(() -> bookingService.book(new BookingRequest(ticketCategory.getId(), 0), attendee))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(ticketCategoryRepository);
    }

    @Test
    void booking_ticketCategoryNotFound_throwsNotFound() {
        when(ticketCategoryRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.book(new BookingRequest(UUID.randomUUID(), 1), attendee))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelAllForEvent_cancelsEveryConfirmedBookingAndRestoresSeats() {
        User organiser = ticketCategory.getEvent().getOrganiser();
        Booking first = Booking.builder()
                .id(UUID.randomUUID())
                .user(attendee)
                .ticketCategory(ticketCategory)
                .quantity(2)
                .status(BookingService.STATUS_CONFIRMED)
                .bookingRef("BK-AAAA1111")
                .build();
        Booking second = Booking.builder()
                .id(UUID.randomUUID())
                .user(attendee)
                .ticketCategory(ticketCategory)
                .quantity(1)
                .status(BookingService.STATUS_CONFIRMED)
                .bookingRef("BK-BBBB2222")
                .build();

        when(eventRepository.findById(ticketCategory.getEvent().getId())).thenReturn(Optional.of(ticketCategory.getEvent()));
        when(bookingRepository.findByTicketCategory_Event_IdAndStatusNot(ticketCategory.getEvent().getId(), BookingService.STATUS_CANCELLED))
                .thenReturn(java.util.List.of(first, second));
        when(bookingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketCategoryRepository.save(any(TicketCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        java.util.List<BookingResponse> response = bookingService.cancelAllForEvent(ticketCategory.getEvent().getId(), organiser);

        assertThat(response).hasSize(2);
        assertThat(response).allMatch(r -> r.status().equals(BookingService.STATUS_CANCELLED));
        assertThat(ticketCategory.getAvailableSeats()).isEqualTo(13);
    }

    @Test
    void cancel_restoresSeats() {
        ticketCategory.setAvailableSeats(5);
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .user(attendee)
                .ticketCategory(ticketCategory)
                .quantity(2)
                .status(BookingService.STATUS_CONFIRMED)
                .bookingRef("BK-ABCDEF12")
                .build();

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.cancel(booking.getId(), attendee);

        assertThat(response.status()).isEqualTo(BookingService.STATUS_CANCELLED);
        assertThat(ticketCategory.getAvailableSeats()).isEqualTo(7);
    }

    @Test
    void cancel_alreadyCancelled_throwsBadRequest() {
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .user(attendee)
                .ticketCategory(ticketCategory)
                .quantity(2)
                .status(BookingService.STATUS_CANCELLED)
                .bookingRef("BK-ABCDEF12")
                .build();

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(booking.getId(), attendee))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancel_notOwner_throwsForbidden() {
        User otherUser = User.builder().id(UUID.randomUUID()).email("other@eventzone.com").role("ATTENDEE").name("Other").passwordHash("h").build();

        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .user(attendee)
                .ticketCategory(ticketCategory)
                .quantity(2)
                .status(BookingService.STATUS_CONFIRMED)
                .bookingRef("BK-ABCDEF12")
                .build();

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(booking.getId(), otherUser))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancelAllForEvent_whenUnauthorized_throwsForbidden() {
        User otherUser = User.builder().id(UUID.randomUUID()).email("other@eventzone.com").role("ATTENDEE").name("Other").passwordHash("h").build();
        when(eventRepository.findById(ticketCategory.getEvent().getId())).thenReturn(Optional.of(ticketCategory.getEvent()));

        assertThatThrownBy(() -> bookingService.cancelAllForEvent(ticketCategory.getEvent().getId(), otherUser))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancelAllForEvent_whenNoBookings_returnsEmptyList() {
        User organiser = ticketCategory.getEvent().getOrganiser();
        when(eventRepository.findById(ticketCategory.getEvent().getId())).thenReturn(Optional.of(ticketCategory.getEvent()));
        when(bookingRepository.findByTicketCategory_Event_IdAndStatusNot(ticketCategory.getEvent().getId(), BookingService.STATUS_CANCELLED))
                .thenReturn(java.util.List.of());

        java.util.List<BookingResponse> result = bookingService.cancelAllForEvent(ticketCategory.getEvent().getId(), organiser);

        assertThat(result).isEmpty();
    }
}
