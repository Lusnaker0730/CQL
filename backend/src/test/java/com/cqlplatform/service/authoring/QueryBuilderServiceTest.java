package com.cqlplatform.service.authoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class QueryBuilderServiceTest {

    private QueryBuilderService service;

    @BeforeEach
    void setUp() {
        service = new QueryBuilderService();
        service.loadConfig();
    }

    @Test
    void loadConfig_shouldLoadResources() {
        List<Map<String, Object>> resources = service.getResources();
        assertThat(resources).isNotEmpty();
    }

    @Test
    void loadConfig_shouldLoadOperators() {
        List<Map<String, Object>> operators = service.getOperators();
        assertThat(operators).isNotEmpty();
    }

    @Test
    void getResources_shouldBeUnmodifiable() {
        List<Map<String, Object>> resources = service.getResources();
        assertThatThrownBy(() -> resources.add(Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getOperators_shouldBeUnmodifiable() {
        List<Map<String, Object>> operators = service.getOperators();
        assertThatThrownBy(() -> operators.add(Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getResource_observation_shouldReturnResult() {
        Optional<Map<String, Object>> observation = service.getResource("Observation");
        assertThat(observation).isPresent();
        assertThat(observation.get().get("name")).isEqualTo("Observation");
    }

    @Test
    void getResource_nonExistent_shouldReturnEmpty() {
        Optional<Map<String, Object>> result = service.getResource("NonExistentResource");
        assertThat(result).isEmpty();
    }

    @Test
    void getOperatorsForType_string_shouldReturnOperators() {
        List<Map<String, Object>> ops = service.getOperatorsForType("string");
        assertThat(ops).isNotEmpty();
    }

    @Test
    void getOperatorsForType_nonExistent_shouldReturnEmpty() {
        List<Map<String, Object>> ops = service.getOperatorsForType("completely_unknown_type");
        assertThat(ops).isEmpty();
    }
}
