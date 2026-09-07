package com.eventzone.repository;

import com.eventzone.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Booking findFirstByTicketCategory_Event_Id(UUID eventId);

    List<Booking> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    List<Booking> findByTicketCategory_IdAndStatusNot(UUID ticketCategoryId, String status);

    List<Booking> findByTicketCategory_Event_IdAndStatusNot(UUID eventId, String status);

    boolean existsByBookingRef(String bookingRef);
}
