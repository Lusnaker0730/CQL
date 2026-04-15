package com.cqlplatform.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CqlExecutionResponse {
    private boolean success;
    private String patientId;
    private Map<String, ExpressionResult> results;
    private List<String> errors;
    private ExecutionMetadata metadata;
    private DebugTrace debugTrace;

    @Data
    @Builder
    public static class ExpressionResult {
        private String name;
        private Object value;
        private String valueType;
        private String displayValue;
    }

    @Data
    @Builder
    public static class ExecutionMetadata {
        private long executionTimeMs;
        private String libraryId;
        private String libraryVersion;
        private String fhirServerUrl;
        private int resourcesRetrieved;
    }

    @Data
    @Builder
    public static class DebugTrace {
        private List<ExpressionTrace> expressionTraces;
        private List<RetrieveTrace> retrieveTraces;
        private long totalTimeMs;
        private Map<String, String> sourceLocators;
        /** Compiled ELM JSON (only populated when debugMode=true). */
        private String elmJson;
    }

    @Data
    @Builder
    public static class ExpressionTrace {
        private String name;
        private String resultType;
        private String resultDisplay;
        private long evaluationTimeMs;
        private int order;
        private String sourceLocator;
        private List<String> dependencies;
    }

    @Data
    @Builder
    public static class RetrieveTrace {
        private String resourceType;
        private int resourceCount;
        private long retrieveTimeMs;
    }
}
