package com.cqlplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        public static final String KIND_EXPRESSION = "expression";
        public static final String KIND_FUNCTION = "function";

        private String name;
        private String context;
        private String accessLevel;
        private String resultType;
        /**
         * PAT-237: {@code expression} (a plain define) or {@code function} (a {@code define function}
         * with {@link #operands}). Older stored metadata has no kind — treat null as expression.
         */
        private String kind;
        /** PAT-237: the declared operands of a function, in order; null for a plain define. */
        private List<OperandInfo> operands;

        @JsonIgnore
        public boolean isFunction() {
            return KIND_FUNCTION.equals(kind);
        }
    }

    /** PAT-237: one declared operand of a {@code define function}. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperandInfo {
        private String name;
        /** The declared type as written in CQL terms, e.g. {@code Integer}, {@code List<FHIR.Observation>}. */
        private String type;
    }
}
