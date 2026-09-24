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

    // PAT-236 — standard FHIR Measure metadata, published onto the MeasureDefinition
    @Size(max = 5)
    private List<@jakarta.validation.constraints.Pattern(regexp = "process|outcome|structure|patient-reported-outcome|composite") String> measureTypes;

    /** {@code [{term, definition}]} — validated as expression-free maps by the tree validator's HTML check. */
    @Size(max = 50)
    private List<Map<String, Object>> definitionTerms;

    @Size(max = 5000)
    @NoXss
    private String clinicalRecommendationStatement;

    /**
     * The four dates are ISO strings ({@code yyyy-MM-dd}) rather than LocalDate because the
     * workspace autosaves partial updates (only changed keys) and the service keeps whatever is
     * absent — the same rule the text fields follow: absent keeps, {@code ""} clears, a value
     * sets. (A LocalDate could not express "clear", and through the HTTP converter an absent
     * {@code Optional} arrives as {@code Optional.empty()} just like an explicit null.)
     */
    @Pattern(regexp = ISO_DATE_OR_EMPTY, message = "must be an ISO date (yyyy-MM-dd) or empty")
    private String effectiveStart;
    @Pattern(regexp = ISO_DATE_OR_EMPTY, message = "must be an ISO date (yyyy-MM-dd) or empty")
    private String effectiveEnd;
    @Pattern(regexp = ISO_DATE_OR_EMPTY, message = "must be an ISO date (yyyy-MM-dd) or empty")
    private String approvalDate;
    @Pattern(regexp = ISO_DATE_OR_EMPTY, message = "must be an ISO date (yyyy-MM-dd) or empty")
    private String lastReviewDate;
    private Boolean experimental;

    public static final String ISO_DATE_OR_EMPTY = "|\\d{4}-\\d{2}-\\d{2}";

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
