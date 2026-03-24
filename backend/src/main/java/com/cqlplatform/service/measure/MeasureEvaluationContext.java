package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Immutable context object carrying all parameters needed for a measure evaluation.
 * Replaces scattered parameter passing across the evaluation pipeline.
 */
@Getter
@Builder
public class MeasureEvaluationContext {
    private final MeasureEvaluationRequest request;
    private final MeasureDefinition measureDefinition;
    private final Long measureDefinitionId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int timeoutSeconds;

    public String getMeasureId() {
        return request.getMeasureId();
    }

    public String getMeasureCql() {
        return request.getMeasureCql();
    }

    public String getFhirServerUrl() {
        return request.getFhirServerUrl();
    }

    public String getPatientId() {
        return request.getPatientId();
    }

    public String getReportType() {
        return request.getReportType();
    }

    public String getElmJson() {
        return measureDefinition != null ? measureDefinition.getElmJson() : null;
    }
}
