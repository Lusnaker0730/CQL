package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates multiple measures sequentially and returns per-measure results.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchEvaluationService {

    private final MeasureEvaluationService evaluationService;
    private final MeasureDefinitionService definitionService;

    public record BatchEvaluationRequest(
            @NotEmpty List<Long> measureIds,
            @Size(max = 500) String fhirServerUrl,
            String periodStart,
            String periodEnd
    ) {}

    public record BatchResult(
            List<MeasureEvaluationResult> results,
            int totalMeasures,
            int successCount,
            int errorCount,
            long totalDurationMs
    ) {}

    public BatchResult evaluateBatch(BatchEvaluationRequest request) {
        long start = System.currentTimeMillis();
        List<MeasureEvaluationResult> results = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (Long measureId : request.measureIds()) {
            try {
                MeasureDefinition def = definitionService.getById(measureId)
                        .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));

                MeasureEvaluationRequest evalRequest = new MeasureEvaluationRequest();
                evalRequest.setMeasureId(measureId.toString());
                evalRequest.setMeasureCql(def.getCqlContent());
                evalRequest.setFhirServerUrl(request.fhirServerUrl());
                if (request.periodStart() != null) {
                    evalRequest.setPeriodStart(LocalDate.parse(request.periodStart()));
                }
                if (request.periodEnd() != null) {
                    evalRequest.setPeriodEnd(LocalDate.parse(request.periodEnd()));
                }

                MeasureEvaluationResult result = evaluationService.evaluateMeasure(evalRequest, measureId, def);
                result.setMeasureName(def.getTitle() != null ? def.getTitle() : def.getName());
                results.add(result);

                if ("error".equals(result.getStatus())) {
                    errorCount++;
                } else {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Batch evaluation failed for measure {}", measureId, e);
                MeasureEvaluationResult errorResult = MeasureEvaluationResult.builder()
                        .measureId(measureId.toString())
                        .status("error")
                        .errorMessage(e.getMessage())
                        .build();
                results.add(errorResult);
                errorCount++;
            }
        }

        long duration = System.currentTimeMillis() - start;
        return new BatchResult(results, request.measureIds().size(), successCount, errorCount, duration);
    }
}
