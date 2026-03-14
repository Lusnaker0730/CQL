package com.cqlplatform.model.measure;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseRunResult {

    private Long testCaseId;
    private String testCaseTitle;

    /** pass, fail, error */
    private String status;

    /** Expected population membership */
    private Map<String, Boolean> expectedPopulations;

    /** Actual population membership from CQL execution */
    private Map<String, Boolean> actualPopulations;

    /** Per-population pass/fail detail */
    private List<PopulationComparison> comparisons;

    /** Error message if execution failed */
    private String errorMessage;

    /** Execution time in milliseconds */
    private Long executionTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PopulationComparison {
        private String populationType;
        private Boolean expected;
        private Boolean actual;
        private boolean match;
    }
}
