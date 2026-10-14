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
    /** PAT-259: QM IG composite-measure-scoring — in the numerator only when in every component numerator it is eligible for. */
    public static final String COMPOSITE_ALL_OR_NOTHING = "all-or-nothing";
    /** PAT-259: QM IG composite-measure-scoring — weighted average of the component scores (cqfm-weight). */
    public static final String COMPOSITE_WEIGHTED = "weighted";
    /** PAT-259: every composite scoring method the platform evaluates (the THO composite-measure-scoring codes). */
    public static final java.util.Set<String> COMPOSITE_SCORING_METHODS = java.util.Set.of(
            COMPOSITE_OPPORTUNITY, COMPOSITE_ALL_OR_NOTHING, COMPOSITE_LINEAR, COMPOSITE_WEIGHTED);
}
