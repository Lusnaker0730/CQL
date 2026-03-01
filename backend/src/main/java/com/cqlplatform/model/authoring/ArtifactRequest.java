package com.cqlplatform.model.authoring;

import com.cqlplatform.security.NoXss;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactRequest {

    @NotBlank
    @Size(max = 255)
    @NoXss
    private String name;

    @Size(max = 50)
    private String version;

    @Size(max = 5000)
    @NoXss
    private String description;

    @Pattern(regexp = "draft|active|retired")
    private String status;

    @Size(max = 20)
    private String fhirVersion;

    // CPG Metadata
    @Size(max = 500)
    private String url;

    @Size(max = 255)
    @NoXss
    private String publisher;

    @Size(max = 5000)
    @NoXss
    private String purpose;

    @Size(max = 5000)
    @NoXss
    private String usageInfo;

    @Size(max = 5000)
    @NoXss
    private String copyright;

    private Boolean experimental;
    private LocalDate approvalDate;
    private LocalDate lastReviewDate;
    private LocalDate effectivePeriodStart;
    private LocalDate effectivePeriodEnd;

    private Map<String, Object> strengthOfRecommendation;
    private Map<String, Object> qualityOfEvidence;
    @Size(max = 50)
    private List<Map<String, Object>> context;

    @Size(max = 50)
    private List<Map<String, Object>> topic;

    @Size(max = 50)
    private List<Map<String, Object>> author;

    @Size(max = 50)
    private List<Map<String, Object>> reviewer;

    @Size(max = 50)
    private List<Map<String, Object>> endorser;

    @Size(max = 100)
    private List<Map<String, Object>> relatedArtifact;

    // Expression Trees — exempt from XSS sanitization (JSON structure data)
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private Map<String, Object> expTreeInclude;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private Map<String, Object> expTreeExclude;

    // Linked data — exempt from XSS sanitization (JSON structure data)
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> recommendations;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> subpopulations;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> baseElements;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> parameters;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private Map<String, Object> errorStatement;
}
