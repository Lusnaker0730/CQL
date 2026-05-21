package com.cqlplatform.service.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.fhir.FhirRequestContext;
import com.cqlplatform.model.fhir.PatientImportPreview;
import com.cqlplatform.model.fhir.PatientSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientSearchService {

    private final EhrConnectionService connectionService;
    private final FhirClientFactory fhirClientFactory;

    private static final List<String> PREVIEW_RESOURCE_TYPES = List.of(
            "Condition", "Observation", "MedicationRequest", "Encounter", "Procedure"
    );

    /**
     * Search for patients on a remote EHR by identifier or name.
     */
    public List<PatientSearchResult> searchPatients(Long connectionId, String nationalId,
                                                     String mrn, String family, String given) {
        EhrConnectionEntity connection = connectionService.getById(connectionId);
        // PAT-110: expose connection identity to any FhirServerUnavailableException
        // thrown downstream so the FE banner can name the failing EHR.
        FhirRequestContext.set(connection.getId(), connection.getName());
        try {
            IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);

            var searchQuery = client.search().forResource(Patient.class);

            boolean hasCriteria = false;
            if (nationalId != null && !nationalId.isBlank()) {
                searchQuery = searchQuery.where(Patient.IDENTIFIER.exactly().identifier(nationalId));
                hasCriteria = true;
            }
            if (mrn != null && !mrn.isBlank()) {
                searchQuery = searchQuery.where(Patient.IDENTIFIER.exactly().identifier(mrn));
                hasCriteria = true;
            }
            if (family != null && !family.isBlank()) {
                searchQuery = searchQuery.where(Patient.FAMILY.matches().value(family));
                hasCriteria = true;
            }
            if (given != null && !given.isBlank()) {
                searchQuery = searchQuery.where(Patient.GIVEN.matches().value(given));
                hasCriteria = true;
            }

            if (!hasCriteria) {
                log.warn("Patient search on connection {} with no criteria, returning empty", connectionId);
                return Collections.emptyList();
            }

            Bundle bundle = searchQuery.returnBundle(Bundle.class).execute();

            List<PatientSearchResult> results = new ArrayList<>();
            if (bundle.hasEntry()) {
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (entry.getResource() instanceof Patient patient) {
                        results.add(patientToSearchResult(patient));
                    }
                }
            }

            log.info("Patient search on connection {} returned {} results", connectionId, results.size());
            return results;
        } finally {
            FhirRequestContext.clear();
        }
    }

    /**
     * Get a preview of what data is available for a patient before importing.
     */
    public PatientImportPreview getPatientPreview(Long connectionId, String patientId) {
        EhrConnectionEntity connection = connectionService.getById(connectionId);
        IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);

        // Fetch the patient resource for the name
        Patient patient;
        try {
            patient = client.read().resource(Patient.class).withId(patientId).execute();
        } catch (Exception e) {
            throw new IllegalArgumentException("Patient not found: " + patientId);
        }

        String patientName = extractPatientName(patient);

        // Count resources by type
        Map<String, Integer> resourceCounts = new LinkedHashMap<>();
        int totalResources = 0;

        // Always count the Patient itself
        resourceCounts.put("Patient", 1);
        totalResources++;

        for (String resourceType : PREVIEW_RESOURCE_TYPES) {
            try {
                Bundle searchBundle = client.search()
                        .byUrl(resourceType + "?patient=" + patientId + "&_summary=count")
                        .returnBundle(Bundle.class)
                        .execute();
                int count = searchBundle.getTotal();
                if (count > 0) {
                    resourceCounts.put(resourceType, count);
                    totalResources += count;
                }
            } catch (Exception e) {
                log.debug("Could not count {} for patient {}: {}", resourceType, patientId, e.getMessage());
            }
        }

        return PatientImportPreview.builder()
                .patientId(patientId)
                .patientName(patientName)
                .resourceCounts(resourceCounts)
                .totalResources(totalResources)
                .build();
    }

    private PatientSearchResult patientToSearchResult(Patient patient) {
        return PatientSearchResult.builder()
                .id(patient.getIdElement().getIdPart())
                .name(extractPatientName(patient))
                .birthDate(patient.hasBirthDate() ? patient.getBirthDateElement().getValueAsString() : null)
                .gender(patient.hasGender() ? patient.getGender().toCode() : null)
                .identifier(extractPrimaryIdentifier(patient))
                .build();
    }

    private String extractPatientName(Patient patient) {
        if (patient.hasName()) {
            HumanName name = patient.getNameFirstRep();
            StringBuilder sb = new StringBuilder();
            if (name.hasFamily()) {
                sb.append(name.getFamily());
            }
            if (name.hasGiven()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(name.getGiven().stream()
                        .map(StringType::getValue)
                        .collect(Collectors.joining(" ")));
            }
            if (sb.length() > 0) return sb.toString();
        }
        return "Unknown";
    }

    private String extractPrimaryIdentifier(Patient patient) {
        if (patient.hasIdentifier()) {
            // Prefer an official identifier
            for (Identifier id : patient.getIdentifier()) {
                if (id.hasUse() && id.getUse() == Identifier.IdentifierUse.OFFICIAL && id.hasValue()) {
                    return id.getValue();
                }
            }
            // Fall back to the first identifier with a value
            for (Identifier id : patient.getIdentifier()) {
                if (id.hasValue()) {
                    return id.getValue();
                }
            }
        }
        return null;
    }
}
