package com.cqlplatform.model.fhir;

import com.cqlplatform.entity.PatientImportEntity;

/**
 * PAT-206 follow-up — result of importing an uploaded FHIR bundle. The import always happens;
 * TW Core conformance validation is opt-in ({@code validate=true}) because per-resource
 * validation against the IG is slow for large bundles. When not requested, {@code validated}
 * is false and the resource counts are null.
 */
public record FhirBundleImportResult(
        PatientImportEntity patientImport,
        boolean validated,
        Integer totalResources,
        Integer validResources,
        Integer invalidResources) {

    public static FhirBundleImportResult withoutValidation(PatientImportEntity patientImport) {
        return new FhirBundleImportResult(patientImport, false, null, null, null);
    }
}
