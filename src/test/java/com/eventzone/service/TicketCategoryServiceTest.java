package com.eventzone.service;

import com.eventzone.dto.ticketcategory.TicketCategoryRequest;
import com.eventzone.dto.ticketcategory.TicketCategoryResponse;
import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.exception.ForbiddenException;
import com.eventzone.exception.ResourceNotFoundException;
import com.eventzone.repository.TicketCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCategoryServiceTest {

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    @Mock
    private EventService eventService;

    private TicketCategoryService ticketCategoryService;
    private User organiser;
    private User otherUser;
    private Event event;

    @BeforeEach
    void setUp() {
        ticketCategoryService = new TicketCategoryService(ticketCategoryRepository, eventService);
        organiser = User.builder().id(UUID.randomUUID()).email("org@eventzone.com").role("ORGANISER").name("Organizer").passwordHash("hash").build();
        otherUser = User.builder().id(UUID.randomUUID()).email("other@eventzone.com").role("ORGANISER").name("Other").passwordHash("hash").build();
        event = Event.builder()
                .id(UUID.randomUUID())
                .title("Conference")
                .description("desc")
                .eventDate(LocalDateTime.now().plusDays(5))
                .venue("Bengaluru")
                .organiser(organiser)
                .category(EventCategory.builder().id(UUID.randomUUID()).name("Tech").build())
                .active(true)
                .build();
    }

    @Test
    void create_whenOwner_savesTicketCategory() {
        UUID eventId = event.getId();
        when(eventService.findEventOrThrow(eventId)).thenReturn(event);
        when(ticketCategoryRepository.save(any(TicketCategory.class))).thenAnswer(invocation -> {
            TicketCategory tc = invocation.getArgument(0);
            tc.setId(UUID.randomUUID());
            return tc;
        });

        TicketCategoryResponse result = ticketCategoryService.create(eventId, new TicketCategoryRequest("VIP", new BigDecimal("250.00"), 50), organiser);

        assertThat(result.name()).isEqualTo("VIP");
        assertThat(result.totalSeats()).isEqualTo(50);
        assertThat(result.availableSeats()).isEqualTo(50);
        verify(ticketCategoryRepository).save(any(TicketCategory.class));
    }

    @Test
    void create_whenNotOwner_throwsForbidden() {
        UUID eventId = event.getId();
        when(eventService.findEventOrThrow(eventId)).thenReturn(event);

        assertThatThrownBy(() -> ticketCategoryService.create(eventId, new TicketCategoryRequest("VIP", new BigDecimal("250.00"), 50), otherUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You do not have permission to manage ticket categories for this event");
    }

    @Test
    void update_whenOwner_updatesSeatsAndReturnsResponse() {
        UUID ticketCategoryId = UUID.randomUUID();
        TicketCategory ticketCategory = TicketCategory.builder()
                .id(ticketCategoryId)
                .event(event)
                .name("General")
                .price(new BigDecimal("120.00"))
                .totalSeats(10)
                .availableSeats(5)
                .build();
        when(ticketCategoryRepository.findById(ticketCategoryId)).thenReturn(Optional.of(ticketCategory));
        when(ticketCategoryRepository.save(any(TicketCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketCategoryResponse result = ticketCategoryService.update(ticketCategoryId, new TicketCategoryRequest("General", new BigDecimal("120.00"), 15), organiser);

        assertThat(result.totalSeats()).isEqualTo(15);
        assertThat(result.availableSeats()).isEqualTo(10);
        assertThat(ticketCategory.getAvailableSeats()).isEqualTo(10);
    }

    @Test
    void update_whenNotOwner_throwsForbidden() {
        UUID ticketCategoryId = UUID.randomUUID();
        TicketCategory ticketCategory = TicketCategory.builder()
                .id(ticketCategoryId)
                .event(event)
                .name("General")
                .price(new BigDecimal("120.00"))
                .totalSeats(10)
                .availableSeats(10)
                .build();
        when(ticketCategoryRepository.findById(ticketCategoryId)).thenReturn(Optional.of(ticketCategory));

        assertThatThrownBy(() -> ticketCategoryService.update(ticketCategoryId, new TicketCategoryRequest("General", new BigDecimal("120.00"), 12), otherUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You do not have permission to manage ticket categories for this event");
    }

    @Test
    void delete_whenOwner_deletesTicketCategory() {
        UUID ticketCategoryId = UUID.randomUUID();
        TicketCategory ticketCategory = TicketCategory.builder()
                .id(ticketCategoryId)
                .event(event)
                .name("General")
                .price(new BigDecimal("120.00"))
                .totalSeats(10)
                .availableSeats(10)
                .build();
        when(ticketCategoryRepository.findById(ticketCategoryId)).thenReturn(Optional.of(ticketCategory));

        ticketCategoryService.delete(ticketCategoryId, organiser);

        verify(ticketCategoryRepository).delete(ticketCategory);
    }

    @Test
    void delete_whenMissingTicketCategory_throwsResourceNotFound() {
        UUID ticketCategoryId = UUID.randomUUID();
        when(ticketCategoryRepository.findById(ticketCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketCategoryService.delete(ticketCategoryId, organiser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Ticket category not found");
    }
}
