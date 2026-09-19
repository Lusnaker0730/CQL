package com.cqlplatform.service.measure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the canonical URLs of the artifacts this installation publishes (PAT-229).
 *
 * <p>A canonical URL identifies a measure or library across organisations, so it has to be
 * stable and owned by the publisher — typically {@code https://<hospital-domain>/fhir}. It is
 * configured with {@code FHIR_CANONICAL_BASE}; when that is missing the public base URL of
 * the deployment is used, and as a last resort a placeholder that the conformance report
 * flags, because packages exported with it are not globally unique.
 */
@Component
public class FhirCanonicalResolver {

    static final String PLACEHOLDER_BASE = "http://localhost:8080/fhir";

    private final String base;
    private final boolean configured;

    public FhirCanonicalResolver(@Value("${fhir.canonical-base:}") String canonicalBase,
                                 @Value("${APP_BASE_URL:}") String appBaseUrl) {
        if (canonicalBase != null && !canonicalBase.isBlank()) {
            this.base = stripTrailingSlash(canonicalBase.trim());
            this.configured = true;
        } else if (appBaseUrl != null && !appBaseUrl.isBlank()) {
            this.base = stripTrailingSlash(appBaseUrl.trim()) + "/fhir";
            this.configured = true;
        } else {
            this.base = PLACEHOLDER_BASE;
            this.configured = false;
        }
    }

    public String getBase() {
        return base;
    }

    /** False when neither FHIR_CANONICAL_BASE nor APP_BASE_URL is set. */
    public boolean isConfigured() {
        return configured;
    }

    public String measureUrl(String name) {
        return base + "/Measure/" + idPart(name);
    }

    public String libraryUrl(String name) {
        return base + "/Library/" + idPart(name);
    }

    /** PAT-230: canonical URL of a value set authored in this installation. */
    public String valueSetUrl(String name) {
        return base + "/ValueSet/" + idPart(name);
    }

    /** {@code url|version} — how FHIR pins a canonical reference to a business version. */
    public static String versioned(String url, String version) {
        return version == null || version.isBlank() ? url : url + "|" + version;
    }

    /**
     * FHIR ids allow {@code [A-Za-z0-9\-\.]{1,64}}. Anything else becomes a dash so a measure
     * named "LOS (days)" still yields a valid id and URL.
     */
    public static String idPart(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9.\\-]+", "-").replaceAll("^-+|-+$", "");
        if (cleaned.isEmpty()) return "unnamed";
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
