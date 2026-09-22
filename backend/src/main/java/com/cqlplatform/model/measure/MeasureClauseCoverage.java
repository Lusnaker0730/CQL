package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PAT-232 — clause coverage of a measure's CQL across ALL its test cases (the union), the number
 * Bonnie and MADiE put at the top of the test-case page. {@code coverage} is null when the
 * measure has no test cases, or none of them could be executed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureClauseCoverage {
    private Long measureId;
    private int testCases;
    /** Test cases whose CQL ran to completion (pass or fail); the others contribute nothing. */
    private int executed;
    private int passed;
    private ClauseCoverage coverage;
}
