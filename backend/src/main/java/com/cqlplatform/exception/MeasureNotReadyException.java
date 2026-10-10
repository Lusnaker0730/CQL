package com.cqlplatform.exception;

import lombok.Getter;

import java.util.List;

/**
 * PAT-249 — a measure cannot be submitted for review or approved while its approval readiness
 * check has blockers (CQL that does not translate, FHIR-invalid test cases, test cases that do
 * not pass on the current logic). Mapped to HTTP 409 {@code Measure Not Ready}; {@code details}
 * lists the blockers so the caller can show exactly what to fix.
 */
@Getter
public class MeasureNotReadyException extends RuntimeException {

    private final List<String> details;

    public MeasureNotReadyException(String message, List<String> details) {
        super(message);
        this.details = details == null ? List.of() : List.copyOf(details);
    }
}
