package com.eventzone.controller;

import com.eventzone.dto.common.ErrorResponse;
import com.eventzone.dto.event.EventDetailResponse;
import com.eventzone.service.EventService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful response"),
        @ApiResponse(responseCode = "400", description = "Validation or bad request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AdminEventController {

    private final EventService eventService;

    @PutMapping("/{id}/deactivate")
    public EventDetailResponse deactivate(@PathVariable UUID id) {
        eventService.setActive(id, false);
        return eventService.getDetail(id);
    }

    @PutMapping("/{id}/activate")
    public EventDetailResponse activate(@PathVariable UUID id) {
        eventService.setActive(id, true);
        return eventService.getDetail(id);
    }
}
