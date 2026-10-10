package com.cqlplatform.service.measure;

import com.cqlplatform.entity.TestCaseEntity;

import java.util.LinkedHashMap;

/**
 * PAT-246 — how a test case is duplicated onto another measure (a new version, or any other
 * measure via "copy to"): the authored content travels (title, description, series, order,
 * patient bundle, expectations, FHIR validation outcome — the bundle is byte-identical so the
 * verdict still holds); the run state does not (it belongs to the source measure's runs).
 */
final class TestCaseCopies {

    private TestCaseCopies() {
    }

    static TestCaseEntity copyOf(TestCaseEntity source, Long targetMeasureId) {
        return TestCaseEntity.builder()
                .measureDefinitionId(targetMeasureId)
                .title(source.getTitle())
                .description(source.getDescription())
                .series(source.getSeries())
                .sortOrder(source.getSortOrder())
                .patientBundleJson(source.getPatientBundleJson())
                .expectedPopulationMap(source.getExpectedPopulationMap() != null
                        ? new LinkedHashMap<>(source.getExpectedPopulationMap()) : new LinkedHashMap<>())
                .expectedValues(source.getExpectedValues())
                .validationStatus(source.getValidationStatus())
                .validationSummary(source.getValidationSummary())
                .validatedAt(source.getValidatedAt())
                .status("pending")
                .build();
    }
}
