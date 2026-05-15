package com.cqlplatform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class InputValidatorTest {

    // --- FHIR Resource Type Validation ---

    @ParameterizedTest
    @ValueSource(strings = {"Patient", "Observation", "Condition", "Procedure", "Encounter",
            "MedicationRequest", "Bundle", "ValueSet", "Library", "Measure"})
    void isValidFhirResourceType_shouldAcceptAllowedTypes(String type) {
        assertThat(InputValidator.isValidFhirResourceType(type)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Invalid", "patient", "PATIENT", "FakeResource", "Script", ""})
    void isValidFhirResourceType_shouldRejectInvalidTypes(String type) {
        assertThat(InputValidator.isValidFhirResourceType(type)).isFalse();
    }

    @Test
    void isValidFhirResourceType_shouldRejectNull() {
        assertThat(InputValidator.isValidFhirResourceType(null)).isFalse();
    }

    // --- Resource ID Validation ---

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "patient-1", "test.resource", "a_b_c", "A1.B2-C3"})
    void isValidResourceId_shouldAcceptValidIds(String id) {
        assertThat(InputValidator.isValidResourceId(id)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "id with spaces", "id<script>", "id;drop", "../etc/passwd"})
    void isValidResourceId_shouldRejectInvalidIds(String id) {
        assertThat(InputValidator.isValidResourceId(id)).isFalse();
    }

    @Test
    void isValidResourceId_shouldRejectNull() {
        assertThat(InputValidator.isValidResourceId(null)).isFalse();
    }

    @Test
    void isValidResourceId_shouldRejectOverlongId() {
        String longId = "a".repeat(129);
        assertThat(InputValidator.isValidResourceId(longId)).isFalse();
    }

    @Test
    void isValidResourceId_shouldAcceptMaxLengthId() {
        String maxId = "a".repeat(128);
        assertThat(InputValidator.isValidResourceId(maxId)).isTrue();
    }

    // --- URL Validation ---

    @Test
    void isValidUrl_shouldAcceptHttpUrl() {
        assertThat(InputValidator.isValidUrl("http://example.com/fhir")).isTrue();
    }

    @Test
    void isValidUrl_shouldAcceptHttpsUrl() {
        // Use a well-known domain that resolves reliably in CI environments
        assertThat(InputValidator.isValidUrl("https://example.com/r4")).isTrue();
    }

    @Test
    void isValidUrl_shouldRejectFtpUrl() {
        assertThat(InputValidator.isValidUrl("ftp://example.com")).isFalse();
    }

    @Test
    void isValidUrl_shouldRejectJavascriptUrl() {
        assertThat(InputValidator.isValidUrl("javascript:alert(1)")).isFalse();
    }

    @Test
    void isValidUrl_shouldRejectNull() {
        assertThat(InputValidator.isValidUrl(null)).isFalse();
    }

    @Test
    void isValidUrl_shouldRejectBlank() {
        assertThat(InputValidator.isValidUrl("")).isFalse();
        assertThat(InputValidator.isValidUrl("   ")).isFalse();
    }

    // --- Search Params Validation ---

    @Test
    void isValidSearchParams_shouldAcceptValidParams() {
        assertThat(InputValidator.isValidSearchParams("patient=123&category=vital-signs")).isTrue();
    }

    @Test
    void isValidSearchParams_shouldAcceptNull() {
        assertThat(InputValidator.isValidSearchParams(null)).isTrue();
    }

    @Test
    void isValidSearchParams_shouldAcceptEmptyString() {
        assertThat(InputValidator.isValidSearchParams("")).isTrue();
    }

    @Test
    void isValidSearchParams_shouldRejectScriptInjection() {
        assertThat(InputValidator.isValidSearchParams("<script>alert(1)</script>")).isFalse();
    }

    @Test
    void isValidSearchParams_shouldRejectSqlInjection() {
        assertThat(InputValidator.isValidSearchParams("'; DROP TABLE users;--")).isFalse();
    }

    // --- PAT-165 follow-up: internal hostname allow-list ---

    @Test
    void isValidUrl_shouldAllowDockerInternalHapiFhirHost() {
        // hapi-fhir is the platform's own FHIR server (Docker compose service
        // name). It resolves to a private IP but is explicitly allow-listed.
        assertThat(InputValidator.isValidUrl("http://hapi-fhir:8080/fhir")).isTrue();
    }

    @Test
    void isValidUrl_shouldAllowDockerInternalTwFhirGenerator() {
        assertThat(InputValidator.isValidUrl("http://taiwan-fhir-generator:5000/fhir")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data",  // AWS metadata (link-local)
            "http://192.168.1.1/admin",                 // site-local
            "http://10.0.0.1/internal",                 // site-local
    })
    void isValidUrl_shouldStillRejectOtherPrivateIps(String url) {
        // The allow-list is ONLY for known internal hostnames; literal private
        // IPs are still blocked (SSRF surface stays narrow).
        assertThat(InputValidator.isValidUrl(url)).isFalse();
    }
}
