package com.cqlplatform.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "ecqm_artifact")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class EcqmArtifactEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "version", length = 50)
    @Builder.Default
    private String version = "1.0.0";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "draft";

    @Column(name = "fhir_version", length = 20)
    @Builder.Default
    private String fhirVersion = "4.0.1";

    @Column(name = "scoring_type", nullable = false, length = 30)
    @Builder.Default
    private String scoringType = "proportion";

    @Column(name = "population_basis", length = 20)
    @Builder.Default
    private String populationBasis = "boolean";

    @Column(name = "improvement_notation", length = 20)
    @Builder.Default
    private String improvementNotation = "increase";

    @Column(name = "measure_set", length = 200)
    private String measureSet;

    @Column(name = "cms_measure_id", length = 20)
    private String cmsMeasureId;

    @Column(name = "nqf_number", length = 20)
    private String nqfNumber;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "publisher", length = 255)
    private String publisher;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "copyright", columnDefinition = "TEXT")
    private String copyright;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "clinical_guidance", columnDefinition = "TEXT")
    private String clinicalGuidance;

    @Column(name = "steward", length = 500)
    private String steward;

    @Column(name = "disclaimer", columnDefinition = "TEXT")
    private String disclaimer;

    @Column(name = "supplemental_data_guidance", columnDefinition = "TEXT")
    private String supplementalDataGuidance;

    // JSON columns
    @Column(name = "population_groups", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String populationGroupsJson = "[]";

    @Column(name = "supplemental_data", columnDefinition = "TEXT")
    @Builder.Default
    private String supplementalDataJson = "[]";

    @Column(name = "stratifiers", columnDefinition = "TEXT")
    @Builder.Default
    private String stratifiersJson = "[]";

    @Column(name = "base_elements", columnDefinition = "TEXT")
    @Builder.Default
    private String baseElementsJson = "[]";

    @Column(name = "parameters", columnDefinition = "TEXT")
    @Builder.Default
    private String parametersJson = "[]";

    @Column(name = "published_measure_id")
    private Long publishedMeasureId;

    @Column(name = "owner_username", nullable = false, length = 255)
    private String ownerUsername;

    /**
     * Tenant (clinic) that owns this eCQM artifact — the isolation boundary (Phase 2).
     * Assigned server-side; existing rows were backfilled to the default tenant. Nullable
     * for now (foundation); the enforcement PR scopes reads and makes it NOT NULL.
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Transient deserialized fields
    @Transient
    @Builder.Default
    private List<Map<String, Object>> populationGroupsList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> supplementalDataList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> stratifiersList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> baseElementsList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> parametersList = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        serializeAll();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        serializeAll();
    }

    @PostLoad
    protected void onLoad() {
        deserializeAll();
    }

    public void serializeAll() {
        populationGroupsJson = serializeList(populationGroupsList, "[]");
        supplementalDataJson = serializeList(supplementalDataList, "[]");
        stratifiersJson = serializeList(stratifiersList, "[]");
        baseElementsJson = serializeList(baseElementsList, "[]");
        parametersJson = serializeList(parametersList, "[]");
    }

    private void deserializeAll() {
        populationGroupsList = deserializeList(populationGroupsJson);
        supplementalDataList = deserializeList(supplementalDataJson);
        stratifiersList = deserializeList(stratifiersJson);
        baseElementsList = deserializeList(baseElementsJson);
        parametersList = deserializeList(parametersJson);
    }

    private String serializeList(List<Map<String, Object>> list, String defaultVal) {
        if (list == null || list.isEmpty()) return defaultVal;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list for ecqm_artifact id={}: {}", id, e.getMessage());
            return defaultVal;
        }
    }

    private List<Map<String, Object>> deserializeList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize list for ecqm_artifact id={}: {}", id, e.getMessage());
            return new ArrayList<>();
        }
    }
}
