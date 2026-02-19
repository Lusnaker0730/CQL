package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized FHIR client creation with standard logging interceptors.
 * Replaces duplicated fhirContext.newRestfulGenericClient() + interceptor setup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FhirClientFactory {

    private final FhirContext fhirContext;

    @Value("${fhir.server.url:http://hapi-fhir:8080/fhir}")
    private String defaultFhirServerUrl;

    /**
     * Creates a FHIR client with logging interceptors for the given URL.
     * Falls back to the default FHIR server URL if null is provided.
     */
    public IGenericClient createClient(String fhirServerUrl) {
        String serverUrl = fhirServerUrl != null ? fhirServerUrl : defaultFhirServerUrl;
        log.debug("Creating FHIR Client for URL: {}", serverUrl);
        IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);

        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLogRequestSummary(true);
        loggingInterceptor.setLogResponseSummary(true);
        client.registerInterceptor(loggingInterceptor);

        return client;
    }

    /**
     * Creates a plain FHIR client without interceptors, for services that manage their own configuration.
     */
    public IGenericClient createPlainClient(String fhirServerUrl) {
        String serverUrl = fhirServerUrl != null ? fhirServerUrl : defaultFhirServerUrl;
        return fhirContext.newRestfulGenericClient(serverUrl);
    }

    public String getDefaultFhirServerUrl() {
        return defaultFhirServerUrl;
    }
}
