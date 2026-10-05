package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PAT-246 — outcome of copying test cases to another measure (typically another version of the
 * same measure, where MADiE's "copy to" lives). A copy whose structured expectation does not fit
 * the target's population groups still lands, with the expectation dropped and a warning saying so.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseCopyResult {
    private Long sourceMeasureId;
    private Long targetMeasureId;
    private List<TestCase> copied;
    /** One line per copy whose expected values were dropped, naming the test case and the reason. */
    private List<String> warnings;

    /** Request body: the test cases to copy; null / empty = every test case of the source measure. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private List<Long> testCaseIds;
    }
}
