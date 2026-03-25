package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FhirImplementationGuideServiceTest {

    private FhirImplementationGuideService igService;
    private Map<String, StructureDefinition> profilesByUrl;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        FhirContext fhirContext = FhirContext.forR4();
        igService = new FhirImplementationGuideService(fhirContext);

        // Access the private profilesByUrl map via reflection to inject test data
        Field field = FhirImplementationGuideService.class.getDeclaredField("profilesByUrl");
        field.setAccessible(true);
        profilesByUrl = (Map<String, StructureDefinition>) field.get(igService);

        // Add test profiles
        StructureDefinition twPatient = new StructureDefinition();
        twPatient.setUrl("https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Patient-twcore");
        twPatient.setName("TWCorePatient");
        twPatient.setTitle("TW Core Patient");
        twPatient.setType("Patient");
        twPatient.setKind(StructureDefinition.StructureDefinitionKind.RESOURCE);
        twPatient.setStatus(Enumerations.PublicationStatus.ACTIVE);
        twPatient.setDerivation(StructureDefinition.TypeDerivationRule.CONSTRAINT);
        profilesByUrl.put(twPatient.getUrl(), twPatient);

        StructureDefinition twObservation = new StructureDefinition();
        twObservation.setUrl("https://twcore.mohw.gov.tw/ig/twcore/StructureDefinition/Observation-laboratoryResult-twcore");
        twObservation.setName("TWCoreObservationLab");
        twObservation.setTitle("TW Core Observation Laboratory Result");
        twObservation.setType("Observation");
        twObservation.setKind(StructureDefinition.StructureDefinitionKind.RESOURCE);
        twObservation.setStatus(Enumerations.PublicationStatus.ACTIVE);
        twObservation.setDerivation(StructureDefinition.TypeDerivationRule.CONSTRAINT);
        profilesByUrl.put(twObservation.getUrl(), twObservation);

        // Add a base definition (not a constraint) — should not appear in recommended
        StructureDefinition basePatient = new StructureDefinition();
        basePatient.setUrl("http://hl7.org/fhir/StructureDefinition/Patient");
        basePatient.setName("Patient");
        basePatient.setTitle("Patient");
        basePatient.setType("Patient");
        basePatient.setKind(StructureDefinition.StructureDefinitionKind.RESOURCE);
        basePatient.setStatus(Enumerations.PublicationStatus.ACTIVE);
        basePatient.setDerivation(StructureDefinition.TypeDerivationRule.SPECIALIZATION);
        profilesByUrl.put(basePatient.getUrl(), basePatient);
    }

    @Test
    void getRecommendedProfiles_knownType_shouldReturnProfiles() {
        List<FhirImplementationGuideService.ProfileSummary> profiles =
                igService.getRecommendedProfiles("Patient");

        assertFalse(profiles.isEmpty());
        assertEquals(1, profiles.size());
        assertEquals("TWCorePatient", profiles.get(0).name());
        assertEquals("Patient", profiles.get(0).type());
    }

    @Test
    void getRecommendedProfiles_unknownType_shouldReturnEmpty() {
        List<FhirImplementationGuideService.ProfileSummary> profiles =
                igService.getRecommendedProfiles("MedicationRequest");

        assertTrue(profiles.isEmpty());
    }

    @Test
    void getRecommendedProfiles_nullType_shouldReturnEmpty() {
        List<FhirImplementationGuideService.ProfileSummary> profiles =
                igService.getRecommendedProfiles(null);

        assertTrue(profiles.isEmpty());
    }

    @Test
    void getRecommendedProfiles_shouldExcludeBaseDefinitions() {
        List<FhirImplementationGuideService.ProfileSummary> profiles =
                igService.getRecommendedProfiles("Patient");

        // Should only return the TW Core constraint, not the base Patient definition
        assertTrue(profiles.stream().allMatch(p -> p.url().contains("twcore")));
    }

    @Test
    void findProfileUrlForResourceType_knownType_shouldReturnUrl() {
        String url = igService.findProfileUrlForResourceType("Patient");

        assertNotNull(url);
        assertTrue(url.contains("twcore"));
    }

    @Test
    void findProfileUrlForResourceType_unknownType_shouldReturnNull() {
        String url = igService.findProfileUrlForResourceType("AllergyIntolerance");

        assertNull(url);
    }

    @Test
    void findProfileUrlForResourceType_nullType_shouldReturnNull() {
        String url = igService.findProfileUrlForResourceType(null);

        assertNull(url);
    }
}
