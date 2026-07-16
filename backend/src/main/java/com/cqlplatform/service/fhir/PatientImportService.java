package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.repository.PatientImportRepository;
import com.cqlplatform.service.measure.TestCaseService;
import com.cqlplatform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
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
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    /** Max entries in an uploaded single-patient bundle (PAT-206 follow-up). */
    private static final int MAX_BUNDLE_ENTRIES = 10_000;

    /**
     * The caller's tenant, falling back to the default tenant for legacy callers without
     * a tenant claim (same semantics as EhrConnectionService / CqlLibraryService).
     */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

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

        return persistBundleImport(patientBundle, patientId, connectionId, "ehr",
                "EHR connection '" + connection.getName() + "'", measureId);
    }

    /**
     * PAT-206 — import a FHIR bundle uploaded as a file (e.g. a 健康存摺 / My Health Bank
     * export) rather than fetched from a live EHR connection. Same landing as the connection
     * path — a tenant-scoped patient_import row (source='fhir-upload', connection_id null),
     * plus an optional test case when a measure is given.
     *
     * @param bundleJson the raw FHIR JSON of a Bundle
     * @param measureId  optional measure to attach the import to as a test case
     */
    @Transactional
    public PatientImportEntity importUploadedBundle(String bundleJson, Long measureId) {
        Bundle bundle;
        try {
            bundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
        } catch (Exception e) {
            throw new com.cqlplatform.exception.ValidationException(
                    "Uploaded file is not a valid FHIR Bundle: " + e.getMessage());
        }
        if (!bundle.hasEntry()) {
            throw new com.cqlplatform.exception.ValidationException(
                    "Uploaded FHIR Bundle contains no entries.");
        }
        // PAT-206 follow-up: bound the work a single upload can trigger. A genuine
        // single-patient 健康存摺 export is hundreds of resources; anything far larger is
        // either a batch export (not what this endpoint is for) or pathological input.
        if (bundle.getEntry().size() > MAX_BUNDLE_ENTRIES) {
            throw new com.cqlplatform.exception.ValidationException(
                    "Uploaded FHIR Bundle has too many entries (" + bundle.getEntry().size()
                            + "); the limit is " + MAX_BUNDLE_ENTRIES + ".");
        }

        // Identify the patient the bundle is about. Uploaded bundles (health-bank exports)
        // carry a Patient resource; fall back to a placeholder id when absent so the row still
        // records the import.
        String patientFhirId = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof Patient)
                .map(r -> r.getIdElement().getIdPart())
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse("uploaded-" + System.identityHashCode(bundle));

        return persistBundleImport(bundle, patientFhirId, null, "fhir-upload",
                "Uploaded FHIR bundle", measureId);
    }

    /**
     * Shared landing for both ingress paths (EHR $everything fetch and uploaded bundle):
     * extract patient info, serialise, and persist a tenant-scoped patient_import row plus an
     * optional test case.
     */
    private PatientImportEntity persistBundleImport(Bundle patientBundle, String patientFhirId,
                                                    Long connectionId, String source,
                                                    String sourceDescription, Long measureId) {
        String patientName = "Unknown";
        String patientIdentifier = null;
        for (Bundle.BundleEntryComponent entry : patientBundle.getEntry()) {
            if (entry.getResource() instanceof Patient patient) {
                patientName = extractPatientName(patient);
                patientIdentifier = extractPrimaryIdentifier(patient);
                break;
            }
        }

        String bundleJson = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(patientBundle);
        int resourceCount = patientBundle.getEntry().size();
        String importedBy = SecurityUtils.getCurrentUsername("system");

        PatientImportEntity importEntity = new PatientImportEntity();
        importEntity.setConnectionId(connectionId);
        importEntity.setSource(source);
        importEntity.setPatientFhirId(patientFhirId);
        importEntity.setPatientIdentifier(patientIdentifier);
        importEntity.setPatientName(patientName);
        importEntity.setResourceCount(resourceCount);
        importEntity.setBundleJson(bundleJson);
        importEntity.setImportedBy(importedBy);
        // Batch/scheduled callers run under TenantContext.callWith(job/failed-row tenant),
        // so this resolves to the right clinic on async threads too.
        importEntity.setTenantId(effectiveTenantId());

        if (measureId != null) {
            importEntity.setTargetMeasureId(measureId);

            TestCase testCase = TestCase.builder()
                    .title("Imported: " + patientName + " (" + patientFhirId + ")")
                    .description("Imported from " + sourceDescription + " on " + java.time.LocalDate.now())
                    .patientBundleJson(bundleJson)
                    .status("pending")
                    .build();

            TestCase created = testCaseService.create(measureId, testCase);
            importEntity.setTargetTestCaseId(created.getId());
            log.info("Created test case {} from patient import {}", created.getId(), patientFhirId);
        }

        importEntity = importRepository.save(importEntity);
        log.info("Imported patient {} from {} ({} resources)", patientFhirId, sourceDescription, resourceCount);
        return importEntity;
    }

    @Transactional(readOnly = true)
    public List<PatientImportEntity> listImports(String importedBy) {
        if (importedBy != null && !importedBy.isBlank()) {
            return importRepository.findByTenantIdAndImportedByOrderByCreatedAtDesc(effectiveTenantId(), importedBy);
        }
        return importRepository.findByTenantIdOrderByCreatedAtDesc(effectiveTenantId());
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

}
