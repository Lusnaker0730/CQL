package com.cqlplatform.exception;

import lombok.Getter;

/**
 * Thrown when a stored measure definition is asked to evaluate against patient data while
 * not in the {@code active} lifecycle status (PAT-219, readiness review Critical #2).
 *
 * <p>Draft / in-review / retired logic has not (or no longer) passed review; running it
 * against a live FHIR server means CDS alerts, quality rates and dashboards could be driven
 * by unvalidated clinical logic. Such logic is verified through the test-case sandbox
 * instead. Mapped to HTTP 409 by {@link GlobalExceptionHandler} — the request is well-formed,
 * it just conflicts with the measure's current state.
 */
@Getter
public class MeasureNotEvaluableException extends RuntimeException {

    private final Long measureId;
    private final String status;

    public MeasureNotEvaluableException(Long measureId, String status) {
        super("Measure " + measureId + " cannot be evaluated: status is '" + status
                + "' but only 'active' measures may run against patient data. "
                + "Verify draft logic with test cases, or submit the measure for review and approve it first.");
        this.measureId = measureId;
        this.status = status;
    }
}
