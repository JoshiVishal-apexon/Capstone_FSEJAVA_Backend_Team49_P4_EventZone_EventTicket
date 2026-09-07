package com.eventzone.controller;

import com.eventzone.dto.auth.AuthResponse;
import com.eventzone.dto.auth.LoginRequest;
import com.eventzone.dto.auth.RegisterRequest;
import com.eventzone.dto.auth.UserResponse;
import com.eventzone.dto.common.ErrorResponse;
import com.eventzone.service.AuthService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful response"),
        @ApiResponse(responseCode = "400", description = "Validation or bad request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt received for email={}", request.email());
        UserResponse response = authService.register(request);
        log.info("Registration succeeded for email={} userId={}", request.email(), response.id());
        return response;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt received for email={}", request.email());
        AuthResponse response = authService.login(request);
        log.info("Login succeeded for email={} role={}", response.email(), response.role());
        return response;
    }

    /**
     * Stateless JWT means there is no server-side session to invalidate;
     * the client is simply expected to discard its token. This endpoint
     * exists so the frontend has a symmetrical call to make on logout.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        log.info("Logout request received; JWT logout is stateless and no server-side session is invalidated.");
        return ResponseEntity.noContent().build();
    }
}
