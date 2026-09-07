package com.eventzone.repository;

import com.eventzone.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import com.eventzone.entity.User;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByActiveTrue();

    List<Event> findByActiveTrueAndCategory_NameIgnoreCase(String categoryName);

    List<Event> findByOrganiser(User organiser);

    List<Event> findByOrganiser_Id(UUID organiserId);

    List<Event> findByCategory_Id(UUID categoryId);

    boolean existsByCategory_Id(UUID categoryId);
}
