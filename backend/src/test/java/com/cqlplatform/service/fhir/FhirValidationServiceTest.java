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
        validationService = new FhirValidationService(fhirContext, null);
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

    @Test
    void validateResource_withProfile_shouldValidateAgainstProfile() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "id": "test-patient",
                    "name": [{"family": "Smith", "given": ["John"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;

        // Validate against base Patient profile — should succeed
        FhirValidationService.ValidationResult result =
                validationService.validateResource(patientJson, "http://hl7.org/fhir/StructureDefinition/Patient");

        assertTrue(result.valid());
    }

    @Test
    void validateResource_withoutProfile_shouldUseBaseValidation() {
        String observationJson = """
                {
                    "resourceType": "Observation",
                    "id": "test-obs",
                    "status": "final",
                    "code": {
                        "coding": [{"system": "http://loinc.org", "code": "85354-9"}]
                    }
                }
                """;

        FhirValidationService.ValidationResult result = validationService.validateResource(observationJson);

        // Should validate without fatal errors (base validation, no profile)
        assertTrue(result.issues().stream().noneMatch(i -> "fatal".equals(i.severity())));
    }

    @Test
    void validateBundle_withValidBundle_shouldReturnResults() {
        String bundleJson = """
                {
                    "resourceType": "Bundle",
                    "type": "collection",
                    "entry": [
                        {
                            "resource": {
                                "resourceType": "Patient",
                                "id": "patient-1",
                                "name": [{"family": "Smith", "given": ["John"]}],
                                "gender": "male",
                                "birthDate": "1990-01-01"
                            }
                        },
                        {
                            "resource": {
                                "resourceType": "Observation",
                                "id": "obs-1",
                                "status": "final",
                                "code": {
                                    "coding": [{"system": "http://loinc.org", "code": "85354-9"}]
                                }
                            }
                        }
                    ]
                }
                """;

        FhirValidationService.BundleValidationResult result = validationService.validateBundle(bundleJson);

        assertEquals(2, result.totalResources());
        assertEquals(2, result.entries().size());
        assertEquals("Patient", result.entries().get(0).resourceType());
        assertEquals("patient-1", result.entries().get(0).resourceId());
        assertEquals("Observation", result.entries().get(1).resourceType());
    }

    @Test
    void validateBundle_emptyBundle_shouldReturnEmpty() {
        String emptyBundleJson = """
                {
                    "resourceType": "Bundle",
                    "type": "collection",
                    "entry": []
                }
                """;

        FhirValidationService.BundleValidationResult result = validationService.validateBundle(emptyBundleJson);

        assertEquals(0, result.totalResources());
        assertEquals(0, result.validResources());
        assertEquals(0, result.invalidResources());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void validateBundle_withResource_shouldPopulateEntries() {
        // Bundle with a minimal Patient — validates each entry and populates results
        String bundleJson = """
                {
                    "resourceType": "Bundle",
                    "type": "collection",
                    "entry": [
                        {
                            "resource": {
                                "resourceType": "Patient",
                                "id": "patient-1",
                                "gender": "male"
                            }
                        }
                    ]
                }
                """;

        FhirValidationService.BundleValidationResult result = validationService.validateBundle(bundleJson);

        assertEquals(1, result.totalResources());
        assertFalse(result.entries().isEmpty());
        FhirValidationService.ResourceValidationEntry entry = result.entries().get(0);
        assertEquals("Patient", entry.resourceType());
        assertEquals("patient-1", entry.resourceId());
        assertNotNull(entry.issues());
        assertEquals(1, result.validResources() + result.invalidResources());
    }

    @Test
    void validateBundle_invalidJson_shouldReturnEmptyResult() {
        String invalidBundleJson = "{ not valid json }";

        FhirValidationService.BundleValidationResult result = validationService.validateBundle(invalidBundleJson);

        assertEquals(0, result.totalResources());
        assertTrue(result.entries().isEmpty());
    }
}
