package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.repository.PatientImportRepository;
import com.cqlplatform.service.measure.TestCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientImportService {

    private final EhrConnectionService connectionService;
    private final FhirClientFactory fhirClientFactory;
    private final FhirContext fhirContext;
    private final PatientImportRepository importRepository;
    private final TestCaseService testCaseService;

    /**
     * Import a patient's data from an EHR connection as a test case bundle.
     */
    @Transactional
    public PatientImportEntity importAsTestCase(Long connectionId, String patientId, Long measureId) {
        EhrConnectionEntity connection = connectionService.getById(connectionId);
        IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);

        // Fetch the patient's complete data using $everything
        Bundle patientBundle;
        try {
            patientBundle = client.operation()
                    .onInstance(new IdType("Patient", patientId))
                    .named("$everything")
                    .withNoParameters(Parameters.class)
                    .returnResourceType(Bundle.class)
                    .execute();
        } catch (Exception e) {
            log.warn("$everything operation failed for patient {}, falling back to individual searches", patientId);
            patientBundle = fetchPatientDataManually(client, patientId);
        }

        // Extract patient info
        String patientName = "Unknown";
        String patientIdentifier = null;
        for (Bundle.BundleEntryComponent entry : patientBundle.getEntry()) {
            if (entry.getResource() instanceof Patient patient) {
                patientName = extractPatientName(patient);
                patientIdentifier = extractPrimaryIdentifier(patient);
                break;
            }
        }

        // Serialize bundle to JSON
        String bundleJson = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(patientBundle);
        int resourceCount = patientBundle.getEntry().size();

        // Get current user
        String importedBy = getCurrentUsername();

        // Create import record
        PatientImportEntity importEntity = new PatientImportEntity();
        importEntity.setConnectionId(connectionId);
        importEntity.setPatientFhirId(patientId);
        importEntity.setPatientIdentifier(patientIdentifier);
        importEntity.setPatientName(patientName);
        importEntity.setResourceCount(resourceCount);
        importEntity.setBundleJson(bundleJson);
        importEntity.setImportedBy(importedBy);

        // If a measure is specified, create a test case
        if (measureId != null) {
            importEntity.setTargetMeasureId(measureId);

            TestCase testCase = TestCase.builder()
                    .title("Imported: " + patientName + " (" + patientId + ")")
                    .description("Imported from EHR connection '" + connection.getName() + "' on " + java.time.LocalDate.now())
                    .patientBundleJson(bundleJson)
                    .status("pending")
                    .build();

            TestCase created = testCaseService.create(measureId, testCase);
            importEntity.setTargetTestCaseId(created.getId());
            log.info("Created test case {} from patient import {}", created.getId(), patientId);
        }

        importEntity = importRepository.save(importEntity);
        log.info("Imported patient {} from connection '{}' ({} resources)",
                patientId, connection.getName(), resourceCount);
        return importEntity;
    }

    @Transactional(readOnly = true)
    public List<PatientImportEntity> listImports(String importedBy) {
        if (importedBy != null && !importedBy.isBlank()) {
            return importRepository.findByImportedByOrderByCreatedAtDesc(importedBy);
        }
        return importRepository.findAll();
    }

    /**
     * Fallback method when $everything is not supported: manually fetch common resource types.
     */
    private Bundle fetchPatientDataManually(IGenericClient client, String patientId) {
        Bundle combinedBundle = new Bundle();
        combinedBundle.setType(Bundle.BundleType.COLLECTION);

        // Fetch the Patient resource
        try {
            Patient patient = client.read().resource(Patient.class).withId(patientId).execute();
            combinedBundle.addEntry().setResource(patient);
        } catch (Exception e) {
            throw new IllegalArgumentException("Patient not found: " + patientId);
        }

        // Fetch related resources
        String[] resourceTypes = {"Condition", "Observation", "MedicationRequest", "Encounter", "Procedure"};
        for (String resourceType : resourceTypes) {
            try {
                Bundle searchResult = client.search()
                        .byUrl(resourceType + "?patient=" + patientId)
                        .returnBundle(Bundle.class)
                        .execute();
                if (searchResult.hasEntry()) {
                    for (Bundle.BundleEntryComponent entry : searchResult.getEntry()) {
                        combinedBundle.addEntry().setResource(entry.getResource());
                    }
                }
            } catch (Exception e) {
                log.debug("Could not fetch {} for patient {}: {}", resourceType, patientId, e.getMessage());
            }
        }

        return combinedBundle;
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
                        .collect(java.util.stream.Collectors.joining(" ")));
            }
            if (sb.length() > 0) return sb.toString();
        }
        return "Unknown";
    }

    private String extractPrimaryIdentifier(Patient patient) {
        if (patient.hasIdentifier()) {
            for (Identifier id : patient.getIdentifier()) {
                if (id.hasUse() && id.getUse() == Identifier.IdentifierUse.OFFICIAL && id.hasValue()) {
                    return id.getValue();
                }
            }
            for (Identifier id : patient.getIdentifier()) {
                if (id.hasValue()) {
                    return id.getValue();
                }
            }
        }
        return null;
    }

    private String getCurrentUsername() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not determine current user: {}", e.getMessage());
        }
        return "system";
    }
}
