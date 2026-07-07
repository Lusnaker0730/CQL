package com.cqlplatform.model.measure;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MeasureEvaluationRequest {
    @Size(max = 128)
    private String measureId;

    /** CQL content — exempt from XSS sanitization. */
    @Size(max = 512_000, message = "CQL content must be at most 512 KB")
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String measureCql;

    @Size(max = 128)
    private String patientId;

    @Pattern(regexp = "Patient|Group")
    private String subjectType = "Patient";

    private LocalDate periodStart;
    private LocalDate periodEnd;

    @Pattern(regexp = "individual|subject-list|summary|data-collection")
    private String reportType = "individual";

    @Size(max = 500)
    private String fhirServerUrl;

    /**
     * ID of a stored, authenticated {@code EhrConnection} to evaluate the measure against.
     * When set, the FHIR URL + credentials come from that connection (clinic runs eCQM on
     * its own secured FHIR server) and {@code fhirServerUrl} is ignored. See Phase 1.
     */
    private Long connectionId;
}
