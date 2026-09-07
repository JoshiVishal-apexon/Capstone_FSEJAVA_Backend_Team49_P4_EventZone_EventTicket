package com.eventzone.service;

import com.eventzone.dto.category.CategoryRequest;
import com.eventzone.dto.category.CategoryResponse;
import com.eventzone.entity.EventCategory;
import com.eventzone.exception.ConflictException;
import com.eventzone.exception.ResourceNotFoundException;
import com.eventzone.repository.EventCategoryRepository;
import com.eventzone.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private EventCategoryRepository categoryRepository;

    @Mock
    private EventRepository eventRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, eventRepository);
    }

    @Test
    void listAll_returnsMappedCategories() {
        EventCategory music = EventCategory.builder().id(UUID.randomUUID()).name("Music").build();
        EventCategory tech = EventCategory.builder().id(UUID.randomUUID()).name("Tech").build();
        when(categoryRepository.findAll()).thenReturn(List.of(music, tech));

        List<CategoryResponse> result = categoryService.listAll();

        assertThat(result)
                .extracting(CategoryResponse::id, CategoryResponse::name)
                .containsExactlyInAnyOrder(
                        tuple(music.getId(), "Music"),
                        tuple(tech.getId(), "Tech")
                );
    }

    @Test
    void create_whenDuplicateName_throwsConflict() {
        when(categoryRepository.existsByNameIgnoreCase("Music")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Music")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A category with this name already exists");
    }

    @Test
    void create_whenValid_savesAndReturnsResponse() {
        EventCategory saved = EventCategory.builder().id(UUID.randomUUID()).name("Music").build();
        when(categoryRepository.existsByNameIgnoreCase("Music")).thenReturn(false);
        when(categoryRepository.save(any(EventCategory.class))).thenReturn(saved);

        CategoryResponse result = categoryService.create(new CategoryRequest("Music"));

        assertThat(result.id()).isEqualTo(saved.getId());
        assertThat(result.name()).isEqualTo("Music");
        verify(categoryRepository).save(any(EventCategory.class));
    }

    @Test
    void update_whenDifferentCategoryUsesSameName_throwsConflict() {
        UUID targetId = UUID.randomUUID();
        EventCategory target = EventCategory.builder().id(targetId).name("Music").build();
        EventCategory existing = EventCategory.builder().id(UUID.randomUUID()).name("Tech").build();
        when(categoryRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(categoryRepository.findByNameIgnoreCase("Tech")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> categoryService.update(targetId, new CategoryRequest("Tech")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A category with this name already exists");
    }

    @Test
    void update_whenValid_updatesAndReturnsResponse() {
        UUID id = UUID.randomUUID();
        EventCategory category = EventCategory.builder().id(id).name("Music").build();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Concert")).thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse result = categoryService.update(id, new CategoryRequest("Concert"));

        assertThat(result.name()).isEqualTo("Concert");
        assertThat(category.getName()).isEqualTo("Concert");
    }

    @Test
    void delete_whenCategoryInUse_throwsConflictWithAssignedEventName() {
        UUID id = UUID.randomUUID();
        EventCategory category = EventCategory.builder().id(id).name("Music").build();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(eventRepository.findByCategory_Id(id)).thenReturn(List.of(
                com.eventzone.entity.Event.builder().id(UUID.randomUUID()).title("Sunburn Night").build()
        ));

        assertThatThrownBy(() -> categoryService.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This category is assigned to event 'Sunburn Night' and cannot be deleted");
    }

    @Test
    void delete_whenCategoryExists_deletesIt() {
        UUID id = UUID.randomUUID();
        EventCategory category = EventCategory.builder().id(id).name("Music").build();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(eventRepository.findByCategory_Id(id)).thenReturn(List.of());

        categoryService.delete(id);

        verify(categoryRepository).delete(category);
    }

    @Test
    void delete_whenCategoryMissing_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category not found");
    }
}
