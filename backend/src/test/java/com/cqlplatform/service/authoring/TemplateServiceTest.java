package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.FormTemplateCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TemplateServiceTest {

    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService();
        service.init();
    }

    @Test
    void init_shouldLoadCategoriesFromClasspath() {
        List<FormTemplateCategory> categories = service.getAllCategories();
        assertThat(categories).isNotEmpty();
    }

    @Test
    void getAllCategories_shouldReturnCorrectStructure() {
        List<FormTemplateCategory> categories = service.getAllCategories();

        for (FormTemplateCategory category : categories) {
            assertThat(category.getName()).isNotNull().isNotBlank();
            assertThat(category.getId()).isNotNull();
            assertThat(category.getEntries()).isNotNull();
        }
    }

    @Test
    void getAllCategories_shouldHaveEntriesWithNames() {
        List<FormTemplateCategory> categories = service.getAllCategories();

        boolean hasEntries = categories.stream()
                .anyMatch(c -> c.getEntries() != null && !c.getEntries().isEmpty());
        assertThat(hasEntries).isTrue();

        categories.stream()
                .flatMap(c -> c.getEntries().stream())
                .forEach(entry -> {
                    assertThat(entry.getName()).isNotNull().isNotBlank();
                });
    }

    @Test
    void getAllCategories_entriesShouldHaveIds() {
        List<FormTemplateCategory> categories = service.getAllCategories();

        categories.stream()
                .flatMap(c -> c.getEntries().stream())
                .forEach(entry -> {
                    assertThat(entry.getId()).isNotNull().isNotBlank();
                });
    }
}
