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
public class BatchTestCaseImportResult {
    private int totalReceived;
    private int successCount;
    private int failureCount;
    private List<TestCase> imported;
    private List<String> errors;
    /** Imported, but with something dropped or worth knowing (PAT-247): one line per test case. */
    private List<String> warnings;
}
