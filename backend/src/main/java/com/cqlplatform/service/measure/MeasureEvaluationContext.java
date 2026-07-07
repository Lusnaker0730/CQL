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
    /** Pre-compiled ELM JSON — translated once, reused for all patients. */
    private final String preCompiledElmJson;

    public String getMeasureId() {
        return request.getMeasureId();
    }

    public String getMeasureCql() {
        return request.getMeasureCql();
    }

    public String getFhirServerUrl() {
        return request.getFhirServerUrl();
    }

    public Long getConnectionId() {
        return request.getConnectionId();
    }

    public String getPatientId() {
        return request.getPatientId();
    }

    public String getReportType() {
        return request.getReportType();
    }

    public String getElmJson() {
        // Prefer pre-compiled ELM (translated once before patient loop)
        if (preCompiledElmJson != null) {
            return preCompiledElmJson;
        }
        return measureDefinition != null ? measureDefinition.getElmJson() : null;
    }
}
