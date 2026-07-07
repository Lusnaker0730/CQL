package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.entity.EhrConnectionEntity;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;

import static org.mockito.Mockito.*;

/**
 * Phase 1 — createDataProvider must build the FHIR client from a stored EhrConnection's
 * credentials when a connection is supplied, and from an unauthenticated client otherwise.
 */
class FhirDataProviderServiceAuthTest {

    private static final FhirContext R4 = FhirContext.forR4();

    private FhirDataProviderService newService(FhirClientFactory factory) {
        return new FhirDataProviderService(R4, factory, CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void usesAuthenticatedClientWhenConnectionPresent() {
        FhirClientFactory factory = mock(FhirClientFactory.class);
        when(factory.createAuthenticatedClient(any())).thenReturn(mock(IGenericClient.class));
        FhirDataProviderService svc = newService(factory);

        EhrConnectionEntity conn = new EhrConnectionEntity();
        conn.setId(3L);
        conn.setFhirServerUrl("https://clinic.example/fhir");

        svc.createDataProvider("https://clinic.example/fhir", mock(TerminologyProvider.class), conn);

        verify(factory).createAuthenticatedClient(conn);
        verify(factory, never()).createClient(anyString());
    }

    @Test
    void usesUnauthenticatedClientWhenNoConnection() {
        FhirClientFactory factory = mock(FhirClientFactory.class);
        when(factory.createClient(anyString())).thenReturn(mock(IGenericClient.class));
        FhirDataProviderService svc = newService(factory);

        svc.createDataProvider("https://public.example/fhir", mock(TerminologyProvider.class), null);

        verify(factory).createClient("https://public.example/fhir");
        verify(factory, never()).createAuthenticatedClient(any());
    }

    @Test
    void twoArgOverloadIsUnauthenticated() {
        FhirClientFactory factory = mock(FhirClientFactory.class);
        when(factory.createClient(anyString())).thenReturn(mock(IGenericClient.class));
        FhirDataProviderService svc = newService(factory);

        svc.createDataProvider("https://public.example/fhir", mock(TerminologyProvider.class));

        verify(factory).createClient("https://public.example/fhir");
        verify(factory, never()).createAuthenticatedClient(any());
    }
}
