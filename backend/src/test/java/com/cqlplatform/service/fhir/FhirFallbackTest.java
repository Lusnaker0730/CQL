package com.cqlplatform.service.fhir;

import com.cqlplatform.exception.FhirServerUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import ca.uhn.fhir.context.FhirContext;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class FhirFallbackTest {

    @Mock
    private FhirContext fhirContext;

    @Mock
    private FhirClientFactory fhirClientFactory;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private FhirImplementationGuideService igService;

    @Mock
    private VsacService vsacService;

    @InjectMocks
    private FhirDataProviderService dataProviderService;

    @InjectMocks
    private FhirTerminologyService terminologyService;

    // ===== FhirDataProviderService fallbacks =====

    @Test
    void searchResourcesFallback_shouldThrowFhirServerUnavailableException() {
        Throwable cause = new IOException("Connection refused");

        assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(dataProviderService,
                        "searchResourcesFallback", "http://fhir.example.com", "Patient", (String) null, cause))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("Connection refused")
                .hasCause(cause);
    }

    @Test
    void searchPatientsByDemographicsFallback_shouldThrowFhirServerUnavailableException() {
        Throwable cause = new IOException("Timeout");

        assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(dataProviderService,
                        "searchPatientsByDemographicsFallback",
                        "http://fhir.example.com", "Smith", "John", "1990-01-01", (String) null, cause))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("Timeout")
                .hasCause(cause);
    }

    // ===== FhirTerminologyService fallbacks =====

    @Test
    void validateCodeFallback_shouldThrowFhirServerUnavailableException() {
        Throwable cause = new IOException("Connection reset");

        assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(terminologyService,
                        "validateCodeFallback",
                        "http://snomed.info/sct", "123456", "http://valueset.example.com", cause))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("Connection reset")
                .hasCause(cause);
    }

    @Test
    void searchValueSetsFallback_shouldThrowFhirServerUnavailableException() {
        Throwable cause = new IOException("Server error");

        assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(terminologyService,
                        "searchValueSetsFallback", "diabetes", cause))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("Server error")
                .hasCause(cause);
    }
}
