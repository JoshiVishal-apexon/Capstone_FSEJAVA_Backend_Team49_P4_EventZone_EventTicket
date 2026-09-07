package com.eventzone.controller;

import com.eventzone.dto.common.ErrorResponse;
import com.eventzone.dto.ticketcategory.TicketCategoryRequest;
import com.eventzone.dto.ticketcategory.TicketCategoryResponse;
import com.eventzone.entity.User;
import com.eventzone.security.SecurityUtils;
import com.eventzone.service.TicketCategoryService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANISER')")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful response"),
        @ApiResponse(responseCode = "400", description = "Validation or bad request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TicketCategoryController {

    private final TicketCategoryService ticketCategoryService;

    @PostMapping("/api/events/{eventId}/ticket-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketCategoryResponse create(@PathVariable UUID eventId, @Valid @RequestBody TicketCategoryRequest request) {
        User currentUser = SecurityUtils.currentUser();
        return ticketCategoryService.create(eventId, request, currentUser);
    }

    @PutMapping("/api/ticket-categories/{id}")
    public TicketCategoryResponse update(@PathVariable UUID id, @Valid @RequestBody TicketCategoryRequest request) {
        User currentUser = SecurityUtils.currentUser();
        return ticketCategoryService.update(id, request, currentUser);
    }

    @DeleteMapping("/api/ticket-categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        User currentUser = SecurityUtils.currentUser();
        ticketCategoryService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
