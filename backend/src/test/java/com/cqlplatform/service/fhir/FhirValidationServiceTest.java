package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FhirValidationServiceTest {

    private FhirValidationService validationService;

    @BeforeEach
    void setUp() {
        FhirContext fhirContext = FhirContext.forR4();
        validationService = new FhirValidationService(fhirContext);
    }

    @Test
    void validateResource_validPatient_shouldBeValid() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "id": "test-patient",
                    "name": [{"family": "Smith", "given": ["John"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;

        FhirValidationService.ValidationResult result = validationService.validateResource(patientJson);

        assertTrue(result.valid());
        assertTrue(result.issues().stream().noneMatch(i -> "error".equals(i.severity())));
    }

    @Test
    void validateResource_malformedResource_shouldHaveIssues() {
        String invalidJson = """
                {
                    "resourceType": "Patient",
                    "gender": "invalid-gender-value"
                }
                """;

        FhirValidationService.ValidationResult result = validationService.validateResource(invalidJson);

        assertFalse(result.issues().isEmpty());
    }

    @Test
    void validateResource_invalidJson_shouldReturnInvalid() {
        String badJson = "{ not valid json at all }}}";

        FhirValidationService.ValidationResult result = validationService.validateResource(badJson);

        assertFalse(result.valid());
        assertFalse(result.issues().isEmpty());
    }
}
