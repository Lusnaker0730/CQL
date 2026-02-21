package com.cqlplatform.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
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
    private String status = com.cqlplatform.model.measure.MeasureStatusConstants.DRAFT;

    @Column(name = "scoring_type", length = 30)
    @Builder.Default
    private String scoringType = com.cqlplatform.model.measure.ScoringTypeConstants.PROPORTION;

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

    @Column(name = "owner_username", length = 100)
    private String ownerUsername;

    @Column(name = "shared_with", columnDefinition = "TEXT")
    @Builder.Default
    private String sharedWith = "[]";

    @Column(name = "access_level", length = 20)
    @Builder.Default
    private String accessLevel = "private";

    @Transient
    @Builder.Default
    private List<String> sharedWithList = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "setting", length = 50)
    private String setting;

    // Enhanced metadata columns
    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "clinical_guidance", columnDefinition = "TEXT")
    private String clinicalGuidance;

    @Column(name = "steward", length = 500)
    private String steward;

    @Column(name = "developers", columnDefinition = "TEXT")
    private String developers;

    @Transient
    @Builder.Default
    private List<String> developerList = new ArrayList<>();

    @Column(name = "measure_references", columnDefinition = "TEXT")
    private String measureReferences;

    @Transient
    @Builder.Default
    private List<MeasureDefinition.MeasureReference> referenceList = new ArrayList<>();

    @Column(name = "disclaimer", columnDefinition = "TEXT")
    private String disclaimer;

    @Column(name = "copyright", columnDefinition = "TEXT")
    private String copyright;

    @Column(name = "measure_set", length = 200)
    private String measureSet;

    @Column(name = "nqf_number", length = 20)
    private String nqfNumber;

    @Column(name = "cms_measure_id", length = 20)
    private String cmsMeasureId;

    @Column(name = "supplemental_data_guidance", columnDefinition = "TEXT")
    private String supplementalDataGuidance;

    @Column(name = "risk_adjustment_description", columnDefinition = "TEXT")
    private String riskAdjustmentDescription;

    @Column(name = "risk_adjustments", columnDefinition = "TEXT")
    private String riskAdjustmentsJson;

    @Transient
    @Builder.Default
    private List<MeasureDefinition.RiskAdjustmentDef> riskAdjustmentList = new ArrayList<>();

    @Column(name = "supplemental_data", columnDefinition = "TEXT")
    private String supplementalDataJson;

    @Transient
    @Builder.Default
    private List<MeasureDefinition.SupplementalDataDef> supplementalDataList = new ArrayList<>();

    @Column(name = "improvement_notation", length = 20)
    private String improvementNotation;

    @Column(name = "rate_aggregation", length = 2000)
    private String rateAggregation;

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

    private void serializeAll() {
        serializeGroupDefinitions();
        serializeComponentMeasureIds();
        serializeJsonList(developerList, (json) -> developers = json, "[]");
        serializeJsonList(referenceList, (json) -> measureReferences = json, "[]");
        serializeJsonList(riskAdjustmentList, (json) -> riskAdjustmentsJson = json, "[]");
        serializeJsonList(supplementalDataList, (json) -> supplementalDataJson = json, "[]");
        serializeJsonList(sharedWithList, (json) -> sharedWith = json, "[]");
    }

    private void deserializeAll() {
        deserializeGroupDefinitions();
        deserializeComponentMeasureIds();
        developerList = deserializeJsonList(developers, new TypeReference<>() {});
        referenceList = deserializeJsonList(measureReferences, new TypeReference<>() {});
        riskAdjustmentList = deserializeJsonList(riskAdjustmentsJson, new TypeReference<>() {});
        supplementalDataList = deserializeJsonList(supplementalDataJson, new TypeReference<>() {});
        sharedWithList = deserializeJsonList(sharedWith, new TypeReference<>() {});
    }

    private <T> void serializeJsonList(List<T> list, java.util.function.Consumer<String> setter, String defaultVal) {
        if (list != null && !list.isEmpty()) {
            try {
                setter.accept(MAPPER.writeValueAsString(list));
            } catch (JsonProcessingException e) {
                setter.accept(defaultVal);
            }
        } else {
            setter.accept(defaultVal);
        }
    }

    private <T> List<T> deserializeJsonList(String json, TypeReference<List<T>> typeRef) {
        if (json != null && !json.isBlank()) {
            try {
                return MAPPER.readValue(json, typeRef);
            } catch (JsonProcessingException e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
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
