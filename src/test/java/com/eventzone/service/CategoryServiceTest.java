package com.eventzone.service;

import com.eventzone.dto.category.CategoryResponse;
import com.eventzone.entity.EventCategory;
import com.eventzone.repository.EventCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private EventCategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
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
    void listAll_whenNoCategories_returnsEmptyList() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        assertThat(categoryService.listAll()).isEmpty();
    }
}
