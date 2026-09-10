package com.eventzone.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EntityLifecycleTest {

    @Test
    void userPrePersist_setsCreatedAt() {
        User user = User.builder().email("a@b.com").passwordHash("x").role("ATTENDEE").name("A").build();

        user.prePersist();

        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void bookingPrePersist_setsCreatedAt() {
        Booking booking = Booking.builder().quantity(1).status("CONFIRMED").bookingRef("BK-1").build();

        booking.prePersist();

        assertThat(booking.getCreatedAt()).isNotNull();
    }

    @Test
    void eventPrePersist_setsCreatedAt() {
        Event event = Event.builder().title("T").venue("V").active(true).build();

        event.prePersist();

        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    void ticketCategoryPrePersist_setsCreatedAt() {
        TicketCategory ticketCategory = TicketCategory.builder().name("General").price(new BigDecimal("10.00")).totalSeats(1).availableSeats(1).build();

        ticketCategory.prePersist();

        assertThat(ticketCategory.getCreatedAt()).isNotNull();
    }
}
