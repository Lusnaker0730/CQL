package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.entity.EhrConnectionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirClientFactoryTest {

    @Mock
    private SmartBackendTokenService smartBackendTokenService;

    @Mock
    private TlsContextFactory tlsContextFactory;

    private FhirClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new FhirClientFactory(FhirContext.forR4(), smartBackendTokenService, tlsContextFactory);
    }

    @Test
    void createClient_returnsClient() {
        IGenericClient client = factory.createClient("http://localhost:8080/fhir");
        assertNotNull(client);
    }

    @Test
    void createClient_cachesClient() {
        IGenericClient client1 = factory.createClient("http://localhost:8080/fhir");
        IGenericClient client2 = factory.createClient("http://localhost:8080/fhir");
        assertSame(client1, client2);
    }

    @Test
    void createPlainClient_returnsClient() {
        IGenericClient client = factory.createPlainClient("http://localhost:8080/fhir");
        assertNotNull(client);
    }

    @Test
    void createAuthenticatedClient_noAuth_returnsClient() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setFhirServerUrl("http://localhost:8080/fhir");
        connection.setAuthType("none");

        IGenericClient client = factory.createAuthenticatedClient(connection);
        assertNotNull(client);
    }

    @Test
    void createAuthenticatedClient_basicAuth_returnsClient() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setFhirServerUrl("http://localhost:8080/fhir");
        connection.setAuthType("basic");
        connection.setCredentials("{\"username\":\"user\",\"password\":\"pass\"}");

        IGenericClient client = factory.createAuthenticatedClient(connection);
        assertNotNull(client);
    }

    @Test
    void createAuthenticatedClient_bearerAuth_returnsClient() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setFhirServerUrl("http://localhost:8080/fhir");
        connection.setAuthType("bearer");
        connection.setCredentials("{\"token\":\"my-token\"}");

        IGenericClient client = factory.createAuthenticatedClient(connection);
        assertNotNull(client);
    }

    @Test
    void createAuthenticatedClient_smartBackend_usesTokenService() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setFhirServerUrl("http://localhost:8080/fhir");
        connection.setAuthType("smart_backend");
        when(smartBackendTokenService.getAccessToken(connection)).thenReturn("smart-token");

        IGenericClient client = factory.createAuthenticatedClient(connection);
        assertNotNull(client);
        verify(smartBackendTokenService).getAccessToken(connection);
    }

    @Test
    void createAuthenticatedClient_tlsEnabled_callsTlsContextFactory() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setFhirServerUrl("http://localhost:8080/fhir");
        connection.setAuthType("none");
        connection.setTlsEnabled(true);
        connection.setCaCertPem("some-cert");
        when(tlsContextFactory.createSslContext(connection)).thenReturn(null);
        when(tlsContextFactory.createHostnameVerifier(connection)).thenReturn(null);

        IGenericClient client = factory.createAuthenticatedClient(connection);
        assertNotNull(client);
        verify(tlsContextFactory).createSslContext(connection);
    }

    @Test
    void createAuthenticatedClient_tlsDisabled_doesNotCallTlsContextFactory() {
        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setFhirServerUrl("http://localhost:8080/fhir");
        connection.setAuthType("none");
        connection.setTlsEnabled(false);

        IGenericClient client = factory.createAuthenticatedClient(connection);
        assertNotNull(client);
        verifyNoInteractions(tlsContextFactory);
    }
}
