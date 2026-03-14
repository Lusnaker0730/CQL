package com.cqlplatform.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared CQL text parsing utilities — regex patterns and helpers
 * used by ExternalCqlLibraryService, EcqmExternalCqlLibraryService, and CqlImportService.
 */
public final class CqlParsingUtils {

    private CqlParsingUtils() {}

    public static final Pattern LIBRARY_PATTERN = Pattern
            .compile("library\\s+\"?([\\w_]+)\"?(?:\\s+version\\s+'([^']*)')?");

    public static final Pattern FHIR_VERSION_PATTERN = Pattern
            .compile("using\\s+FHIR\\s+version\\s+'([^']*)'");

    /** Extract library name from CQL source, or "Unknown" if not found. */
    public static String parseLibraryName(String cql) {
        Matcher m = LIBRARY_PATTERN.matcher(cql);
        return m.find() ? m.group(1) : "Unknown";
    }

    /** Extract library version from CQL source, or null if not found. */
    public static String parseLibraryVersion(String cql) {
        Matcher m = LIBRARY_PATTERN.matcher(cql);
        return m.find() ? m.group(2) : null;
    }

    /** Extract FHIR version from CQL source, or null if not found. */
    public static String parseFhirVersion(String cql) {
        Matcher m = FHIR_VERSION_PATTERN.matcher(cql);
        return m.find() ? m.group(1) : null;
    }
}
