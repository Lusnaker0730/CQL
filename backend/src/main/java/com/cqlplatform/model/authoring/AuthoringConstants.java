package com.cqlplatform.model.authoring;

import java.util.Map;

/**
 * Centralized constants for the CDS Authoring Tool.
 * Keeps magic strings out of service/controller/entity code.
 */
public final class AuthoringConstants {

    private AuthoringConstants() {
    }

    // --- CQL system definition names (used by CqlArtifactBuilder, tests, and frontend) ---

    public static final String DEF_MEETS_INCLUSION = "MeetsInclusionCriteria";
    public static final String DEF_MEETS_EXCLUSION = "MeetsExclusionCriteria";
    public static final String DEF_IN_POPULATION = "InPopulation";
    public static final String DEF_RECOMMENDATION = "Recommendation";
    public static final String DEF_ERRORS = "Errors";

    // --- Special subpopulation IDs ---

    public static final String SUBPOP_DOESNT_MEET_INCLUSION = "__doesnt_meet_inclusion__";
    public static final String SUBPOP_MEETS_EXCLUSION = "__meets_exclusion__";

    // --- Default artifact values ---

    public static final String DEFAULT_VERSION = "1.0.0";
    public static final String DEFAULT_STATUS = "draft";
    public static final String DEFAULT_FHIR_VERSION = "4.0.1";
    public static final boolean DEFAULT_EXPERIMENTAL = false;

    // --- FHIR version mappings ---

    public static final Map<String, String> FHIR_VERSION_MAP = Map.of(
            "DSTU2", "1.0.2",
            "STU3", "3.0.2",
            "R4", "4.0.1");

    public static final Map<String, String> FHIR_HELPERS_VERSION_MAP = Map.of(
            "DSTU2", "1.0.2",
            "STU3", "3.0.0",
            "R4", "4.0.1");

    // --- Code system URI → display name ---

    public static final Map<String, String> CODE_SYSTEM_NAMES = Map.of(
            "http://loinc.org", "LOINC",
            "http://snomed.info/sct", "SNOMED",
            "http://hl7.org/fhir/sid/icd-10-cm", "ICD-10-CM",
            "http://hl7.org/fhir/sid/icd-9-cm", "ICD-9-CM",
            "http://www.nlm.nih.gov/research/umls/rxnorm", "RXNORM",
            "http://ncimeta.nci.nih.gov", "NCI");

    /**
     * Look up the display name for a code system URL, falling back to the URL itself.
     */
    public static String getCodeSystemDisplayName(String systemUrl) {
        return CODE_SYSTEM_NAMES.getOrDefault(systemUrl, systemUrl);
    }
}
