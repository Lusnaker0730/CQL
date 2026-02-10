package com.cqlplatform.model.measure;

import com.cqlplatform.security.NoXss;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank
    @Size(max = 200)
    @NoXss
    private String name;

    @Size(max = 50)
    private String version;

    @Size(max = 500)
    @NoXss
    private String title;

    @Size(max = 5000)
    @NoXss
    private String description;

    @Pattern(regexp = "draft|active|retired|in-review")
    private String status;

    @Pattern(regexp = "proportion|ratio|continuous-variable|cohort|composite")
    private String scoringType;

    @Size(max = 100)
    private String cqlLibraryId;

    /** CQL content — exempt from XSS sanitization (legitimate code). */
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cqlContent;

    /** FHIR Measure JSON — exempt from XSS sanitization (legitimate JSON/code). */
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String fhirMeasureJson;

    @Valid
    private List<GroupDefinition> groupDefinitions;

    private String compositeScoring;
    private List<Long> componentMeasureIds;
    private String createdBy;
    private String ownerUsername;
    private List<String> sharedWith;
    private String accessLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lockedBy;
    private LocalDateTime lockedAt;

    @Pattern(regexp = "inpatient|outpatient|emergency|community|long-term-care|home-health|")
    private String setting;

    // Enhanced metadata fields
    @Size(max = 5000)
    @NoXss
    private String rationale;

    @Size(max = 5000)
    @NoXss
    private String clinicalGuidance;

    @Size(max = 5000)
    @NoXss
    private String steward;

    private List<String> developers;
    private List<MeasureReference> references;

    @Size(max = 5000)
    @NoXss
    private String disclaimer;

    @Size(max = 5000)
    @NoXss
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
