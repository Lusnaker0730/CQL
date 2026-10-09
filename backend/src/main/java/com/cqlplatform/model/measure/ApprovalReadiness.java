package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PAT-249 — what stands between a measure and its approval. {@code blockers} stop submit-for-review
 * and approve (HTTP 409 {@code Measure Not Ready}); {@code warnings} are shown but do not stop
 * anything. {@code fourEyes} tells the caller whether <em>they</em> would be refused as the approver.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalReadiness {

    // blocker codes
    public static final String CQL_MISSING = "CQL_MISSING";
    public static final String CQL_ERRORS = "CQL_ERRORS";
    public static final String TEST_CASE_INVALID = "TEST_CASE_INVALID";
    public static final String TEST_CASE_VALIDATION_PENDING = "TEST_CASE_VALIDATION_PENDING";
    public static final String TEST_CASE_NOT_PASSING = "TEST_CASE_NOT_PASSING";
    // warning codes
    public static final String NO_TEST_CASES = "NO_TEST_CASES";
    public static final String TEST_CASE_NEVER_VALIDATED = "TEST_CASE_NEVER_VALIDATED";
    public static final String TEST_CASE_VALIDATION_ERROR = "TEST_CASE_VALIDATION_ERROR";
    public static final String TEST_CASE_RUN_STALE = "TEST_CASE_RUN_STALE";
    public static final String CQL_WARNINGS = "CQL_WARNINGS";

    private Long measureId;
    private String status;
    /** No blockers. */
    private boolean ready;
    private List<Item> blockers;
    private List<Item> warnings;
    private TestCaseSummary testCases;
    private int cqlErrorCount;
    private int cqlWarningCount;
    private FourEyes fourEyes;
    private LocalDateTime checkedAt;

    /** One finding; {@code items} names the test cases concerned (capped) so the UI can point at them. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String code;
        private int count;
        private String message;
        private List<String> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseSummary {
        private int total;
        private int passed;
        private int failed;
        private int errored;
        /** Never run, or not run since the test case changed (status {@code pending}). */
        private int notRun;
        /** Last run is older than the measure's last change (a pass that may no longer hold). */
        private int stale;
        private int valid;
        private int invalid;
        private int validationPending;
        private int validationError;
        private int neverValidated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FourEyes {
        private boolean enabled;
        /** The measure's author: its owner, or its creator when it has no owner. */
        private String author;
        /** Who last submitted it for review, when known. */
        private String submittedBy;
        /** Whether the caller of the readiness check would be refused as the approver. */
        private boolean selfApprovalBlocked;
    }
}
