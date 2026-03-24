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
    @Size(max = 512_000, message = "CQL content must be at most 512 KB")
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cqlContent;

    /** Pre-compiled ELM JSON — populated on save, used at execution time to skip CQL translation. */
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String elmJson;

    /** FHIR Measure JSON — exempt from XSS sanitization (legitimate JSON/code). */
    @Size(max = 2_097_152, message = "FHIR Measure JSON must be at most 2 MB")
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String fhirMeasureJson;

    @Valid
    @Size(max = 50)
    private List<GroupDefinition> groupDefinitions;

    @Size(max = 50)
    private String compositeScoring;

    @Size(max = 50)
    private List<Long> componentMeasureIds;
    private String createdBy;
    private String ownerUsername;
    @Size(max = 100)
    private List<String> sharedWith;

    @Size(max = 20)
    private String accessLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lockedBy;
    private LocalDateTime lockedAt;

    // Workflow review tracking
    private String reviewedBy;
    private String approvedBy;
    private String reviewComment;
    private LocalDateTime reviewedAt;

    @Pattern(regexp = "inpatient|outpatient|emergency|community|long-term-care|home-health")
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

    @Size(max = 50)
    private List<String> developers;

    @Size(max = 100)
    private List<MeasureReference> references;

    @Size(max = 5000)
    @NoXss
    private String disclaimer;

    @Size(max = 5000)
    @NoXss
    private String copyright;

    @Size(max = 200)
    private String measureSet;

    @Size(max = 20)
    private String nqfNumber;

    @Size(max = 20)
    private String cmsMeasureId;
    @Size(max = 5000)
    @NoXss
    private String supplementalDataGuidance;

    @Size(max = 5000)
    @NoXss
    private String riskAdjustmentDescription;

    @Size(max = 50)
    private List<RiskAdjustmentDef> riskAdjustments;

    @Size(max = 50)
    private List<SupplementalDataDef> supplementalData;

    @Pattern(regexp = "increase|decrease")
    private String improvementNotation;

    @Size(max = 2000)
    @NoXss
    private String rateAggregation;

    // Indicator code mapping
    @Size(max = 50)
    private String mohIndicatorCode;

    @Size(max = 50)
    private String nhiaP4pCode;

    @Size(max = 50)
    private String drgIndicatorCode;

    @Size(max = 100)
    private String indicatorCategory;

    // Department (soft multi-tenancy)
    @Size(max = 100)
    private String department;

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
