package com.cqlplatform.model.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    /** Structured expectations this run compared against (PAT-228); null for legacy test cases. */
    private TestCaseExpectedValues expectedValues;

    /**
     * Structured actual values — per group effective population counts, observation values and
     * stratifier values, computed with the production evaluation rules. Filled on every
     * successful run (also for legacy test cases) so the UI can offer "use actual as expected".
     */
    private TestCaseExpectedValues actualValues;

    /** Per-item detail of the structured comparison; null for legacy test cases. */
    private List<ValueComparison> valueComparisons;

    /** PAT-232: which clauses of the measure's CQL this run executed (debug mode only). */
    private ClauseCoverage clauseCoverage;

    /** Error message if execution failed */
    private String errorMessage;

    /** Execution time in milliseconds */
    private Long executionTimeMs;

    /** CQL execution trace (expressions + retrieves + ELM). Populated only when debugMode=true. */
    private CqlExecutionResponse.DebugTrace debugTrace;

    /** Per-group population membership trace (raw vs effective, with reasons). Populated only when debugMode=true. */
    private PopulationMembershipTrace populationTrace;

    /** Expression coverage (relevance + result per expression). Populated only when debugMode=true. */
    private CoverageResult coverage;

    /** Structured phase-wrapped error. Populated only when debugMode=true and execution failed. */
    private PhaseError phaseError;

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

    /** One compared item of a structured expectation. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValueComparison {
        public static final String KIND_POPULATION = "population";
        public static final String KIND_OBSERVATION = "observation";
        public static final String KIND_STRATIFIER = "stratifier";

        private String groupId;
        /** population | observation | stratifier */
        private String kind;
        /** Population type, stratifier id, or "values" for the observation list. */
        private String key;
        /** Rendered for display: a count, a sorted value list, or a stratum value. */
        private String expected;
        private String actual;
        private boolean match;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhaseError {
        /** BUNDLE_PARSE | CQL_TRANSLATION | CQL_EXECUTION | POPULATION_EVAL | UNKNOWN */
        private String phase;
        private String message;
        /** Top N stack frames from own package (diagnostic hint). */
        private List<String> stackHint;
    }
}
