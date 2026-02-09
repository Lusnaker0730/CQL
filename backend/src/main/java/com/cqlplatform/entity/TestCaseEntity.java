package com.cqlplatform.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "test_case")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_definition_id", nullable = false)
    private Long measureDefinitionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "patient_bundle_json", columnDefinition = "TEXT")
    private String patientBundleJson;

    @Column(name = "expected_populations", columnDefinition = "TEXT")
    private String expectedPopulations;

    @Transient
    @Builder.Default
    private Map<String, Boolean> expectedPopulationMap = new LinkedHashMap<>();

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "last_run_result_json", columnDefinition = "TEXT")
    private String lastRunResultJson;

    @Column(name = "last_run_actual_populations", columnDefinition = "TEXT")
    private String lastRunActualPopulations;

    @Transient
    @Builder.Default
    private Map<String, Boolean> lastRunActualPopulationMap = new LinkedHashMap<>();

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        serializeMaps();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        serializeMaps();
    }

    @PostLoad
    protected void onLoad() {
        deserializeMaps();
    }

    private void serializeMaps() {
        expectedPopulations = serializeMap(expectedPopulationMap);
        lastRunActualPopulations = serializeMap(lastRunActualPopulationMap);
    }

    private void deserializeMaps() {
        expectedPopulationMap = deserializeMap(expectedPopulations);
        lastRunActualPopulationMap = deserializeMap(lastRunActualPopulations);
    }

    private String serializeMap(Map<String, Boolean> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Boolean> deserializeMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }
}
