package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VsacServiceTest {

    @Mock
    private FhirContext fhirContext;

    @Mock
    private IGenericClient genericClient;

    private VsacService vsacService;

    @BeforeEach
    void setUp() {
        vsacService = new VsacService(fhirContext, "http://localhost:9999/vsac", "test-key");
    }

    @Test
    void getValueSetByOid_serverError_shouldThrow() {
        when(fhirContext.newRestfulGenericClient(anyString())).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(RuntimeException.class, () -> vsacService.getValueSetByOid("2.16.840.1.113883.3.464.1003.101.12.1001"));
    }

    @Test
    void expandValueSetByOid_serverError_shouldThrow() {
        when(fhirContext.newRestfulGenericClient(anyString())).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(RuntimeException.class, () -> vsacService.expandValueSetByOid("2.16.840.1.113883.3.464.1003.101.12.1001"));
    }

    @Test
    void searchValueSets_serverError_shouldThrow() {
        when(fhirContext.newRestfulGenericClient(anyString())).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(RuntimeException.class, () -> vsacService.searchValueSets("diabetes"));
    }
}
