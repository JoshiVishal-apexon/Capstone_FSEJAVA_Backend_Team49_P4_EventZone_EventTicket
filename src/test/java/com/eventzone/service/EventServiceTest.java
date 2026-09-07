package com.eventzone.service;

import com.eventzone.dto.event.EventDetailResponse;
import com.eventzone.dto.event.EventSummaryResponse;
import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.exception.ResourceNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    private EventService eventService;

    private User organiser;
    private EventCategory category;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository);
        organiser = User.builder().id(UUID.randomUUID()).email("org1@eventzone.com").role("ORGANISER")
                .name("Arjun Events").passwordHash("h").build();
        category = EventCategory.builder().id(UUID.randomUUID()).name("Concert").build();
    }

    @Test
    void listActive_withoutCategory_returnsEventsSortedByDate() {
        Event later = event("Later event", LocalDateTime.now().plusDays(20));
        Event sooner = event("Sooner event", LocalDateTime.now().plusDays(5));
        when(eventRepository.findByActiveTrue()).thenReturn(List.of(later, sooner));

        List<EventSummaryResponse> result = eventService.listActive(null);

        assertThat(result).extracting(EventSummaryResponse::title)
                .containsExactly("Sooner event", "Later event");
    }

    @Test
    void listActive_withCategory_filtersByCategoryName() {
        Event concert = event("Sunburn Arena", LocalDateTime.now().plusDays(10));
        when(eventRepository.findByActiveTrueAndCategory_NameIgnoreCase("Concert")).thenReturn(List.of(concert));

        List<EventSummaryResponse> result = eventService.listActive("Concert");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryName()).isEqualTo("Concert");
    }

    @Test
    void listActive_exposesMinAndMaxTicketPrice() {
        Event concert = event("Sunburn Arena", LocalDateTime.now().plusDays(10));
        concert.getTicketCategories().add(ticket(concert, "General", "999.00"));
        concert.getTicketCategories().add(ticket(concert, "VIP", "2999.00"));
        when(eventRepository.findByActiveTrue()).thenReturn(List.of(concert));

        EventSummaryResponse summary = eventService.listActive(null).get(0);

        assertThat(summary.minPrice()).isEqualByComparingTo("999.00");
        assertThat(summary.maxPrice()).isEqualByComparingTo("2999.00");
    }

    @Test
    void getDetail_returnsEventWithTicketCategories() {
        Event concert = event("Sunburn Arena", LocalDateTime.now().plusDays(10));
        concert.getTicketCategories().add(ticket(concert, "General", "999.00"));
        when(eventRepository.findById(concert.getId())).thenReturn(Optional.of(concert));

        EventDetailResponse detail = eventService.getDetail(concert.getId());

        assertThat(detail.title()).isEqualTo("Sunburn Arena");
        assertThat(detail.organiserName()).isEqualTo(organiser.getName());
        assertThat(detail.ticketCategories()).extracting("name").containsExactly("General");
    }

    @Test
    void getDetail_whenEventMissing_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getDetail(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Event not found");
    }

    private Event event(String title, LocalDateTime eventDate) {
        return Event.builder()
                .id(UUID.randomUUID())
                .title(title)
                .description("desc")
                .eventDate(eventDate)
                .venue("Some venue")
                .organiser(organiser)
                .category(category)
                .active(true)
                .ticketCategories(new ArrayList<>())
                .build();
    }

    private TicketCategory ticket(Event event, String name, String price) {
        return TicketCategory.builder()
                .id(UUID.randomUUID())
                .event(event)
                .name(name)
                .price(new BigDecimal(price))
                .totalSeats(100)
                .availableSeats(100)
                .build();
    }
}
