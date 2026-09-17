package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirBulkExportServiceTest {

    @Mock
    private FhirContext fhirContext;

    @Mock
    private IGenericClient genericClient;

    @Mock
    private IRestfulClientFactory clientFactory;

    @Mock
    private com.cqlplatform.service.fhir.FhirClientFactory fhirClientFactory;

    private FhirBulkExportService bulkExportService;

    @BeforeEach
    void setUp() {
        bulkExportService = new FhirBulkExportService(fhirContext, fhirClientFactory);
        org.springframework.test.util.ReflectionTestUtils.setField(
                bulkExportService, "defaultFhirServerUrl", "http://hapi-fhir:8080/fhir");
    }

    @Test
    void kickOffExport_serverError_shouldThrow() {
        // PAT-212: no connection → sandbox client via fhirClientFactory.createClient.
        when(fhirClientFactory.createClient(anyString())).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(RuntimeException.class, () ->
                bulkExportService.kickOffExport(null, "system", null, null, null));
    }

    @Test
    void pollExportStatus_serverError_shouldReturnInProgress() {
        when(fhirContext.newRestfulGenericClient(anyString())).thenThrow(new RuntimeException("Connection refused"));

        FhirBulkExportService.BulkExportStatusResult result =
                bulkExportService.pollExportStatus("http://localhost:9999/fhir/$export-poll");

        assertEquals("in-progress", result.status());
        assertNotNull(result.errorMessage());
    }
}
