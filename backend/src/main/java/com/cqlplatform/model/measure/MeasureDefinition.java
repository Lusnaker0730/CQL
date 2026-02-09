package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureDefinition {
    private Long id;
    private String name;
    private String version;
    private String title;
    private String description;
    private String status;
    private String scoringType;
    private String cqlLibraryId;
    private String cqlContent;
    private String fhirMeasureJson;
    private List<GroupDefinition> groupDefinitions;
    private String compositeScoring;
    private List<Long> componentMeasureIds;
    private String createdBy;
    private String ownerUsername;
    private List<String> sharedWith;
    private String accessLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Enhanced metadata fields
    private String rationale;
    private String clinicalGuidance;
    private String steward;
    private List<String> developers;
    private List<MeasureReference> references;
    private String disclaimer;
    private String copyright;
    private String measureSet;
    private String supplementalDataGuidance;
    private String riskAdjustmentDescription;
    private List<RiskAdjustmentDef> riskAdjustments;
    private List<SupplementalDataDef> supplementalData;

    // Nested records
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeasureReference {
        private String type;
        private String reference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAdjustmentDef {
        private String definition;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplementalDataDef {
        private String definition;
        private String description;
    }
}
