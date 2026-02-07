package com.cqlplatform.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cqlplatform.model.measure.GroupDefinition;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "measure_definition")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureDefinitionEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "version", nullable = false, length = 50)
    @Builder.Default
    private String version = "1.0.0";

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "draft";

    @Column(name = "scoring_type", length = 30)
    @Builder.Default
    private String scoringType = "proportion";

    @Column(name = "cql_library_id", length = 200)
    private String cqlLibraryId;

    @Column(name = "cql_content", columnDefinition = "TEXT")
    private String cqlContent;

    @Column(name = "fhir_measure_json", columnDefinition = "TEXT")
    private String fhirMeasureJson;

    @Column(name = "group_definitions", columnDefinition = "TEXT")
    private String groupDefinitions;

    @Transient
    @Builder.Default
    private List<GroupDefinition> groupDefinitionList = new ArrayList<>();

    @Column(name = "composite_scoring", length = 30)
    private String compositeScoring;

    @Column(name = "component_measure_ids", columnDefinition = "TEXT")
    private String componentMeasureIds;

    @Transient
    @Builder.Default
    private List<Long> componentMeasureIdList = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        serializeGroupDefinitions();
        serializeComponentMeasureIds();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        serializeGroupDefinitions();
        serializeComponentMeasureIds();
    }

    @PostLoad
    protected void onLoad() {
        deserializeGroupDefinitions();
        deserializeComponentMeasureIds();
    }

    private void serializeGroupDefinitions() {
        if (groupDefinitionList != null && !groupDefinitionList.isEmpty()) {
            try {
                groupDefinitions = MAPPER.writeValueAsString(groupDefinitionList);
            } catch (JsonProcessingException e) {
                groupDefinitions = "[]";
            }
        } else {
            groupDefinitions = "[]";
        }
    }

    private void deserializeGroupDefinitions() {
        if (groupDefinitions != null && !groupDefinitions.isBlank()) {
            try {
                groupDefinitionList = MAPPER.readValue(groupDefinitions, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                groupDefinitionList = new ArrayList<>();
            }
        } else {
            groupDefinitionList = new ArrayList<>();
        }
    }

    private void serializeComponentMeasureIds() {
        if (componentMeasureIdList != null && !componentMeasureIdList.isEmpty()) {
            try {
                componentMeasureIds = MAPPER.writeValueAsString(componentMeasureIdList);
            } catch (JsonProcessingException e) {
                componentMeasureIds = "[]";
            }
        } else {
            componentMeasureIds = "[]";
        }
    }

    private void deserializeComponentMeasureIds() {
        if (componentMeasureIds != null && !componentMeasureIds.isBlank()) {
            try {
                componentMeasureIdList = MAPPER.readValue(componentMeasureIds, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                componentMeasureIdList = new ArrayList<>();
            }
        } else {
            componentMeasureIdList = new ArrayList<>();
        }
    }
}
