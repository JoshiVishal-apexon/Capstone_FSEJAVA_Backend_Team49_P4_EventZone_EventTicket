package com.eventzone.controller;

import com.eventzone.dto.auth.AuthResponse;
import com.eventzone.dto.auth.LoginRequest;
import com.eventzone.dto.auth.RegisterRequest;
import com.eventzone.dto.auth.UserResponse;
import com.eventzone.dto.booking.BookingRequest;
import com.eventzone.dto.booking.BookingResponse;
import com.eventzone.dto.category.CategoryRequest;
import com.eventzone.dto.category.CategoryResponse;
import com.eventzone.dto.event.EventCreateRequest;
import com.eventzone.dto.event.EventDetailResponse;
import com.eventzone.dto.event.EventSummaryResponse;
import com.eventzone.dto.event.EventUpdateRequest;
import com.eventzone.dto.organiser.OrganiserEventResponse;
import com.eventzone.dto.organiser.OrganiserTicketCategoryResponse;
import com.eventzone.dto.ticketcategory.TicketCategoryRequest;
import com.eventzone.dto.ticketcategory.TicketCategoryResponse;
import com.eventzone.entity.User;
import com.eventzone.security.EventZoneUserPrincipal;
import com.eventzone.service.AuthService;
import com.eventzone.service.BookingService;
import com.eventzone.service.CategoryService;
import com.eventzone.service.EventService;
import com.eventzone.service.OrganiserService;
import com.eventzone.service.TicketCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControllerCoverageTest {

    @Mock
    private AuthService authService;

    @Mock
    private BookingService bookingService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private EventService eventService;

    @Mock
    private OrganiserService organiserService;

    @Mock
    private TicketCategoryService ticketCategoryService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authController_registerLoginAndLogout() {
        AuthController controller = new AuthController(authService);
        RegisterRequest registerRequest = new RegisterRequest("new@eventzone.com", "Password@123", "New User");
        UserResponse userResponse = new UserResponse(UUID.randomUUID(), "new@eventzone.com", "New User", "ATTENDEE");
        when(authService.register(registerRequest)).thenReturn(userResponse);

        assertThat(controller.register(registerRequest)).isEqualTo(userResponse);

        LoginRequest loginRequest = new LoginRequest("new@eventzone.com", "Password@123");
        AuthResponse authResponse = new AuthResponse("jwt-token", "new@eventzone.com", "ATTENDEE", "New User");
        when(authService.login(loginRequest)).thenReturn(authResponse);

        assertThat(controller.login(loginRequest)).isEqualTo(authResponse);

        ResponseEntity<Void> logout = controller.logout();
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void eventController_listGetCreateUpdateAndDelete() {
        EventController controller = new EventController(eventService);
        User organiser = user("organiser@eventzone.com", "ORGANISER");
        setCurrentUser(organiser);

        EventSummaryResponse summary = new EventSummaryResponse(
                UUID.randomUUID(),
                "Neon Pulse Live",
                "Concert",
                LocalDateTime.of(2026, 9, 14, 19, 0),
                "Bharat Mandapam",
                true,
                "cover.jpg",
                new BigDecimal("999.00"),
                new BigDecimal("2999.00")
        );
        when(eventService.listActive("Concert")).thenReturn(List.of(summary));
        assertThat(controller.list("Concert")).containsExactly(summary);

        EventDetailResponse detail = new EventDetailResponse(
                summary.id(),
                summary.title(),
                "A concert description",
                "Concert",
                summary.eventDate(),
                summary.venue(),
                summary.coverImageUrl(),
                true,
                "Skyline Events",
                List.of()
        );
        when(eventService.getDetail(summary.id())).thenReturn(detail);
        assertThat(controller.getById(summary.id())).isEqualTo(detail);

        EventCreateRequest createRequest = new EventCreateRequest(
                "New event",
                "Event description",
                LocalDateTime.now().plusDays(10),
                "Venue",
                "cover-url",
                UUID.randomUUID()
        );
        when(eventService.create(createRequest, organiser)).thenReturn(detail);
        assertThat(controller.create(createRequest)).isEqualTo(detail);

        EventUpdateRequest updateRequest = new EventUpdateRequest(
                "Updated title",
                "Updated description",
                LocalDateTime.now().plusDays(11),
                "Updated venue",
                "updated-cover",
                UUID.randomUUID()
        );
        when(eventService.update(summary.id(), updateRequest, organiser)).thenReturn(detail);
        assertThat(controller.update(summary.id(), updateRequest)).isEqualTo(detail);

        ResponseEntity<Void> deleted = controller.delete(summary.id());
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void bookingController_bookMineCancelAndCancelAllForEvent() {
        BookingController controller = new BookingController(bookingService);
        User attendee = user("attendee@eventzone.com", "ATTENDEE");
        setCurrentUser(attendee);

        UUID ticketCategoryId = UUID.randomUUID();
        BookingRequest bookingRequest = new BookingRequest(ticketCategoryId, 2);
        BookingResponse bookingResponse = new BookingResponse(
                UUID.randomUUID(),
                "EVT-1024",
                ticketCategoryId,
                "General",
                UUID.randomUUID(),
                "Neon Pulse Live",
                2,
                new BigDecimal("999.00"),
                "CONFIRMED",
                LocalDateTime.now()
        );
        when(bookingService.book(bookingRequest, attendee)).thenReturn(bookingResponse);
        assertThat(controller.book(bookingRequest)).isEqualTo(bookingResponse);

        when(bookingService.findMine(attendee)).thenReturn(List.of(bookingResponse));
        assertThat(controller.mine()).containsExactly(bookingResponse);

        when(bookingService.cancel(bookingResponse.id(), attendee)).thenReturn(bookingResponse);
        assertThat(controller.cancel(bookingResponse.id())).isEqualTo(bookingResponse);

        UUID eventId = UUID.randomUUID();
        when(bookingService.cancelAllForEvent(eventId, attendee)).thenReturn(List.of(bookingResponse));
        assertThat(controller.cancelAllForEvent(eventId)).containsExactly(bookingResponse);
    }

    @Test
    void categoryController_andAdminCategoryController_crud() {
        CategoryController publicController = new CategoryController(categoryService);
        CategoryResponse categoryResponse = new CategoryResponse(UUID.randomUUID(), "Concert");
        when(categoryService.listAll()).thenReturn(List.of(categoryResponse));
        assertThat(publicController.listAll()).containsExactly(categoryResponse);

        AdminCategoryController adminController = new AdminCategoryController(categoryService);
        CategoryRequest createRequest = new CategoryRequest("Workshop");
        when(categoryService.create(createRequest)).thenReturn(categoryResponse);
        assertThat(adminController.create(createRequest)).isEqualTo(categoryResponse);

        when(categoryService.update(categoryResponse.id(), createRequest)).thenReturn(categoryResponse);
        assertThat(adminController.update(categoryResponse.id(), createRequest)).isEqualTo(categoryResponse);

        ResponseEntity<Void> deleted = adminController.delete(categoryResponse.id());
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void adminEventController_listDeactivateAndActivate() {
        AdminEventController controller = new AdminEventController(eventService);
        UUID eventId = UUID.randomUUID();
        EventSummaryResponse summary = new EventSummaryResponse(
                eventId,
                "Event Name",
                "Concert",
                LocalDateTime.of(2026, 10, 2, 18, 30),
                "Venue",
                true,
                "cover.jpg",
                new BigDecimal("100.00"),
                new BigDecimal("500.00")
        );
        EventDetailResponse detail = new EventDetailResponse(
                eventId,
                "Event Name",
                "Desc",
                "Concert",
                summary.eventDate(),
                summary.venue(),
                summary.coverImageUrl(),
                false,
                "Admin",
                List.of()
        );

        when(eventService.listAllEvents("Concert")).thenReturn(List.of(summary));
        assertThat(controller.list("Concert")).containsExactly(summary);

        when(eventService.getDetail(eventId)).thenReturn(detail);
        assertThat(controller.deactivate(eventId)).isEqualTo(detail);

        when(eventService.getDetail(eventId)).thenReturn(detail);
        assertThat(controller.activate(eventId)).isEqualTo(detail);
    }

    @Test
    void organiserController_andTicketCategoryController_crud() {
        User organiser = user("organiser@eventzone.com", "ORGANISER");
        setCurrentUser(organiser);

        OrganiserController organiserController = new OrganiserController(organiserService);
        OrganiserTicketCategoryResponse ticket = new OrganiserTicketCategoryResponse(
                UUID.randomUUID(),
                "General",
                new BigDecimal("999.00"),
                200,
                180,
                20L
        );
        OrganiserEventResponse organiserEvent = new OrganiserEventResponse(
                UUID.randomUUID(),
                "Neon Pulse Live",
                "Concert",
                LocalDateTime.of(2026, 9, 14, 19, 0),
                "Bharat Mandapam",
                true,
                List.of(ticket)
        );
        when(organiserService.myEvents(organiser)).thenReturn(List.of(organiserEvent));
        assertThat(organiserController.myEvents()).containsExactly(organiserEvent);

        TicketCategoryController ticketController = new TicketCategoryController(ticketCategoryService);
        UUID eventId = UUID.randomUUID();
        UUID ticketCategoryId = UUID.randomUUID();
        TicketCategoryRequest createRequest = new TicketCategoryRequest("VIP", new BigDecimal("1999.00"), 50);
        TicketCategoryResponse created = new TicketCategoryResponse(ticketCategoryId, "VIP", new BigDecimal("1999.00"), 50, 50);
        when(ticketCategoryService.create(eventId, createRequest, organiser)).thenReturn(created);
        assertThat(ticketController.create(eventId, createRequest)).isEqualTo(created);

        when(ticketCategoryService.update(ticketCategoryId, createRequest, organiser)).thenReturn(created);
        assertThat(ticketController.update(ticketCategoryId, createRequest)).isEqualTo(created);

        ResponseEntity<Void> deleted = ticketController.delete(ticketCategoryId);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private void setCurrentUser(User user) {
        var principal = new EventZoneUserPrincipal(user);
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                "token",
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User user(String email, String role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed")
                .name("Test User")
                .role(role)
                .build();
    }
}
