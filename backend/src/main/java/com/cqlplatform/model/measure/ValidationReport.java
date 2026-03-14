package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationReport {

    @Builder.Default
    private List<ValidationIssue> issues = new ArrayList<>();
    private int errorCount;
    private int warningCount;
    private int infoCount;
    private boolean valid;
    private long validationTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationIssue {
        private String severity;   // ERROR, WARNING, INFO
        private String category;   // CQL, POPULATIONS, METADATA, TEST_CASES, QI_CORE
        private String message;
        private String field;      // optional: which field or expression
        private String fixHint;    // optional: suggestion for fixing
    }

    public void addError(String category, String message, String field, String fixHint) {
        issues.add(ValidationIssue.builder()
                .severity("ERROR").category(category).message(message).field(field).fixHint(fixHint).build());
        errorCount++;
    }

    public void addWarning(String category, String message, String field, String fixHint) {
        issues.add(ValidationIssue.builder()
                .severity("WARNING").category(category).message(message).field(field).fixHint(fixHint).build());
        warningCount++;
    }

    public void addInfo(String category, String message, String field, String fixHint) {
        issues.add(ValidationIssue.builder()
                .severity("INFO").category(category).message(message).field(field).fixHint(fixHint).build());
        infoCount++;
    }

    public void complete(long timeMs) {
        this.validationTimeMs = timeMs;
        this.valid = errorCount == 0;
    }
}
