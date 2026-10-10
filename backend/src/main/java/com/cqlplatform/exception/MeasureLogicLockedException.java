package com.cqlplatform.exception;

import lombok.Getter;

/**
 * BUG-147 — a change to the evaluated logic (CQL, population mapping, scoring, composite
 * components) of a measure that is not a draft. Approved ({@code active}) and retired logic is
 * immutable and in-review logic is what the reviewer is looking at: changing it means a new
 * version, which starts as a draft and goes through review. Mapped to HTTP 409.
 */
@Getter
public class MeasureLogicLockedException extends RuntimeException {

    private final Long measureId;
    private final String status;

    public MeasureLogicLockedException(Long measureId, String status) {
        super("The logic of measure " + measureId + " cannot change while it is '" + status + "'. "
                + ("in-review".equals(status)
                    ? "Reject it back to draft first, or wait for the review to finish."
                    : "Create a new version (it starts as a draft and goes through review)."));
        this.measureId = measureId;
        this.status = status;
    }
}
