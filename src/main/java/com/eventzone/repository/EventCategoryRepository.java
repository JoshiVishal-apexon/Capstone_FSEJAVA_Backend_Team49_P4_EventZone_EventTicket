package com.eventzone.repository;

import com.eventzone.entity.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventCategoryRepository extends JpaRepository<EventCategory, UUID> {
    Optional<EventCategory> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
