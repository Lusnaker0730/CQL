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
public class CoverageResult {
    private List<ExpressionCoverage> definitions;
    private List<ExpressionCoverage> functions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpressionCoverage {
        private String name;
        private String type;
        private String relevance; // "TRUE" | "FALSE" | "NA"
        private String result;
    }
}
