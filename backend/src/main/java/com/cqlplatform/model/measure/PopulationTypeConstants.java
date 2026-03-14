package com.cqlplatform.model.measure;

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
}
