package com.eventzone.service;

import com.eventzone.dto.category.CategoryRequest;
import com.eventzone.dto.category.CategoryResponse;
import com.eventzone.entity.Event;
import com.eventzone.entity.EventCategory;
import com.eventzone.exception.ConflictException;
import com.eventzone.exception.ResourceNotFoundException;
import com.eventzone.repository.EventCategoryRepository;
import com.eventzone.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final EventCategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A category with this name already exists");
        }
        EventCategory saved = categoryRepository.save(EventCategory.builder().name(request.name()).build());
        return new CategoryResponse(saved.getId(), saved.getName());
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        EventCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        categoryRepository.findByNameIgnoreCase(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("A category with this name already exists");
                });

        category.setName(request.name());
        EventCategory saved = categoryRepository.save(category);
        return new CategoryResponse(saved.getId(), saved.getName());
    }

    @Transactional
    public void delete(UUID id) {
        EventCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        List<Event> assignedEvents = eventRepository.findByCategory_Id(id);
        if (!assignedEvents.isEmpty()) {
            String eventNames = assignedEvents.stream()
                    .map(event -> event.getTitle() == null || event.getTitle().isBlank() ? "Untitled Event" : event.getTitle())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("', '", "'", "'"));

            String message = assignedEvents.size() == 1
                    ? "This category is assigned to event " + eventNames + " and cannot be deleted"
                    : "This category is assigned to events " + eventNames + " and cannot be deleted";
            throw new ConflictException(message);
        }

        categoryRepository.delete(category);
    }
}
