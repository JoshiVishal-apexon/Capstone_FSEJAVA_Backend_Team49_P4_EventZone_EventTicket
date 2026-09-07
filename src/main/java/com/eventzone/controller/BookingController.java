package com.eventzone.controller;

import com.eventzone.dto.booking.BookingRequest;
import com.eventzone.dto.booking.BookingResponse;
import com.eventzone.dto.common.ErrorResponse;
import com.eventzone.entity.User;
import com.eventzone.security.SecurityUtils;
import com.eventzone.service.BookingService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful response"),
        @ApiResponse(responseCode = "400", description = "Validation or bad request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@Valid @RequestBody BookingRequest request) {
        User currentUser = SecurityUtils.currentUser();
        return bookingService.book(request, currentUser);
    }

    @GetMapping("/mine")
    public List<BookingResponse> mine() {
        User currentUser = SecurityUtils.currentUser();
        return bookingService.findMine(currentUser);
    }

    @PutMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable UUID id) {
        User currentUser = SecurityUtils.currentUser();
        return bookingService.cancel(id, currentUser);
    }

    @PutMapping("/event/{eventId}/cancel")
    @PreAuthorize("hasRole('ORGANISER') or hasRole('ADMIN')")
    public List<BookingResponse> cancelAllForEvent(@PathVariable UUID eventId) {
        User currentUser = SecurityUtils.currentUser();
        return bookingService.cancelAllForEvent(eventId, currentUser);
    }
}
