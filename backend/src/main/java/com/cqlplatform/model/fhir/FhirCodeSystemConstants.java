package com.cqlplatform.model.fhir;

/**
 * Centralized FHIR code system URI constants.
 * Eliminates hardcoded URI strings scattered across measure and FHIR services.
 */
public final class FhirCodeSystemConstants {

    private FhirCodeSystemConstants() {}

    // HL7 Terminology Code Systems
    public static final String CS_LIBRARY_TYPE = "http://terminology.hl7.org/CodeSystem/library-type";
    public static final String CS_MEASURE_SCORING = "http://terminology.hl7.org/CodeSystem/measure-scoring";
    public static final String CS_MEASURE_POPULATION = "http://terminology.hl7.org/CodeSystem/measure-population";

    // Clinical Terminology Code Systems
    public static final String CS_SNOMED_CT = "http://snomed.info/sct";
    public static final String CS_ICD10_CM = "http://hl7.org/fhir/sid/icd-10-cm";
    public static final String CS_LOINC = "http://loinc.org";

    // FHIR Structure Definition base URL
    public static final String STRUCTURE_DEFINITION_BASE = "http://hl7.org/fhir/StructureDefinition/";
}
