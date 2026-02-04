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
}
