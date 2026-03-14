package com.cqlplatform.model.measure;

/**
 * Status constants for measure evaluation and async operations (bulk export, etc.).
 * Distinct from MeasureStatusConstants which tracks the measure workflow lifecycle.
 */
public final class EvaluationStatusConstants {

    private EvaluationStatusConstants() {}

    public static final String COMPLETE = "complete";
    public static final String IN_PROGRESS = "in-progress";
    public static final String FAILED = "failed";
}
