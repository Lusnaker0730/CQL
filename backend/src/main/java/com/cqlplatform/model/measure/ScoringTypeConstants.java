package com.cqlplatform.model.measure;

/**
 * Standard FHIR measure scoring type constants.
 * Frontend equivalent: frontend/src/constants/measureConstants.ts (SCORING_TYPE)
 */
public final class ScoringTypeConstants {

    private ScoringTypeConstants() {}

    public static final String PROPORTION = "proportion";
    public static final String RATIO = "ratio";
    public static final String CONTINUOUS_VARIABLE = "continuous-variable";
    public static final String COHORT = "cohort";
    public static final String COMPOSITE = "composite";

    // Composite scoring methods
    public static final String COMPOSITE_OPPORTUNITY = "opportunity";
    public static final String COMPOSITE_LINEAR = "linear";
}
