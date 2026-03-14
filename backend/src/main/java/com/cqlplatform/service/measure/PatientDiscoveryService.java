package com.cqlplatform.service.measure;

import com.cqlplatform.service.fhir.FhirDataProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles patient discovery for measure evaluation.
 * Determines which patients to evaluate based on the request context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientDiscoveryService {

    private final FhirDataProviderService fhirDataProviderService;

    /**
     * Discovers patients to evaluate. If a specific patient ID is given, returns just that.
     * Otherwise queries the FHIR server for all patient IDs.
     *
     * @param context the evaluation context
     * @return list of patient IDs to evaluate
     */
    public List<String> discoverPatients(MeasureEvaluationContext context) {
        String patientId = context.getPatientId();
        if (patientId != null && !patientId.isBlank()) {
            log.info("Evaluating single patient: {}", patientId);
            return List.of(patientId);
        }

        log.info("Discovering all patients from FHIR server: {}", context.getFhirServerUrl());
        List<String> patients = fhirDataProviderService.getAllPatientIds(context.getFhirServerUrl());
        log.info("Discovered {} patients", patients.size());
        return patients;
    }

    /**
     * Builds an appropriate error message when no patients are found.
     */
    public String buildNoPatientsMessage(MeasureEvaluationContext context) {
        if (context.getPatientId() != null && !context.getPatientId().isBlank()) {
            return "Patient not found: " + context.getPatientId();
        }
        return "No patients found on FHIR server. Verify the server URL and that patient data exists.";
    }
}
