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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String ROLE_ATTENDEE = "ATTENDEE";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        log.info("Attempting registration for email={}", email);
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration rejected because account already exists for email={}", email);
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(ROLE_ATTENDEE)
                .name(request.name())
                .build();

        User saved = userRepository.save(user);
        log.info("Registration succeeded for email={} userId={}", saved.getEmail(), saved.getId());
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getName(), saved.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        log.info("Login attempt received for email={}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login failed: email not found for email={}", email);
                    return new UnauthorizedException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for email={}", email);
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        log.info("Login succeeded for email={} role={}", user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getName(), user.getRole(), user.getEmail());
    }
}
