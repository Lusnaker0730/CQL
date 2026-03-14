package com.cqlplatform.model.ecqm;

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

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcqmArtifactRequest {

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

    @Pattern(regexp = "proportion|ratio|continuous-variable|cohort")
    private String scoringType;

    @Size(max = 20)
    private String populationBasis;

    @Pattern(regexp = "increase|decrease")
    private String improvementNotation;

    @Size(max = 200)
    @NoXss
    private String measureSet;

    @Size(max = 20)
    private String cmsMeasureId;

    @Size(max = 20)
    private String nqfNumber;

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
    private String copyright;

    @Size(max = 5000)
    @NoXss
    private String rationale;

    @Size(max = 5000)
    @NoXss
    private String clinicalGuidance;

    @Size(max = 500)
    @NoXss
    private String steward;

    @Size(max = 5000)
    @NoXss
    private String disclaimer;

    @Size(max = 5000)
    @NoXss
    private String supplementalDataGuidance;

    // Expression trees — exempt from XSS sanitization
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> populationGroups;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> supplementalData;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> stratifiers;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> baseElements;

    @JsonDeserialize(using = JsonDeserializer.None.class)
    private List<Map<String, Object>> parameters;
}
