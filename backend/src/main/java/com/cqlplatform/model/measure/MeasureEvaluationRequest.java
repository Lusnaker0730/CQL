package com.cqlplatform.model.measure;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MeasureEvaluationRequest {
    private String measureId;

    /** CQL content — exempt from XSS sanitization. */
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String measureCql;

    private String patientId;

    @Pattern(regexp = "Patient|Group")
    private String subjectType = "Patient";

    private LocalDate periodStart;
    private LocalDate periodEnd;

    @Pattern(regexp = "individual|subject-list|summary|data-collection")
    private String reportType = "individual";

    @Size(max = 500)
    private String fhirServerUrl;
}
