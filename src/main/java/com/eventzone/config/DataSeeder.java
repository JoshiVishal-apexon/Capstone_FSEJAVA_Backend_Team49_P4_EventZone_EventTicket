package com.eventzone.config;

import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
import com.eventzone.entity.TicketCategory;
import com.eventzone.entity.User;
import com.eventzone.repository.EventCategoryRepository;
import com.eventzone.repository.EventRepository;
import com.eventzone.repository.TicketCategoryRepository;
import com.eventzone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Seeds a small, deterministic dataset on first startup only (checks
 * whether the users table is empty first) so that restarting the app
 * against the same H2 file never creates duplicates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String SEED_PASSWORD = "Password@123";

    private final UserRepository userRepository;
    private final EventCategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("EventZone: data already present, skipping seed.");
            return;
        }

        log.info("EventZone: seeding initial data...");

        User admin = saveUser("admin@eventzone.com", "Admin", "ADMIN");
        User organiser1 = saveUser("skyline@eventzone.com", "Skyline Events", "ORGANISER");
        User organiser2 = saveUser("nova@eventzone.com", "Nova Productions", "ORGANISER");
        User attendee = saveUser("attendee1@eventzone.com", "Aarav", "ATTENDEE");

        Map<String, EventCategory> categories = new HashMap<>();
        for (String name : new String[]{"Concert", "Sports", "Workshop", "Conference"}) {
            categories.put(name, categoryRepository.save(EventCategory.builder().name(name).build()));
        }

        // Dates are deliberately set after this seeder's "today" so they remain
        // genuinely upcoming (the EventCreateRequest DTO enforces @Future on
        // organiser-submitted dates via the API; seeded rows bypass that DTO
        // but should still look realistic in the UI).
        createEvent(organiser1, categories.get("Concert"), "Neon Pulse Live",
                "A high-energy live concert featuring electronic and pop acts.",
                LocalDateTime.of(2026, 9, 14, 19, 0), "Bharat Mandapam, New Delhi",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSkpbb5uqg3lUI_krb40UPhT0Hp8qnAl5Jf_ydGbEZweA&s=10",
                new TicketCategoryDef("General", new BigDecimal("999.00"), 500),
                new TicketCategoryDef("VIP", new BigDecimal("2999.00"), 100));

        createEvent(organiser1, categories.get("Concert"), "Midnight Sessions",
                "An intimate acoustic evening featuring indie artists and storytellers.",
                LocalDateTime.of(2026, 10, 5, 18, 30), "Phoenix Marketcity, Bengaluru",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTv7_GoxndSe4IKfyv9uWrLcdyYF5KaLB_tTfZFVNtTYQ&s=10",
                new TicketCategoryDef("General", new BigDecimal("499.00"), 300),
                new TicketCategoryDef("VIP", new BigDecimal("1499.00"), 60));

        createEvent(organiser2, categories.get("Sports"), "Run for Glory 2026",
                "A citywide 21K run open to all skill levels.",
                LocalDateTime.of(2026, 1, 25, 6, 0), "Marine Drive Promenade, Mumbai",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSOeB7YmhbtO0iNYLneWu__rNsv1IPIODNizG7MaaVTTA&s=10",
                new TicketCategoryDef("General", new BigDecimal("299.00"), 2000),
                new TicketCategoryDef("VIP", new BigDecimal("999.00"), 200));

        createEvent(organiser2, categories.get("Sports"), "Champions Cup Final",
                "The season finale of the Champions Cup. Witness the winners crowned.",
                LocalDateTime.of(2026, 11, 12, 15, 0), "Eden Gardens, Kolkata",
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR8EzZ-14reAIKdlKdCGUth0YuLr8R3yWzwK8ng0_ERqg&s=10",
                new TicketCategoryDef("General", new BigDecimal("799.00"), 5000),
                new TicketCategoryDef("VIP", new BigDecimal("4999.00"), 500));

        createEvent(organiser2, categories.get("Conference"), "FutureStack Summit 2027",
                "Industry leaders discuss cloud, AI, and platform engineering trends.",
                LocalDateTime.of(2027, 2, 20, 9, 30), "HITEX Exhibition Centre, Hyderabad",
                "https://wext.in/wp-content/uploads/2026/02/AI-Impact-Summit-2026.png",
                new TicketCategoryDef("General", new BigDecimal("1999.00"), 1000),
                new TicketCategoryDef("VIP", new BigDecimal("5999.00"), 150));

        log.info("=================================================================");
        log.info("EventZone seed data created. Login credentials (password for all: {}):", SEED_PASSWORD);
        log.info("  ADMIN      -> {}", admin.getEmail());
        log.info("  ORGANISER  -> {}", organiser1.getEmail());
        log.info("  ORGANISER  -> {}", organiser2.getEmail());
        log.info("  ATTENDEE   -> {}", attendee.getEmail());
        log.info("=================================================================");
    }

    private User saveUser(String email, String name, String role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(SEED_PASSWORD))
                .role(role)
                .name(name)
                .build());
    }

    private void createEvent(User organiser, EventCategory category, String title, String description,
                              LocalDateTime eventDate, String venue, String coverImageUrl,
                              TicketCategoryDef... ticketCategoryDefs) {
        Event event = eventRepository.save(Event.builder()
                .title(title)
                .description(description)
                .eventDate(eventDate)
                .venue(venue)
                .coverImageUrl(coverImageUrl)
                .organiser(organiser)
                .category(category)
                .active(true)
                .build());

        for (TicketCategoryDef def : ticketCategoryDefs) {
            ticketCategoryRepository.save(TicketCategory.builder()
                    .event(event)
                    .name(def.name())
                    .price(def.price())
                    .totalSeats(def.totalSeats())
                    .availableSeats(def.totalSeats())
                    .build());
        }
    }

    private record TicketCategoryDef(String name, BigDecimal price, int totalSeats) {
    }
}
