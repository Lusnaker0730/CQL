package com.cqlplatform.security;

import java.util.Set;
import java.util.regex.Pattern;

public final class InputValidator {

    private InputValidator() {}

    private static final Set<String> ALLOWED_FHIR_RESOURCE_TYPES = Set.of(
            "Patient", "Observation", "Condition", "Procedure", "Encounter",
            "MedicationRequest", "MedicationStatement", "AllergyIntolerance",
            "Immunization", "DiagnosticReport", "CarePlan", "CareTeam",
            "Goal", "ServiceRequest", "Practitioner", "Organization",
            "Location", "Device", "Specimen", "Bundle", "Subscription",
            "ValueSet", "CodeSystem", "Library", "Measure", "MeasureReport",
            "Questionnaire", "QuestionnaireResponse", "Coverage",
            "Claim", "ExplanationOfBenefit", "DocumentReference",
            "Composition", "RelatedPerson", "Group"
    );

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,128}$");
    private static final Pattern SAFE_PARAMS_PATTERN = Pattern.compile("^[a-zA-Z0-9._:/?&=,| -]{0,2000}$");
    private static final Pattern DATE_PARAM_PATTERN = Pattern.compile("^\\d{4}(-\\d{2}(-\\d{2})?)?$");
    private static final Pattern NAME_PARAM_PATTERN = Pattern.compile("^[a-zA-Z' \\-]{1,100}$");

    public static boolean isValidFhirResourceType(String resourceType) {
        return resourceType != null && ALLOWED_FHIR_RESOURCE_TYPES.contains(resourceType);
    }

    public static boolean isValidResourceId(String id) {
        return id != null && SAFE_ID_PATTERN.matcher(id).matches();
    }

    public static boolean isValidSearchParams(String params) {
        return params == null || SAFE_PARAMS_PATTERN.matcher(params).matches();
    }

    public static boolean isValidUrl(String url) {
        if (url == null) return true;
        return url.startsWith("http://") || url.startsWith("https://");
    }

    public static boolean isValidDateParam(String date) {
        return date == null || DATE_PARAM_PATTERN.matcher(date).matches();
    }

    public static boolean isValidNameParam(String name) {
        return name == null || NAME_PARAM_PATTERN.matcher(name).matches();
    }
}
