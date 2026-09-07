package com.eventzone.controller;

import com.eventzone.dto.common.ErrorResponse;
import com.eventzone.dto.organiser.OrganiserEventResponse;
import com.eventzone.entity.User;
import com.eventzone.security.SecurityUtils;
import com.eventzone.service.OrganiserService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organiser")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANISER')")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful response"),
        @ApiResponse(responseCode = "400", description = "Validation or bad request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class OrganiserController {

    private final OrganiserService organiserService;

    @GetMapping("/events")
    public List<OrganiserEventResponse> myEvents() {
        User currentUser = SecurityUtils.currentUser();
        return organiserService.myEvents(currentUser);
    }
}
