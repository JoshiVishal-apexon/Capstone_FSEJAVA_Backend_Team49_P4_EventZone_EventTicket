package com.eventzone.service;

import com.eventzone.dto.event.EventCreateRequest;
import com.eventzone.dto.event.EventDetailResponse;
import com.eventzone.dto.event.EventSummaryResponse;
import com.eventzone.dto.event.EventUpdateRequest;
import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.exception.ConflictException;
import com.eventzone.exception.ForbiddenException;
import com.eventzone.repository.BookingRepository;
import com.eventzone.repository.EventCategoryRepository;
import com.eventzone.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventCategoryRepository categoryRepository;

    @Mock
    private BookingRepository bookingRepository;

    private EventService eventService;

    private User organiser;
    private User otherOrganiser;
    private User admin;
    private EventCategory category;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, categoryRepository, bookingRepository);

        organiser = User.builder().id(UUID.randomUUID()).email("organiser1@eventzone.com").role("ORGANISER").name("Skyline Events").passwordHash("h").build();
        otherOrganiser = User.builder().id(UUID.randomUUID()).email("organiser2@eventzone.com").role("ORGANISER").name("Nova Productions").passwordHash("h").build();
        admin = User.builder().id(UUID.randomUUID()).email("admin@eventzone.com").role("ADMIN").name("Admin").passwordHash("h").build();
        category = EventCategory.builder().id(UUID.randomUUID()).name("Concert").build();
    }

    @Test
    void create_associatesCorrectOrganiser() {
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        EventCreateRequest request = new EventCreateRequest(
                "Sunburn Arena", "desc", LocalDateTime.now().plusDays(30), "Delhi", null, category.getId());

        EventDetailResponse response = eventService.create(request, organiser);

        assertThat(response.title()).isEqualTo("Sunburn Arena");
        assertThat(response.organiserName()).isEqualTo(organiser.getName());
        assertThat(response.active()).isTrue();

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        org.mockito.Mockito.verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganiser()).isEqualTo(organiser);
    }

    @Test
    void update_byOwningOrganiser_succeeds() {
        Event event = existingEvent();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventUpdateRequest request = new EventUpdateRequest(
                "Updated title", "desc", LocalDateTime.now().plusDays(10), "New venue", null, category.getId());

        EventDetailResponse response = eventService.update(event.getId(), request, organiser);

        assertThat(response.title()).isEqualTo("Updated title");
    }

    @Test
    void update_byAdmin_succeeds() {
        Event event = existingEvent();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventUpdateRequest request = new EventUpdateRequest(
                "Updated by admin", "desc", LocalDateTime.now().plusDays(10), "New venue", null, category.getId());

        EventDetailResponse response = eventService.update(event.getId(), request, admin);

        assertThat(response.title()).isEqualTo("Updated by admin");
    }

    @Test
    void update_byNonOwningOrganiser_throwsForbidden() {
        Event event = existingEvent();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        EventUpdateRequest request = new EventUpdateRequest(
                "Hijacked title", "desc", LocalDateTime.now().plusDays(10), "New venue", null, category.getId());

        assertThatThrownBy(() -> eventService.update(event.getId(), request, otherOrganiser))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_byNonOwningOrganiser_throwsForbidden() {
        Event event = existingEvent();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.delete(event.getId(), otherOrganiser))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_byOwningOrganiser_succeeds() {
        Event event = existingEvent();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(bookingRepository.findFirstByTicketCategory_Event_Id(event.getId())).thenReturn(null);

        eventService.delete(event.getId(), organiser);

        org.mockito.Mockito.verify(eventRepository).delete(event);
    }

    @Test
    void delete_whenBookingsExist_throwsConflict() {
        Event event = existingEvent();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(bookingRepository.findFirstByTicketCategory_Event_Id(event.getId())).thenReturn(bookingSample());

        assertThatThrownBy(() -> eventService.delete(event.getId(), organiser))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot delete event 'Original title' because tickets have already been booked for it");
    }

    @Test
    void listActive_filtersByCategory() {
        Event event = existingEvent();
        when(eventRepository.findByActiveTrueAndCategory_NameIgnoreCase("Concert")).thenReturn(java.util.List.of(event));

        java.util.List<com.eventzone.dto.event.EventSummaryResponse> result = eventService.listActive("Concert");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Original title");
    }

    @Test
    void getDetail_returnsMappedEvent() {
        Event event = existingEvent();
        event.setTicketCategories(new java.util.ArrayList<>());
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        EventDetailResponse response = eventService.getDetail(event.getId());

        assertThat(response.id()).isEqualTo(event.getId());
        assertThat(response.title()).isEqualTo("Original title");
    }

    @Test
    void listActive_whenCategoryBlank_usesActiveQuery() {
        Event event = existingEvent();
        when(eventRepository.findByActiveTrue()).thenReturn(java.util.List.of(event));

        java.util.List<com.eventzone.dto.event.EventSummaryResponse> result = eventService.listActive("   ");

        assertThat(result).hasSize(1);
    }

    @Test
    void delete_whenCancelledBookingMarksEventInactive() {
        Event event = existingEvent();
        com.eventzone.entity.Booking booking = com.eventzone.entity.Booking.builder()
                .id(UUID.randomUUID())
                .ticketCategory(com.eventzone.entity.TicketCategory.builder().id(UUID.randomUUID()).event(event).name("G").price(java.math.BigDecimal.ONE).totalSeats(1).availableSeats(1).build())
                .quantity(1)
                .status("CANCELLED")
                .bookingRef("BK-CANCELLED")
                .build();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(bookingRepository.findFirstByTicketCategory_Event_Id(event.getId())).thenReturn(booking);
        when(bookingRepository.saveAndFlush(booking)).thenReturn(booking);
        when(eventRepository.saveAndFlush(event)).thenReturn(event);

        eventService.delete(event.getId(), organiser);

        assertThat(event.isActive()).isFalse();
    }

    @Test
    void listActive_whenCategoryIsNull_usesUnfilteredQuery() {
        Event event = existingEvent();
        when(eventRepository.findByActiveTrue()).thenReturn(List.of(event));

        List<EventSummaryResponse> result = eventService.listActive(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Original title");
    }

    @Test
    void listAllEvents_handlesNullAndFilteredCategoryRequests() {
        Event event = existingEvent();
        when(eventRepository.findAll()).thenReturn(List.of(event));
        when(eventRepository.findByActiveTrueAndCategory_NameIgnoreCase("Concert")).thenReturn(List.of(event));

        assertThat(eventService.listAllEvents(null)).hasSize(1);
        assertThat(eventService.listAllEvents("Concert")).hasSize(1);
    }

    @Test
    void toSummary_usesLowestAndHighestTicketPrices() {
        Event event = existingEvent();
        event.setTicketCategories(List.of(
                TicketCategory.builder().name("General").price(new BigDecimal("299.00")).build(),
                TicketCategory.builder().name("VIP").price(new BigDecimal("1499.00")).build(),
                TicketCategory.builder().name("Backstage").price(new BigDecimal("799.00")).build()
        ));

        when(eventRepository.findByActiveTrue()).thenReturn(List.of(event));

        List<EventSummaryResponse> result = eventService.listActive(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).minPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
        assertThat(result.get(0).maxPrice()).isEqualByComparingTo(new BigDecimal("1499.00"));
    }

    @Test
    void delete_whenBookingStatusIsPending_doesNotThrow() {
        Event event = existingEvent();
        com.eventzone.entity.Booking booking = com.eventzone.entity.Booking.builder()
                .id(UUID.randomUUID())
                .ticketCategory(TicketCategory.builder().id(UUID.randomUUID()).event(event).name("G").price(BigDecimal.ONE).totalSeats(1).availableSeats(1).build())
                .quantity(1)
                .status("PENDING")
                .bookingRef("BK-PENDING")
                .build();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(bookingRepository.findFirstByTicketCategory_Event_Id(event.getId())).thenReturn(booking);

        eventService.delete(event.getId(), organiser);

        assertThat(event.isActive()).isTrue();
    }

    private com.eventzone.entity.Booking bookingSample() {
        com.eventzone.entity.Booking b = com.eventzone.entity.Booking.builder()
                .id(UUID.randomUUID())
                .bookingRef("BK-EXIST123")
                .build();
        return b;
    }

    private Event existingEvent() {
        return Event.builder()
                .id(UUID.randomUUID())
                .title("Original title")
                .description("desc")
                .eventDate(LocalDateTime.now().plusDays(5))
                .venue("Original venue")
                .organiser(organiser)
                .category(category)
                .active(true)
                .build();
    }
}
