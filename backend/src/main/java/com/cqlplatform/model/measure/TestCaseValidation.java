package com.cqlplatform.model.measure;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PAT-245 — the FHIR validation outcome of a test case's patient bundle.
 *
 * <p>Every resource of the bundle is validated by the platform's HAPI validator (TW Core profile
 * when the IG is loaded, base FHIR R4 otherwise). {@code status} is {@code valid} when no
 * resource has an error, {@code invalid} when at least one does (or the bundle is empty),
 * {@code error} when the validator itself failed, {@code pending} while the run is queued.
 * Only error-level issues are kept (capped), warnings are counted — the row must stay small.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestCaseValidation {

    public static final String PENDING = "pending";
    public static final String VALID = "valid";
    public static final String INVALID = "invalid";
    public static final String ERROR = "error";

    /** Error issues kept per test case; the count still reports the total. */
    public static final int MAX_ISSUES = 100;

    private String status;
    private LocalDateTime validatedAt;
    private Integer totalResources;
    private Integer invalidResources;
    private Integer errorCount;
    private Integer warningCount;
    /** Set when {@code status} is {@code error} (validator failure) or {@code invalid} without resources. */
    private String message;
    private List<Issue> issues;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Issue {
        private String resourceType;
        private String resourceId;
        private String severity;
        private String location;
        private String message;
    }
}
