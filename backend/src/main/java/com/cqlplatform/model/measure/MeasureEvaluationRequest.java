package com.cqlplatform.model.measure;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MeasureEvaluationRequest {
    private String measureId;
    private String measureCql;
    private String patientId;
    private String subjectType = "Patient";
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String reportType = "individual"; // individual, subject-list, summary, data-collection
    private String fhirServerUrl;
}
