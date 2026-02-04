package com.cqlplatform.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CqlTranslationResponse {
    private boolean success;
    private String elm;
    private String elmJson;
    private List<CqlError> errors;
    private List<CqlError> warnings;
    private TranslationMetadata metadata;

    @Data
    @Builder
    public static class CqlError {
        private String severity;
        private String message;
        private Integer startLine;
        private Integer startColumn;
        private Integer endLine;
        private Integer endColumn;
        private String errorType;
    }

    @Data
    @Builder
    public static class TranslationMetadata {
        private String libraryId;
        private String libraryVersion;
        private List<String> usings;
        private List<String> includes;
        private List<String> parameters;
        private List<String> valueSets;
        private List<String> codes;
        private List<String> concepts;
        private List<ExpressionInfo> expressions;
    }

    @Data
    @Builder
    public static class ExpressionInfo {
        private String name;
        private String context;
        private String accessLevel;
        private String resultType;
    }
}
