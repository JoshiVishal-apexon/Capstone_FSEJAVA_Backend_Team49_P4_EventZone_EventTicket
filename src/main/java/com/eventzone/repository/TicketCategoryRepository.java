package com.eventzone.repository;

import com.eventzone.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {
    List<TicketCategory> findByEvent_Id(UUID eventId);
}
