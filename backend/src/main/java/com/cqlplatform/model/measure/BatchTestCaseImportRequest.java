package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTestCaseImportRequest {
    private List<TestCase> testCases;
    /** Number of days to shift all dates in patient bundles. 0 = no shifting. */
    private int dateShiftDays;
}
