package com.cqlplatform.model.ecqm;

import java.util.*;

/**
 * Constants for eCQM CQL generation, based on CMS 2026 eCQM Logic and
 * Implementation Guidance Version 9.0.
 */
public final class EcqmConstants {

    private EcqmConstants() {}

    // ── Standard population define names ──────────────────────────────────
    public static final String INITIAL_POPULATION = "Initial Population";
    public static final String DENOMINATOR = "Denominator";
    // CQL define names use the plural form per CMS eCQM canonical convention; this MUST match
    // the names PopulationEvaluator looks up by string ("Denominator Exclusions" etc.) — singular
    // forms produced silently-empty exclusion populations because the evaluator wouldn't find them.
    public static final String DENOMINATOR_EXCLUSION = "Denominator Exclusions";
    public static final String DENOMINATOR_EXCEPTION = "Denominator Exceptions";
    public static final String NUMERATOR = "Numerator";
    public static final String NUMERATOR_EXCLUSION = "Numerator Exclusions";
    public static final String MEASURE_POPULATION = "Measure Population";
    public static final String MEASURE_POPULATION_EXCLUSION = "Measure Population Exclusion";
    public static final String MEASURE_OBSERVATION = "Measure Observation";

    // ── Dual IP names (ratio) ─────────────────────────────────────────────
    public static final String INITIAL_POPULATION_1 = "Initial Population 1";
    public static final String INITIAL_POPULATION_2 = "Initial Population 2";

    // ── Scoring types ─────────────────────────────────────────────────────
    public static final String SCORING_PROPORTION = "proportion";
    public static final String SCORING_RATIO = "ratio";
    public static final String SCORING_CONTINUOUS_VARIABLE = "continuous-variable";
    public static final String SCORING_COHORT = "cohort";

    // ── SDE standard names and OIDs (CMS v9.0 §5.2) ──────────────────────
    public static final String SDE_ETHNICITY = "SDE Ethnicity";
    public static final String SDE_RACE = "SDE Race";
    public static final String SDE_SEX = "SDE Sex";
    public static final String SDE_PAYER = "SDE Payer";

    public static final Map<String, String> SDE_VALUE_SET_OIDS = Map.of(
            SDE_ETHNICITY, "urn:oid:2.16.840.1.114222.4.11.837",
            SDE_RACE, "urn:oid:2.16.840.1.114222.4.11.836",
            SDE_SEX, "urn:oid:2.16.840.1.113762.1.4.1",
            SDE_PAYER, "urn:oid:2.16.840.1.114222.4.11.3591"
    );

    // ── Aggregate methods for continuous variable observations ──────────
    public static final List<String> AGGREGATE_METHODS = List.of(
            "Count", "Sum", "Average", "Median", "Maximum", "Minimum", "Percentile"
    );

    // ── Improvement notation ──────────────────────────────────────────────
    public static final String IMPROVEMENT_INCREASE = "increase";
    public static final String IMPROVEMENT_DECREASE = "decrease";

    // ── Population basis options ──────────────────────────────────────────
    public static final List<String> POPULATION_BASIS_OPTIONS = List.of(
            "boolean", "Encounter", "Procedure", "MedicationRequest",
            "MedicationAdministration", "Observation", "ServiceRequest",
            "Condition", "Immunization", "Coverage"
    );

    // ── Scoring type → required/optional populations ──────────────────────
    public static final Map<String, List<String>> REQUIRED_POPULATIONS = Map.of(
            SCORING_PROPORTION, List.of(INITIAL_POPULATION, DENOMINATOR, NUMERATOR),
            SCORING_RATIO, List.of(INITIAL_POPULATION, DENOMINATOR, NUMERATOR),
            SCORING_CONTINUOUS_VARIABLE, List.of(INITIAL_POPULATION, MEASURE_POPULATION),
            SCORING_COHORT, List.of(INITIAL_POPULATION)
    );

    public static final Map<String, List<String>> OPTIONAL_POPULATIONS = Map.of(
            SCORING_PROPORTION, List.of(DENOMINATOR_EXCLUSION, DENOMINATOR_EXCEPTION, NUMERATOR_EXCLUSION),
            SCORING_RATIO, List.of(DENOMINATOR_EXCLUSION, NUMERATOR_EXCLUSION),
            SCORING_CONTINUOUS_VARIABLE, List.of(MEASURE_POPULATION_EXCLUSION),
            SCORING_COHORT, List.of()
    );

    /**
     * When a required population has an empty expression tree, it inherits from its parent.
     * e.g. empty Denominator → references "Initial Population".
     */
    public static final Map<String, String> POPULATION_PARENT = Map.of(
            DENOMINATOR, INITIAL_POPULATION,
            NUMERATOR, DENOMINATOR,
            MEASURE_POPULATION, INITIAL_POPULATION
    );

    /**
     * All population keys that can appear in a population group, in CMS canonical order.
     */
    public static final List<String> ALL_POPULATION_KEYS = List.of(
            "initial-population",
            "denominator",
            "denominator-exclusion",
            "denominator-exception",
            "numerator",
            "numerator-exclusion",
            "measure-population",
            "measure-population-exclusion"
    );

    /**
     * Map population JSON key to CQL define name.
     */
    public static final Map<String, String> POPULATION_KEY_TO_DEFINE = Map.ofEntries(
            Map.entry("initial-population", INITIAL_POPULATION),
            Map.entry("denominator", DENOMINATOR),
            Map.entry("denominator-exclusion", DENOMINATOR_EXCLUSION),
            Map.entry("denominator-exception", DENOMINATOR_EXCEPTION),
            Map.entry("numerator", NUMERATOR),
            Map.entry("numerator-exclusion", NUMERATOR_EXCLUSION),
            Map.entry("measure-population", MEASURE_POPULATION),
            Map.entry("measure-population-exclusion", MEASURE_POPULATION_EXCLUSION)
    );

    /**
     * Reverse: CQL define name → population JSON key.
     */
    public static final Map<String, String> DEFINE_TO_POPULATION_KEY;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        POPULATION_KEY_TO_DEFINE.forEach((k, v) -> m.put(v, k));
        DEFINE_TO_POPULATION_KEY = Collections.unmodifiableMap(m);
    }
}
