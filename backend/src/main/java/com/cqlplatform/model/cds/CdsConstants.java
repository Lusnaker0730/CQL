package com.cqlplatform.model.cds;

/**
 * Centralized constants for CDS Hooks functionality.
 * Keeps magic strings out of service/controller code.
 */
public final class CdsConstants {

    private CdsConstants() {
    }

    // --- Card indicator levels (CDS Hooks spec) ---

    public static final String INDICATOR_INFO = "info";
    public static final String INDICATOR_WARNING = "warning";
    public static final String INDICATOR_CRITICAL = "critical";

    // --- Feedback outcomes (CDS Hooks spec) ---

    public static final String FEEDBACK_ACCEPTED = "accepted";
    public static final String FEEDBACK_OVERRIDDEN = "overridden";

    // --- Default card text ---

    public static final String SOURCE_LABEL = "CQL Platform";
    public static final String ERROR_SUMMARY = "CDS Service Error";
    public static final String ERROR_DETAIL_PREFIX = "An error occurred: ";
    public static final String NO_RECOMMENDATIONS = "No recommendations at this time.";
}
