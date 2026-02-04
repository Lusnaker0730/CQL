package com.cqlplatform.controller;

import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.service.measure.MeasureEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/measures")
@RequiredArgsConstructor
@Tag(name = "Measures", description = "Quality Measure Evaluation APIs")
public class MeasureController {

    private final MeasureEvaluationService measureService;

    @PostMapping("/{measureId}/$evaluate-measure")
    @Operation(summary = "Evaluate Measure", description = "Evaluates a quality measure for a subject")
    public ResponseEntity<MeasureEvaluationResult> evaluateMeasure(
            @PathVariable String measureId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(required = false, defaultValue = "individual") String reportType,
            @RequestBody(required = false) MeasureEvaluationRequest request) {

        if (request == null) {
            request = new MeasureEvaluationRequest();
        }
        request.setMeasureId(measureId);

        if (subject != null) {
            request.setPatientId(subject);
        }
        if (periodStart != null) {
            request.setPeriodStart(periodStart);
        }
        if (periodEnd != null) {
            request.setPeriodEnd(periodEnd);
        }
        if (reportType != null) {
            request.setReportType(reportType);
        }

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate Custom Measure", description = "Evaluates a custom measure with provided CQL")
    public ResponseEntity<MeasureEvaluationResult> evaluateCustomMeasure(
            @RequestBody MeasureEvaluationRequest request) {
        MeasureEvaluationResult result = measureService.evaluateMeasure(request);
        return ResponseEntity.ok(result);
    }
}
