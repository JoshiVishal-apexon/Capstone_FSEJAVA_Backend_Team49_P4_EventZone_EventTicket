package com.eventzone.service;

import com.eventzone.dto.auth.AuthResponse;
import com.eventzone.dto.auth.LoginRequest;
import com.eventzone.dto.auth.RegisterRequest;
import com.eventzone.dto.auth.UserResponse;
import com.eventzone.entity.User;
import com.eventzone.exception.ConflictException;
import com.eventzone.exception.UnauthorizedException;
import com.eventzone.repository.UserRepository;
import com.eventzone.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("existing@eventzone.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("existing@eventzone.com", "Password@123", "Existing User");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void register_newEmail_createsAttendee() {
        when(userRepository.existsByEmail("new@eventzone.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegisterRequest request = new RegisterRequest("new@eventzone.com", "Password@123", "New User");
        UserResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("new@eventzone.com");
        assertThat(response.role()).isEqualTo("ATTENDEE");
        assertThat(response.name()).isEqualTo("New User");
    }

    @Test
    void login_success_returnsToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("attendee1@eventzone.com")
                .passwordHash("hashed-password")
                .role("ATTENDEE")
                .name("Ava Carter")
                .build();

        when(userRepository.findByEmail("attendee1@eventzone.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hashed-password")).thenReturn(true);
        when(jwtUtil.generateToken("attendee1@eventzone.com", "ATTENDEE")).thenReturn("signed-jwt-token");

        AuthResponse response = authService.login(new LoginRequest("attendee1@eventzone.com", "Password@123"));

        assertThat(response.token()).isEqualTo("signed-jwt-token");
        assertThat(response.role()).isEqualTo("ATTENDEE");
        assertThat(response.email()).isEqualTo("attendee1@eventzone.com");
        assertThat(response.name()).isEqualTo("Ava Carter");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("attendee1@eventzone.com")
                .passwordHash("hashed-password")
                .role("ATTENDEE")
                .name("Ava Carter")
                .build();

        when(userRepository.findByEmail("attendee1@eventzone.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("attendee1@eventzone.com", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("nobody@eventzone.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@eventzone.com", "whatever")))
                .isInstanceOf(UnauthorizedException.class);
    }
}
