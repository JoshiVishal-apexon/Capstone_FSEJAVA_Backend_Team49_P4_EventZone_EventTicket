package com.eventzone.repository;

import com.eventzone.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Booking findFirstByTicketCategory_Event_Id(UUID eventId);
}
