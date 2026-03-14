package com.cqlplatform.model.ecqm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcqmArtifactResponse {

    private Long id;
    private String name;
    private String version;
    private String description;
    private String status;
    private String fhirVersion;
    private String scoringType;
    private String populationBasis;
    private String improvementNotation;
    private String measureSet;
    private String cmsMeasureId;
    private String nqfNumber;
    private String url;
    private String publisher;
    private String purpose;
    private String copyright;
    private String rationale;
    private String clinicalGuidance;
    private String steward;
    private String disclaimer;
    private String supplementalDataGuidance;

    private List<Map<String, Object>> populationGroups;
    private List<Map<String, Object>> supplementalData;
    private List<Map<String, Object>> stratifiers;
    private List<Map<String, Object>> baseElements;
    private List<Map<String, Object>> parameters;

    private Long publishedMeasureId;
    private String ownerUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
