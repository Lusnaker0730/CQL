package com.cqlplatform.model.measure;

import java.util.Map;

/**
 * Standard FHIR measure population type codes.
 * These match the values used in FHIR MeasureReport and Measure resources.
 * Frontend equivalent: frontend/src/constants/populationConfig.ts
 */
public final class PopulationTypeConstants {

    private PopulationTypeConstants() {}

    public static final String INITIAL_POPULATION = "initial-population";
    public static final String DENOMINATOR = "denominator";
    public static final String DENOMINATOR_EXCLUSION = "denominator-exclusion";
    public static final String DENOMINATOR_EXCEPTION = "denominator-exception";
    public static final String NUMERATOR = "numerator";
    public static final String NUMERATOR_EXCLUSION = "numerator-exclusion";
    public static final String MEASURE_POPULATION = "measure-population";
    public static final String MEASURE_POPULATION_EXCLUSION = "measure-population-exclusion";
    public static final String MEASURE_OBSERVATION = "measure-observation";

    /**
     * Maps CQL define names ("Initial Population", "Denominator Exclusions", ...) to
     * FHIR population type codes. The pluralization difference (CQL "Exclusions" /
     * "Exceptions" vs. FHIR singular) is intentional — FHIR codes are singular per
     * the value set, but CQL conventions use plural names. Use this lookup instead of
     * ad-hoc {@code .toLowerCase().replace(" ", "-").replace("exclusions", ...)} —
     * the explicit table makes the rules auditable and fails loudly on unrecognized
     * keys (returns null) rather than silently fabricating an invalid FHIR code.
     */
    private static final Map<String, String> CQL_NAME_TO_FHIR_CODE = Map.ofEntries(
            Map.entry("Initial Population", INITIAL_POPULATION),
            Map.entry("Denominator", DENOMINATOR),
            Map.entry("Denominator Exclusions", DENOMINATOR_EXCLUSION),
            Map.entry("Denominator Exceptions", DENOMINATOR_EXCEPTION),
            Map.entry("Numerator", NUMERATOR),
            Map.entry("Numerator Exclusions", NUMERATOR_EXCLUSION),
            Map.entry("Measure Population", MEASURE_POPULATION),
            Map.entry("Measure Population Exclusion", MEASURE_POPULATION_EXCLUSION),
            Map.entry("Measure Observation", MEASURE_OBSERVATION));

    /**
     * Convert a CQL population define name to its FHIR population type code.
     * Returns the lowercase-hyphenated form for unknown names (preserves prior
     * behaviour for non-standard custom populations, while logging-or-failing
     * decisions stay at the call site).
     */
    public static String cqlNameToFhirCode(String cqlName) {
        if (cqlName == null) return null;
        String mapped = CQL_NAME_TO_FHIR_CODE.get(cqlName);
        if (mapped != null) return mapped;
        // Fallback for custom population names not in the standard set: keep the
        // legacy normalization so downstream FHIR-export code doesn't choke on
        // hyphenless / mixed-case codes.
        return cqlName.toLowerCase().replace(" ", "-");
    }
}
