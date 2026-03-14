package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.ModifierDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ModifierServiceTest {

    private ModifierService service;

    @BeforeEach
    void setUp() {
        service = new ModifierService();
        service.init();
    }

    @Test
    void init_shouldLoadModifiersFromClasspath() {
        List<ModifierDefinition> modifiers = service.getAllModifiers();
        assertThat(modifiers).isNotEmpty();
    }

    @Test
    void getAllModifiers_shouldReturnNonEmptyList() {
        List<ModifierDefinition> modifiers = service.getAllModifiers();
        assertThat(modifiers).isNotNull();
        assertThat(modifiers.size()).isGreaterThan(0);
    }

    @Test
    void getAllModifiers_eachShouldHaveNameAndId() {
        List<ModifierDefinition> modifiers = service.getAllModifiers();
        for (ModifierDefinition m : modifiers) {
            assertThat(m.getId()).isNotNull().isNotBlank();
            assertThat(m.getName()).isNotNull().isNotBlank();
        }
    }

    @Test
    void getModifiersByInputType_knownType_shouldReturnResults() {
        // "list_of_conditions" is a common input type in FHIR CQL
        List<ModifierDefinition> all = service.getAllModifiers();
        // Find a valid input type from loaded modifiers
        String validInputType = all.stream()
                .filter(m -> m.getInputTypes() != null && !m.getInputTypes().isEmpty())
                .flatMap(m -> m.getInputTypes().stream())
                .findFirst()
                .orElse(null);

        if (validInputType != null) {
            List<ModifierDefinition> filtered = service.getModifiersByInputType(validInputType);
            assertThat(filtered).isNotEmpty();
            assertThat(filtered).allMatch(m -> m.getInputTypes().contains(validInputType));
        }
    }

    @Test
    void getModifiersByInputType_unknownType_shouldReturnEmpty() {
        List<ModifierDefinition> result = service.getModifiersByInputType("nonexistent_type_xyz");
        assertThat(result).isEmpty();
    }
}
