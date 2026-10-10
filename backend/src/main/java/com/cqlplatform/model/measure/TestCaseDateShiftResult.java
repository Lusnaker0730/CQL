package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** PAT-248 — outcome of shifting every test case of a measure by whole years. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDateShiftResult {
    private Long measureDefinitionId;
    private int years;
    /** How many test cases were shifted (those without a patient bundle are skipped). */
    private int shifted;
    private List<Long> testCaseIds;
}
