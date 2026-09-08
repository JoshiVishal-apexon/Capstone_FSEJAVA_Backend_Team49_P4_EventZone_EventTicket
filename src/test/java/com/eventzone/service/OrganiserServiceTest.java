package com.eventzone.service;

import com.eventzone.dto.organiser.OrganiserEventResponse;
import com.eventzone.dto.organiser.OrganiserTicketCategoryResponse;
import com.eventzone.entity.Booking;
import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.repository.BookingRepository;
import com.eventzone.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganiserServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private BookingRepository bookingRepository;

    private OrganiserService organiserService;
    private User organiser;

    @BeforeEach
    void setUp() {
        organiserService = new OrganiserService(eventRepository, bookingRepository);
        organiser = User.builder().id(UUID.randomUUID()).email("organiser1@eventzone.com").role("ORGANISER").name("Organizer").passwordHash("hash").build();
    }

    @Test
    void myEvents_returnsEventsWithBookedSeatTotals() {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .title("Tech Expo")
                .description("desc")
                .eventDate(LocalDateTime.now().plusDays(10))
                .venue("NCR")
                .organiser(organiser)
                .category(EventCategory.builder().id(UUID.randomUUID()).name("Tech").build())
                .active(true)
                .ticketCategories(new ArrayList<>())
                .build();

        TicketCategory vip = TicketCategory.builder()
                .id(UUID.randomUUID())
                .event(event)
                .name("VIP")
                .price(new BigDecimal("150.00"))
                .totalSeats(50)
                .availableSeats(30)
                .build();
        event.getTicketCategories().add(vip);

        Booking first = Booking.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(UUID.randomUUID()).email("attendee1@eventzone.com").role("ATTENDEE").name("User1").passwordHash("hash").build())
                .ticketCategory(vip)
                .quantity(10)
                .status("CONFIRMED")
                .bookingRef("BK-12345678")
                .build();

        Booking second = Booking.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(UUID.randomUUID()).email("user2@eventzone.com").role("ATTENDEE").name("User2").passwordHash("hash").build())
                .ticketCategory(vip)
                .quantity(5)
                .status("CONFIRMED")
                .bookingRef("BK-87654321")
                .build();

        when(eventRepository.findByOrganiser_Id(organiser.getId())).thenReturn(List.of(event));
        when(bookingRepository.findByTicketCategory_IdAndStatusNot(vip.getId(), BookingService.STATUS_CANCELLED))
                .thenReturn(List.of(first, second));

        List<OrganiserEventResponse> result = organiserService.myEvents(organiser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Tech Expo");
        assertThat(result.get(0).categoryName()).isEqualTo("Tech");
        assertThat(result.get(0).ticketCategories()).hasSize(1);

        OrganiserTicketCategoryResponse ticket = result.get(0).ticketCategories().get(0);
        assertThat(ticket.name()).isEqualTo("VIP");
        assertThat(ticket.totalBooked()).isEqualTo(15L);
        assertThat(ticket.availableSeats()).isEqualTo(30);
    }
}
