package com.eventzone.config;

import com.eventzone.entity.User;
import com.eventzone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the demo accounts used by the auth APIs exist with the expected role,
 * name, and password. Existing rows are normalized rather than duplicated, so
 * restarting the app always leaves a usable ADMIN / ORGANISER / ATTENDEE login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String SEED_PASSWORD = "Password@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("EventZone: seeding or normalizing auth accounts...");

        User admin = saveOrUpdateUser("admin@eventzone.com", "Admin User", "ADMIN");
        User organiser = saveOrUpdateUser("organiser1@eventzone.com", "Silverline Events", "ORGANISER");
        User attendee = saveOrUpdateUser("attendee1@eventzone.com", "Ava Carter", "ATTENDEE");

        log.info("=================================================================");
        log.info("EventZone auth accounts ready. Password for all: {}", SEED_PASSWORD);
        log.info("  ADMIN      -> {}", admin.getEmail());
        log.info("  ORGANISER  -> {}", organiser.getEmail());
        log.info("  ATTENDEE   -> {}", attendee.getEmail());
        log.info("=================================================================");
    }

    private User saveOrUpdateUser(String email, String name, String role) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setName(name);
                    existing.setRole(role);
                    existing.setPasswordHash(passwordEncoder.encode(SEED_PASSWORD));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(SEED_PASSWORD))
                        .role(role)
                        .name(name)
                        .build()));
    }
}
