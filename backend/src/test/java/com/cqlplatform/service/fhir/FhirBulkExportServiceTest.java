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

    private FhirBulkExportService bulkExportService;

    @BeforeEach
    void setUp() {
        bulkExportService = new FhirBulkExportService(fhirContext);
    }

    @Test
    void kickOffExport_serverError_shouldThrow() {
        when(fhirContext.newRestfulGenericClient(anyString())).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(RuntimeException.class, () ->
                bulkExportService.kickOffExport("http://localhost:9999/fhir", "system", null, null, null));
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
