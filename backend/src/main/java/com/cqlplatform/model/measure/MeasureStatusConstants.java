package com.cqlplatform.model.measure;

import java.util.List;
import java.util.Map;

/**
 * Standard measure workflow status constants.
 * Frontend equivalent: frontend/src/constants/measureConstants.ts (MEASURE_STATUS)
 */
public final class MeasureStatusConstants {

    private MeasureStatusConstants() {}

    public static final String DRAFT = "draft";
    public static final String IN_REVIEW = "in-review";
    public static final String ACTIVE = "active";
    public static final String RETIRED = "retired";

    /** Valid status transitions for the measure workflow. */
    public static final Map<String, List<String>> VALID_TRANSITIONS = Map.of(
            DRAFT, List.of(IN_REVIEW),
            IN_REVIEW, List.of(ACTIVE, DRAFT),
            ACTIVE, List.of(RETIRED)
    );
}
