package com.cqlplatform.controller;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.entity.MeasureScheduleEntity;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.measure.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cqlplatform.entity.MeasureAuditEntity;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final CqlTranslationService translationService;
    private final TestCaseService testCaseService;

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

    // ===== CQL Expressions =====

    @GetMapping("/{id}/cql-expressions")
    @Operation(summary = "Get CQL Expressions", description = "Parse a measure's CQL and return available expression names for population mapping")
    public ResponseEntity<List<CqlTranslationResponse.ExpressionInfo>> getCqlExpressions(@PathVariable Long id) {
        return definitionService.getById(id)
                .map(def -> {
                    if (def.getCqlContent() == null || def.getCqlContent().isBlank()) {
                        return ResponseEntity.ok(Collections.<CqlTranslationResponse.ExpressionInfo>emptyList());
                    }
                    CqlTranslationRequest request = new CqlTranslationRequest();
                    request.setCql(def.getCqlContent());
                    CqlTranslationResponse response = translationService.translate(request);
                    if (response.getMetadata() != null && response.getMetadata().getExpressions() != null) {
                        return ResponseEntity.ok(response.getMetadata().getExpressions());
                    }
                    return ResponseEntity.ok(Collections.<CqlTranslationResponse.ExpressionInfo>emptyList());
                })
                .orElse(ResponseEntity.notFound().build());
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

    // ===== Test Cases =====

    @GetMapping("/{measureId}/test-cases")
    @Operation(summary = "List Test Cases", description = "List test cases for a measure")
    public ResponseEntity<List<TestCase>> listTestCases(@PathVariable Long measureId) {
        return ResponseEntity.ok(testCaseService.getTestCasesForMeasure(measureId));
    }

    @GetMapping("/{measureId}/test-cases/{testCaseId}")
    @Operation(summary = "Get Test Case", description = "Get a specific test case")
    public ResponseEntity<TestCase> getTestCase(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId) {
        return testCaseService.getById(testCaseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{measureId}/test-cases")
    @Operation(summary = "Create Test Case", description = "Create a new test case for a measure")
    public ResponseEntity<TestCase> createTestCase(
            @PathVariable Long measureId,
            @RequestBody TestCase testCase) {
        TestCase created = testCaseService.create(measureId, testCase);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{measureId}/test-cases/{testCaseId}")
    @Operation(summary = "Update Test Case", description = "Update a test case")
    public ResponseEntity<TestCase> updateTestCase(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId,
            @RequestBody TestCase testCase) {
        TestCase updated = testCaseService.update(testCaseId, testCase);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{measureId}/test-cases/{testCaseId}")
    @Operation(summary = "Delete Test Case", description = "Delete a test case")
    public ResponseEntity<Void> deleteTestCase(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId) {
        testCaseService.delete(testCaseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{measureId}/test-cases/{testCaseId}/run")
    @Operation(summary = "Run Test Case", description = "Execute a single test case against the measure's CQL")
    public ResponseEntity<TestCaseRunResult> runTestCase(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId) {
        TestCaseRunResult result = testCaseService.runTestCase(testCaseId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{measureId}/test-cases/run")
    @Operation(summary = "Run All Test Cases", description = "Execute all test cases for a measure")
    public ResponseEntity<List<TestCaseRunResult>> runAllTestCases(@PathVariable Long measureId) {
        List<TestCaseRunResult> results = testCaseService.runAllTestCases(measureId);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/{measureId}/test-cases/{testCaseId}/run-with-coverage")
    @Operation(summary = "Run Test Case with Coverage", description = "Execute a test case and return per-expression coverage results")
    public ResponseEntity<CoverageResult> runWithCoverage(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId) {
        CoverageResult coverage = testCaseService.runWithCoverage(testCaseId);
        return ResponseEntity.ok(coverage);
    }

    // ===== Measure Version Management =====

    @PostMapping("/{id}/version")
    @Operation(summary = "Create Measure Version", description = "Creates a new version of a measure (major/minor/patch)")
    public ResponseEntity<MeasureDefinition> createMeasureVersion(
            @PathVariable Long id,
            @RequestParam(defaultValue = "minor") String type) {
        MeasureDefinition versioned = definitionService.createVersion(id, type);
        return ResponseEntity.ok(versioned);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Measure History", description = "Returns all versions of a measure by name")
    public ResponseEntity<List<MeasureDefinition>> getMeasureHistory(@PathVariable Long id) {
        return definitionService.getById(id)
                .map(def -> ResponseEntity.ok(definitionService.getHistory(def.getName())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/version-compare")
    @Operation(summary = "Compare Measure Versions", description = "Returns CQL content of two measure versions for diff comparison")
    public ResponseEntity<Map<String, String>> compareMeasureVersions(
            @RequestParam Long oldId,
            @RequestParam Long newId) {
        Map<String, String> comparison = definitionService.compare(oldId, newId);
        return ResponseEntity.ok(comparison);
    }

    // ===== Measure Sharing & Permissions =====

    @PostMapping("/{id}/share")
    @Operation(summary = "Share Measure", description = "Shares a measure with another user")
    public ResponseEntity<MeasureDefinition> shareMeasure(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String targetUsername = request.get("targetUsername");
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        return ResponseEntity.ok(definitionService.shareMeasure(id, targetUsername, currentUser));
    }

    @PostMapping("/{id}/unshare")
    @Operation(summary = "Unshare Measure", description = "Removes sharing for a user")
    public ResponseEntity<MeasureDefinition> unshareMeasure(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String targetUsername = request.get("targetUsername");
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        return ResponseEntity.ok(definitionService.unshareMeasure(id, targetUsername, currentUser));
    }

    @PostMapping("/{id}/transfer")
    @Operation(summary = "Transfer Measure Ownership", description = "Transfers ownership to another user")
    public ResponseEntity<MeasureDefinition> transferMeasureOwnership(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String newOwner = request.get("newOwner");
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        return ResponseEntity.ok(definitionService.transferOwnership(id, newOwner, currentUser));
    }

    @PutMapping("/{id}/access")
    @Operation(summary = "Set Measure Access Level", description = "Sets measure access level (private/shared/public)")
    public ResponseEntity<MeasureDefinition> setMeasureAccessLevel(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String accessLevel = request.get("accessLevel");
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        return ResponseEntity.ok(definitionService.setAccessLevel(id, accessLevel, currentUser));
    }

    @GetMapping("/owner/{username}")
    @Operation(summary = "Get Measures by Owner", description = "Returns all measures owned by a user")
    public ResponseEntity<List<MeasureDefinition>> getMeasuresByOwner(@PathVariable String username) {
        return ResponseEntity.ok(definitionService.getMeasuresByOwner(username));
    }

    @GetMapping("/shared/{username}")
    @Operation(summary = "Get Shared Measures", description = "Returns measures shared with a user or public")
    public ResponseEntity<List<MeasureDefinition>> getSharedMeasures(@PathVariable String username) {
        return ResponseEntity.ok(definitionService.getSharedMeasures(username));
    }

    // ===== Workflow =====

    @PostMapping("/{id}/submit-for-review")
    @Operation(summary = "Submit for Review", description = "Submits a draft measure for review")
    public ResponseEntity<MeasureDefinition> submitForReview(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        return ResponseEntity.ok(definitionService.submitForReview(id, currentUser));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve Measure", description = "Approves a measure and sets it to active")
    public ResponseEntity<MeasureDefinition> approveMeasure(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        return ResponseEntity.ok(definitionService.approveMeasure(id, currentUser));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject Measure", description = "Rejects a measure and returns it to draft")
    public ResponseEntity<MeasureDefinition> rejectMeasure(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        String reason = request.get("reason");
        return ResponseEntity.ok(definitionService.rejectMeasure(id, reason, currentUser));
    }

    @PostMapping("/{id}/retire")
    @Operation(summary = "Retire Measure", description = "Retires an active measure")
    public ResponseEntity<MeasureDefinition> retireMeasure(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        return ResponseEntity.ok(definitionService.retireMeasure(id, currentUser));
    }

    // ===== Audit Trail =====

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get Audit Trail", description = "Returns the audit trail for a measure")
    public ResponseEntity<List<MeasureAuditEntity>> getAuditTrail(@PathVariable Long id) {
        return ResponseEntity.ok(definitionService.getAuditTrail(id));
    }
}
