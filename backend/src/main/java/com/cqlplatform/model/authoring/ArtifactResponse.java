package com.cqlplatform.model.authoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactResponse {

    private Long id;
    private String name;
    private String version;
    private String description;
    private String status;
    private String fhirVersion;

    // CPG Metadata
    private String url;
    private String publisher;
    private String purpose;
    private String usageInfo;
    private String copyright;
    private Boolean experimental;
    private LocalDate approvalDate;
    private LocalDate lastReviewDate;
    private LocalDate effectivePeriodStart;
    private LocalDate effectivePeriodEnd;
    private Map<String, Object> strengthOfRecommendation;
    private Map<String, Object> qualityOfEvidence;
    private List<Map<String, Object>> context;
    private List<Map<String, Object>> topic;
    private List<Map<String, Object>> author;
    private List<Map<String, Object>> reviewer;
    private List<Map<String, Object>> endorser;
    private List<Map<String, Object>> relatedArtifact;

    // Expression Trees
    private Map<String, Object> expTreeInclude;
    private Map<String, Object> expTreeExclude;

    // Linked data
    private List<Map<String, Object>> recommendations;
    private List<Map<String, Object>> subpopulations;
    private List<Map<String, Object>> baseElements;
    private List<Map<String, Object>> parameters;
    private Map<String, Object> errorStatement;

    // Ownership
    private String ownerUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
