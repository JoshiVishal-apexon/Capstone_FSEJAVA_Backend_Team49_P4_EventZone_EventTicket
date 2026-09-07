package com.eventzone.service;

import com.eventzone.dto.event.EventCreateRequest;
import com.eventzone.dto.event.EventDetailResponse;
import com.eventzone.dto.event.EventUpdateRequest;
import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
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

import java.time.LocalDateTime;
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

        organiser = User.builder().id(UUID.randomUUID()).email("org1@eventzone.com").role("ORGANISER").name("Arjun Events").passwordHash("h").build();
        otherOrganiser = User.builder().id(UUID.randomUUID()).email("org2@eventzone.com").role("ORGANISER").name("Priya Productions").passwordHash("h").build();
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
