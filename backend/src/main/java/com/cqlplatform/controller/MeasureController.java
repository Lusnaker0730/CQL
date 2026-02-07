package com.cqlplatform.controller;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.entity.MeasureScheduleEntity;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.service.measure.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/measures")
@RequiredArgsConstructor
@Tag(name = "Measures", description = "Quality Measure Evaluation APIs")
public class MeasureController {

    private final MeasureEvaluationService measureService;
    private final MeasureDefinitionService definitionService;
    private final MeasureReportService reportService;
    private final FhirMeasureService fhirMeasureService;
    private final CompositeMeasureService compositeMeasureService;
    private final MeasureReportExportService exportService;
    private final ScheduledMeasureEvaluationService scheduleService;
    private final MeasureComparisonService comparisonService;

    // ===== Measure Definition CRUD =====

    @GetMapping
    @Operation(summary = "List Measures", description = "List all measure definitions, optionally filtered by search term")
    public ResponseEntity<List<MeasureDefinition>> listMeasures(
            @RequestParam(required = false) String search) {
        List<MeasureDefinition> measures = (search != null && !search.isBlank())
                ? definitionService.search(search)
                : definitionService.getAll();
        return ResponseEntity.ok(measures);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Measure", description = "Get a measure definition by ID")
    public ResponseEntity<MeasureDefinition> getMeasure(@PathVariable Long id) {
        return definitionService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create Measure", description = "Create a new measure definition")
    public ResponseEntity<MeasureDefinition> createMeasure(@RequestBody MeasureDefinition definition) {
        MeasureDefinition created = definitionService.create(definition);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Measure", description = "Update an existing measure definition")
    public ResponseEntity<MeasureDefinition> updateMeasure(
            @PathVariable Long id,
            @RequestBody MeasureDefinition definition) {
        MeasureDefinition updated = definitionService.update(id, definition);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Measure", description = "Delete a measure definition (ADMIN only)")
    public ResponseEntity<Void> deleteMeasure(@PathVariable Long id) {
        definitionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ===== FHIR Import/Export =====

    @PostMapping("/import/fhir")
    @Operation(summary = "Import FHIR Measure", description = "Import a FHIR Measure resource as a measure definition")
    public ResponseEntity<MeasureDefinition> importFhirMeasure(@RequestBody JsonNode fhirMeasure) {
        MeasureDefinition imported = fhirMeasureService.importFhirMeasure(fhirMeasure);
        return ResponseEntity.ok(imported);
    }

    @GetMapping("/{id}/fhir")
    @Operation(summary = "Export as FHIR Measure", description = "Export a measure definition as a FHIR Measure resource")
    public ResponseEntity<ObjectNode> exportFhirMeasure(@PathVariable Long id) {
        ObjectNode fhirMeasure = fhirMeasureService.exportAsFhirMeasure(id);
        return ResponseEntity.ok(fhirMeasure);
    }

    // ===== Evaluation =====

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

        // Check if measureId is a numeric ID referencing a stored measure
        try {
            Long defId = Long.parseLong(measureId);
            MeasureDefinition def = definitionService.getById(defId).orElse(null);
            if (def != null) {
                // Check for composite measure
                if ("composite".equalsIgnoreCase(def.getScoringType())) {
                    MeasureEvaluationResult result = compositeMeasureService.evaluateComposite(def, request);
                    return ResponseEntity.ok(result);
                }
                // Use stored CQL if no CQL provided in request
                if (request.getMeasureCql() == null || request.getMeasureCql().isBlank()) {
                    request.setMeasureCql(def.getCqlContent());
                }
            }
        } catch (NumberFormatException ignored) {
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

    // ===== Reports =====

    @GetMapping("/reports")
    @Operation(summary = "List Reports", description = "List recent measure reports")
    public ResponseEntity<List<MeasureReportEntity>> listReports() {
        return ResponseEntity.ok(reportService.getRecentReports());
    }

    @GetMapping("/{measureId}/reports")
    @Operation(summary = "Reports for Measure", description = "List reports for a specific measure")
    public ResponseEntity<List<MeasureReportEntity>> getReportsForMeasure(@PathVariable Long measureId) {
        return ResponseEntity.ok(reportService.getReportsForMeasure(measureId));
    }

    @GetMapping("/reports/{reportId}")
    @Operation(summary = "Get Report", description = "Get a specific measure report")
    public ResponseEntity<MeasureReportEntity> getReport(@PathVariable Long reportId) {
        return reportService.getReport(reportId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/reports/{reportId}")
    @Operation(summary = "Delete Report", description = "Delete a measure report (ADMIN only)")
    public ResponseEntity<Void> deleteReport(@PathVariable Long reportId) {
        reportService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }

    // ===== Report Export =====

    @GetMapping("/reports/{reportId}/export")
    @Operation(summary = "Export Report", description = "Export a measure report in FHIR, CSV, or Excel format")
    public ResponseEntity<byte[]> exportReport(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "fhir") String format) {
        return exportService.exportReport(reportId, format);
    }

    // ===== Schedules =====

    @GetMapping("/{measureId}/schedules")
    @Operation(summary = "List Schedules", description = "List schedules for a measure")
    public ResponseEntity<List<MeasureScheduleEntity>> getSchedules(@PathVariable Long measureId) {
        return ResponseEntity.ok(scheduleService.getSchedulesForMeasure(measureId));
    }

    @PostMapping("/{measureId}/schedules")
    @Operation(summary = "Create Schedule", description = "Create a new schedule for a measure")
    public ResponseEntity<MeasureScheduleEntity> createSchedule(
            @PathVariable Long measureId,
            @RequestBody MeasureScheduleEntity schedule) {
        schedule.setMeasureDefinitionId(measureId);
        MeasureScheduleEntity created = scheduleService.createSchedule(schedule);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/schedules/{scheduleId}")
    @Operation(summary = "Update Schedule", description = "Update a schedule")
    public ResponseEntity<MeasureScheduleEntity> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody MeasureScheduleEntity schedule) {
        MeasureScheduleEntity updated = scheduleService.updateSchedule(scheduleId, schedule);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @Operation(summary = "Delete Schedule", description = "Delete a schedule")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/schedules/{scheduleId}/trigger")
    @Operation(summary = "Trigger Schedule", description = "Manually trigger a scheduled evaluation")
    public ResponseEntity<MeasureEvaluationResult> triggerSchedule(@PathVariable Long scheduleId) {
        MeasureEvaluationResult result = scheduleService.triggerManually(scheduleId);
        return ResponseEntity.ok(result);
    }

    // ===== Comparison & Trends =====

    @GetMapping("/compare")
    @Operation(summary = "Compare Periods", description = "Compare measure results across two periods")
    public ResponseEntity<MeasureComparisonResult> comparePeriods(
            @RequestParam String measureName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate p1Start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate p1End,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate p2Start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate p2End) {
        MeasureComparisonResult comparison = comparisonService.comparePeriods(measureName, p1Start, p1End, p2Start, p2End);
        return ResponseEntity.ok(comparison);
    }

    @GetMapping("/trend")
    @Operation(summary = "Measure Trend", description = "Get measure score trend over time")
    public ResponseEntity<MeasureTrendResult> getTrend(
            @RequestParam String measureName,
            @RequestParam(defaultValue = "4") int periods) {
        MeasureTrendResult trend = comparisonService.getTrend(measureName, periods);
        return ResponseEntity.ok(trend);
    }
}
