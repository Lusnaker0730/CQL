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
    private static final Pattern CACHE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern SAFE_PARAMS_PATTERN = Pattern.compile("^[a-zA-Z0-9._:/?&=,| -]{0,2000}$");
    private static final Pattern DATE_PARAM_PATTERN = Pattern.compile("^\\d{4}(-\\d{2}(-\\d{2})?)?$");
    private static final Pattern NAME_PARAM_PATTERN = Pattern.compile("^[a-zA-Z' \\-]{1,100}$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9:./_|\\\\-]{1,500}$");

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
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null) return false;
            // Block URLs with embedded credentials
            if (uri.getUserInfo() != null) return false;
            // Resolve and check for private IPs
            java.net.InetAddress addr = java.net.InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() ||
                addr.isSiteLocalAddress() || addr.isAnyLocalAddress() ||
                addr.isMulticastAddress()) {
                // Allow localhost in dev, but still validate
                return isLocalDevelopment();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final Boolean IS_LOCAL_DEV = computeIsLocalDevelopment();

    private static boolean isLocalDevelopment() {
        return IS_LOCAL_DEV;
    }

    private static boolean computeIsLocalDevelopment() {
        String profile = System.getProperty("spring.profiles.active", System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", ""));
        return profile.contains("dev") || profile.contains("docker");
    }

    public static void requireValidUrl(String url) {
        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid FHIR server URL");
        }
    }

    public static void requireValidFhirResourceType(String resourceType) {
        if (!isValidFhirResourceType(resourceType)) {
            throw new IllegalArgumentException("Invalid FHIR resource type");
        }
    }

    public static void requireValidResourceId(String id) {
        if (!isValidResourceId(id)) {
            throw new IllegalArgumentException("Invalid resource ID");
        }
    }

    public static void requireValidCacheName(String name) {
        if (!isValidCacheName(name)) {
            throw new IllegalArgumentException("Invalid cache name");
        }
    }

    public static boolean isValidCacheName(String name) {
        return name != null && CACHE_NAME_PATTERN.matcher(name).matches();
    }

    public static void requireValidSearchParams(String params) {
        if (!isValidSearchParams(params)) {
            throw new IllegalArgumentException("Invalid search parameters");
        }
    }

    public static boolean isValidDateParam(String date) {
        return date == null || DATE_PARAM_PATTERN.matcher(date).matches();
    }

    public static boolean isValidNameParam(String name) {
        return name == null || NAME_PARAM_PATTERN.matcher(name).matches();
    }

    public static boolean isValidIdentifierParam(String identifier) {
        return identifier == null || IDENTIFIER_PATTERN.matcher(identifier).matches();
    }

    public static void requireValidIdentifierParam(String identifier) {
        if (!isValidIdentifierParam(identifier)) {
            throw new IllegalArgumentException("Invalid identifier parameter");
        }
    }

    public static boolean isValidFhirCanonicalUrl(String url) {
        if (url == null) return true;
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    public static void requireValidFhirCanonicalUrl(String url) {
        if (!isValidFhirCanonicalUrl(url)) {
            throw new IllegalArgumentException("Invalid FHIR canonical URL");
        }
    }

    /**
     * Escape SQL LIKE wildcard characters (%, _) in user input to prevent
     * LIKE wildcard injection when building JPQL LIKE patterns.
     */
    public static String escapeLikeWildcards(String input) {
        if (input == null) return null;
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
