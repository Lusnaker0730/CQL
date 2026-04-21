package com.cqlplatform.model;

import com.cqlplatform.model.debug.ExecutionErrorInfo;
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
    private List<String> warnings;
    private ExecutionMetadata metadata;
    private DebugTrace debugTrace;

    /**
     * Structured error classification (phase + errorType + stack frames) — populated
     * only on terminal failure. Complements the flat {@link #errors} list which
     * remains for backward compatibility with existing editor consumers that parse
     * strings. New consumers should prefer this field.
     */
    private ExecutionErrorInfo errorInfo;

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
