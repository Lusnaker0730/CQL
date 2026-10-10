package com.cqlplatform.model.fhir;

/**
 * Canonical URLs used when packaging a measure for exchange (PAT-229).
 *
 * <p>Every URL below was read from the published StructureDefinition JSON, not from memory:
 * <ul>
 *   <li>HL7 Quality Measure IG 5.0.0 (STU5, 2024-11) — {@code hl7.org/fhir/us/cqfmeasures}</li>
 *   <li>Canonical Resource Management Infrastructure 2.0.0 (STU2) — {@code hl7.org/fhir/uv/crmi}</li>
 *   <li>Using CQL With FHIR 2.0.0 (STU2, 2025-07) — {@code hl7.org/fhir/uv/cql}</li>
 * </ul>
 * The QM IG is a US-realm guide, but its measure profiles and extensions are what measure
 * tooling (MADiE, HAPI clinical reasoning, cqf-tooling) actually reads; nothing in them is
 * US-specific content.
 */
public final class CqfmConstants {

    private CqfmConstants() {}

    private static final String QM = "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/";
    private static final String CRMI = "http://hl7.org/fhir/uv/crmi/StructureDefinition/";
    private static final String CQL = "http://hl7.org/fhir/uv/cql/StructureDefinition/";

    // ---- Measure profiles ----
    /** Requires url, version, title, description. */
    public static final String PROFILE_CRMI_SHAREABLE_MEASURE = CRMI + "crmi-shareablemeasure";
    /** Adds date; the QM IG requires it of measures in active status (conformance requirement 3.1). */
    public static final String PROFILE_CRMI_PUBLISHABLE_MEASURE = CRMI + "crmi-publishablemeasure";
    public static final String PROFILE_COMPUTABLE_MEASURE = QM + "computable-measure-cqfm";
    public static final String PROFILE_PROPORTION_MEASURE = QM + "proportion-measure-cqfm";
    public static final String PROFILE_RATIO_MEASURE = QM + "ratio-measure-cqfm";
    public static final String PROFILE_CV_MEASURE = QM + "cv-measure-cqfm";
    public static final String PROFILE_COHORT_MEASURE = QM + "cohort-measure-cqfm";
    public static final String PROFILE_COMPOSITE_MEASURE = QM + "composite-measure-cqfm";

    // ---- Library profiles ----
    /** Requires url, version, title, description. */
    public static final String PROFILE_CRMI_SHAREABLE_LIBRARY = CRMI + "crmi-shareablelibrary";
    /** Requires name and a content attachment (text/cql). */
    public static final String PROFILE_CQL_LIBRARY = CQL + "cql-library";
    /** Requires a content attachment (application/elm+json). */
    public static final String PROFILE_ELM_JSON_LIBRARY = CQL + "elm-json-library";

    // ---- Extensions ----
    /** valueCode (FHIR type name); on Measure, Measure.group or Measure.group.population. */
    public static final String EXT_POPULATION_BASIS = QM + "cqfm-populationBasis";
    /** valueCode bound to measure-aggregate-method; on a measure-observation population. */
    public static final String EXT_AGGREGATE_METHOD = QM + "cqfm-aggregateMethod";
    /** valueString = id of the population the observation is computed over. */
    public static final String EXT_CRITERIA_REFERENCE = QM + "cqfm-criteriaReference";
    /** valueCodeableConcept; on Measure or Measure.group. */
    public static final String EXT_SCORING_UNIT = QM + "cqfm-scoringUnit";
    /** valueCanonical → a module-definition Library, conventionally contained. */
    public static final String EXT_EFFECTIVE_DATA_REQUIREMENTS = CRMI + "crmi-effectiveDataRequirements";

    /** Id of the contained module-definition Library referenced by {@link #EXT_EFFECTIVE_DATA_REQUIREMENTS}. */
    public static final String EFFECTIVE_DATA_REQUIREMENTS_ID = "effective-data-requirements";

    // ---- Code systems ----
    public static final String CS_IMPROVEMENT_NOTATION = "http://terminology.hl7.org/CodeSystem/measure-improvement-notation";
    public static final String CS_DATA_USAGE = "http://terminology.hl7.org/CodeSystem/measure-data-usage";
    public static final String USAGE_SUPPLEMENTAL_DATA = "supplemental-data";
    public static final String USAGE_RISK_ADJUSTMENT_FACTOR = "risk-adjustment-factor";

    /** Media type for a criteria expression that names a CQL define (QM IG conformance requirement 3.8). */
    public static final String LANGUAGE_CQL_IDENTIFIER = "text/cql-identifier";

    /** FHIRHelpers as published by the Using CQL With FHIR IG. */
    public static final String FHIR_HELPERS_CANONICAL = "http://hl7.org/fhir/uv/cql/Library/FHIRHelpers";

    /** Population code of a measure observation. */
    public static final String POPULATION_MEASURE_OBSERVATION = "measure-observation";
}
