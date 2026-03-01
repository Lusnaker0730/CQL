package com.cqlplatform.controller;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.entity.MeasureScheduleEntity;
import com.cqlplatform.exception.CqlExecutionException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.model.measure.ValidationReport;
import com.cqlplatform.model.request.AccessUpdateRequest;
import com.cqlplatform.model.request.TransferOwnershipRequest;
import com.cqlplatform.model.request.UsernameRequest;
import com.cqlplatform.model.request.WorkflowActionRequest;
import com.cqlplatform.security.InputValidator;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.DataRequirementExtractor;
import com.cqlplatform.service.measure.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final MeasureValidationService validationService;
    private final FhirMeasureBundleService bundleService;
    private final FhirMeasureBundleImportService bundleImportService;
    private final HqmfExportService hqmfExportService;
    private final HumanReadableService humanReadableService;
    private final BatchEvaluationService batchEvaluationService;
    private final DataRequirementExtractor dataRequirementExtractor;
    private final DashboardService dashboardService;
    private final OwnershipVerifier ownershipVerifier;

    // ===== Helpers =====

    /** Fetch a measure or throw 404. */
    private MeasureDefinition requireMeasure(Long id) {
        return definitionService.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Measure", id));
    }

    /** Fetch a measure and verify the current user owns it (or is admin). */
    private MeasureDefinition requireOwnedMeasure(Long id) {
        MeasureDefinition m = requireMeasure(id);
        ownershipVerifier.verifyOwnership(m.getOwnerUsername());
        return m;
    }

    /** Fetch a schedule by ID and verify ownership of its parent measure. */
    private MeasureScheduleEntity requireOwnedSchedule(Long scheduleId) {
        MeasureScheduleEntity schedule = scheduleService.getScheduleById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));
        requireOwnedMeasure(schedule.getMeasureDefinitionId());
        return schedule;
    }

    /** Verify a test case belongs to the given measure, throw 400 if mismatch. */
    private void verifyTestCaseBelongsToMeasure(Long measureId, Long testCaseId) {
        testCaseService.getById(testCaseId).ifPresent(tc -> {
            if (tc.getMeasureDefinitionId() != null && !tc.getMeasureDefinitionId().equals(measureId)) {
                throw new ValidationException(
                        "Test case " + testCaseId + " does not belong to measure " + measureId);
            }
        });
    }

    // ===== Measure Definition CRUD =====

    @GetMapping
    @Operation(summary = "List Measures", description = "List all measure definitions, optionally filtered by search term and department. Supports pagination via page/size params.")
    public ResponseEntity<List<MeasureDefinition>> listMeasures(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "200") Integer size) {
        int clampedSize = (int) Math.clamp(size, 1, 200);
        List<MeasureDefinition> measures = definitionService.search(search, department);
        if (page != null) {
            int start = page * clampedSize;
            if (start >= measures.size()) {
                return ResponseEntity.ok(List.of());
            }
            measures = measures.subList(start, Math.min(start + clampedSize, measures.size()));
        }
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
    public ResponseEntity<MeasureDefinition> createMeasure(@Valid @RequestBody MeasureDefinition definition) {
        MeasureDefinition created = definitionService.create(definition);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Measure", description = "Update an existing measure definition")
    public ResponseEntity<MeasureDefinition> updateMeasure(
            @PathVariable Long id,
            @Valid @RequestBody MeasureDefinition definition) {
        requireOwnedMeasure(id);
        MeasureDefinition updated = definitionService.update(id, definition);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Measure", description = "Delete a measure definition (owner or ADMIN)")
    public ResponseEntity<Void> deleteMeasure(@PathVariable Long id) {
        requireOwnedMeasure(id);
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

    // ===== Bundle Export/Import =====

    @GetMapping("/{id}/export/bundle")
    @Operation(summary = "Export FHIR Bundle", description = "Exports a complete FHIR Bundle with Measure, Library, and ValueSet resources")
    public ResponseEntity<byte[]> exportBundle(
            @PathVariable Long id,
            @RequestParam(defaultValue = "json") String format) {
        if ("xml".equalsIgnoreCase(format)) {
            String xml = bundleService.exportAsBundleXml(id);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=measure-bundle-" + id + ".xml")
                    .header("Content-Type", "application/fhir+xml")
                    .body(xml.getBytes(StandardCharsets.UTF_8));
        }
        var bundle = bundleService.exportAsBundle(id);
        try {
            byte[] json = new ObjectMapper()
                    .writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=measure-bundle-" + id + ".json")
                    .header("Content-Type", "application/fhir+json")
                    .body(json);
        } catch (Exception e) {
            throw new CqlExecutionException("Failed to serialize bundle: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/export/cql")
    @Operation(summary = "Export CQL Only", description = "Exports the CQL content of a measure")
    public ResponseEntity<byte[]> exportCql(@PathVariable Long id) {
        String cql = bundleService.exportCqlOnly(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=measure-" + id + ".cql")
                .header("Content-Type", "text/cql")
                .body(cql.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}/export/elm")
    @Operation(summary = "Export ELM Only", description = "Exports the ELM JSON translation of a measure's CQL")
    public ResponseEntity<byte[]> exportElm(@PathVariable Long id) {
        String elm = bundleService.exportElmOnly(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=measure-" + id + "-elm.json")
                .header("Content-Type", "application/elm+json")
                .body(elm.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/import/bundle")
    @Operation(summary = "Import FHIR Bundle", description = "Imports a FHIR Bundle containing Measure, Library, and ValueSet resources")
    public ResponseEntity<FhirMeasureBundleImportService.BundleImportResult> importBundle(
            @RequestBody JsonNode bundleJson) {
        var result = bundleImportService.importBundle(bundleJson);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/export/hqmf")
    @Operation(summary = "Export HQMF", description = "Exports a measure as HQMF R2.1 XML for CMS submission")
    public ResponseEntity<byte[]> exportHqmf(@PathVariable Long id) {
        String hqmf = hqmfExportService.exportHqmf(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=measure-" + id + "-hqmf.xml")
                .header("Content-Type", "application/xml")
                .body(hqmf.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}/export/human-readable")
    @Operation(summary = "Export Human Readable", description = "Generates a human-readable HTML narrative document for the measure")
    public ResponseEntity<byte[]> exportHumanReadable(@PathVariable Long id) {
        MeasureDefinition measure = requireMeasure(id);
        String html = humanReadableService.generateHtml(measure);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=measure-" + id + "-narrative.html")
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(html.getBytes(StandardCharsets.UTF_8));
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

    // ===== Data Requirements =====

    @GetMapping("/{id}/data-requirements")
    @Operation(summary = "Get Data Requirements", description = "Extract FHIR DataRequirement resources from a measure's CQL/ELM")
    public ResponseEntity<List<DataRequirementInfo>> getDataRequirements(@PathVariable Long id) {
        return definitionService.getById(id)
                .map(def -> {
                    if (def.getCqlContent() == null || def.getCqlContent().isBlank()) {
                        return ResponseEntity.ok(Collections.<DataRequirementInfo>emptyList());
                    }
                    CqlTranslationRequest request = new CqlTranslationRequest();
                    request.setCql(def.getCqlContent());
                    CqlTranslationResponse response = translationService.translate(request);
                    if (!response.isSuccess() || response.getElmJson() == null) {
                        return ResponseEntity.ok(Collections.<DataRequirementInfo>emptyList());
                    }
                    List<DataRequirementInfo> requirements = dataRequirementExtractor.extract(response.getElmJson());
                    return ResponseEntity.ok(requirements);
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
            @Valid @RequestBody(required = false) MeasureEvaluationRequest request) {

        if (request == null) {
            request = new MeasureEvaluationRequest();
        }
        InputValidator.requireValidUrl(request.getFhirServerUrl());
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
                if (com.cqlplatform.model.measure.ScoringTypeConstants.COMPOSITE.equalsIgnoreCase(def.getScoringType())) {
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
            @Valid @RequestBody MeasureEvaluationRequest request) {
        InputValidator.requireValidUrl(request.getFhirServerUrl());
        MeasureEvaluationResult result = measureService.evaluateMeasure(request);
        return ResponseEntity.ok(result);
    }

    // ===== Reports =====

    @GetMapping("/reports")
    @Operation(summary = "List Reports", description = "List recent measure reports (filtered by current user, admins see all)")
    public ResponseEntity<List<MeasureReportEntity>> listReports() {
        List<MeasureReportEntity> reports = reportService.getRecentReports();
        return ResponseEntity.ok(filterReportsByCurrentUser(reports));
    }

    @GetMapping("/{measureId}/reports")
    @Operation(summary = "Reports for Measure", description = "List reports for a specific measure (filtered by current user, admins see all)")
    public ResponseEntity<List<MeasureReportEntity>> getReportsForMeasure(@PathVariable Long measureId) {
        List<MeasureReportEntity> reports = reportService.getReportsForMeasure(measureId);
        return ResponseEntity.ok(filterReportsByCurrentUser(reports));
    }

    @GetMapping("/reports/{reportId}")
    @Operation(summary = "Get Report", description = "Get a specific measure report (owner or admin only)")
    public ResponseEntity<MeasureReportEntity> getReport(@PathVariable Long reportId) {
        Optional<MeasureReportEntity> opt = reportService.getReport(reportId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        MeasureReportEntity report = opt.get();
        if (!ownershipVerifier.isAdmin() && !ownershipVerifier.getCurrentUsername().equals(report.getEvaluatedBy())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(report);
    }

    @DeleteMapping("/reports/{reportId}")
    @Operation(summary = "Delete Report", description = "Delete a measure report (owner or admin only)")
    public ResponseEntity<Void> deleteReport(@PathVariable Long reportId) {
        Optional<MeasureReportEntity> opt = reportService.getReport(reportId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!ownershipVerifier.isAdmin() && !ownershipVerifier.getCurrentUsername().equals(opt.get().getEvaluatedBy())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        reportService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }

    private List<MeasureReportEntity> filterReportsByCurrentUser(List<MeasureReportEntity> reports) {
        if (ownershipVerifier.isAdmin()) {
            return reports;
        }
        String username = ownershipVerifier.getCurrentUsername();
        return reports.stream()
                .filter(r -> username.equals(r.getEvaluatedBy()))
                .toList();
    }

    // ===== Report Export =====

    @GetMapping("/reports/{reportId}/export")
    @Operation(summary = "Export Report", description = "Export a measure report in FHIR, CSV, or Excel format (owner or admin only)")
    public ResponseEntity<byte[]> exportReport(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "fhir") String format) {
        Optional<MeasureReportEntity> opt = reportService.getReport(reportId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!ownershipVerifier.isAdmin() && !ownershipVerifier.getCurrentUsername().equals(opt.get().getEvaluatedBy())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return exportService.exportReport(reportId, format);
    }

    // ===== Schedules =====

    @GetMapping("/{measureId}/schedules")
    @Operation(summary = "List Schedules", description = "List schedules for a measure (owner or admin only)")
    public ResponseEntity<List<MeasureScheduleEntity>> getSchedules(@PathVariable Long measureId) {
        requireOwnedMeasure(measureId);
        return ResponseEntity.ok(scheduleService.getSchedulesForMeasure(measureId));
    }

    @PostMapping("/{measureId}/schedules")
    @Operation(summary = "Create Schedule", description = "Create a new schedule for a measure (owner or admin only)")
    public ResponseEntity<MeasureScheduleEntity> createSchedule(
            @PathVariable Long measureId,
            @Valid @RequestBody MeasureScheduleEntity schedule) {
        requireOwnedMeasure(measureId);
        InputValidator.requireValidUrl(schedule.getFhirServerUrl());
        schedule.setMeasureDefinitionId(measureId);
        MeasureScheduleEntity created = scheduleService.createSchedule(schedule);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/schedules/{scheduleId}")
    @Operation(summary = "Update Schedule", description = "Update a schedule (owner or admin only)")
    public ResponseEntity<MeasureScheduleEntity> updateSchedule(
            @PathVariable Long scheduleId,
            @Valid @RequestBody MeasureScheduleEntity schedule) {
        requireOwnedSchedule(scheduleId);
        InputValidator.requireValidUrl(schedule.getFhirServerUrl());
        MeasureScheduleEntity updated = scheduleService.updateSchedule(scheduleId, schedule);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @Operation(summary = "Delete Schedule", description = "Delete a schedule (owner or admin only)")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long scheduleId) {
        requireOwnedSchedule(scheduleId);
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/schedules/{scheduleId}/trigger")
    @Operation(summary = "Trigger Schedule", description = "Manually trigger a scheduled evaluation (owner or admin only)")
    public ResponseEntity<MeasureEvaluationResult> triggerSchedule(@PathVariable Long scheduleId) {
        requireOwnedSchedule(scheduleId);
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
        int clampedPeriods = (int) Math.clamp(periods, 1, 52);
        MeasureTrendResult trend = comparisonService.getTrend(measureName, clampedPeriods);
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
        requireMeasure(measureId);
        verifyTestCaseBelongsToMeasure(measureId, testCaseId);
        return testCaseService.getById(testCaseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{measureId}/test-cases")
    @Operation(summary = "Create Test Case", description = "Create a new test case for a measure")
    public ResponseEntity<TestCase> createTestCase(
            @PathVariable Long measureId,
            @Valid @RequestBody TestCase testCase) {
        requireOwnedMeasure(measureId);
        TestCase created = testCaseService.create(measureId, testCase);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{measureId}/test-cases/{testCaseId}")
    @Operation(summary = "Update Test Case", description = "Update a test case")
    public ResponseEntity<TestCase> updateTestCase(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId,
            @Valid @RequestBody TestCase testCase) {
        requireOwnedMeasure(measureId);
        verifyTestCaseBelongsToMeasure(measureId, testCaseId);
        TestCase updated = testCaseService.update(testCaseId, testCase);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{measureId}/test-cases/{testCaseId}")
    @Operation(summary = "Delete Test Case", description = "Delete a test case")
    public ResponseEntity<Void> deleteTestCase(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId) {
        requireOwnedMeasure(measureId);
        verifyTestCaseBelongsToMeasure(measureId, testCaseId);
        testCaseService.delete(testCaseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{measureId}/test-cases/batch-import")
    @Operation(summary = "Batch Import Test Cases", description = "Import multiple test cases at once with optional date shifting")
    public ResponseEntity<BatchTestCaseImportResult> batchImportTestCases(
            @PathVariable Long measureId,
            @Valid @RequestBody BatchTestCaseImportRequest request) {
        requireOwnedMeasure(measureId);
        BatchTestCaseImportResult result = testCaseService.batchImport(
                measureId, request.getTestCases(), request.getDateShiftDays());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{measureId}/test-cases/{testCaseId}/run")
    @Operation(summary = "Run Test Case", description = "Execute a single test case against the measure's CQL")
    public ResponseEntity<TestCaseRunResult> runTestCase(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId) {
        requireOwnedMeasure(measureId);
        verifyTestCaseBelongsToMeasure(measureId, testCaseId);
        TestCaseRunResult result = testCaseService.runTestCase(testCaseId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{measureId}/test-cases/run")
    @Operation(summary = "Run All Test Cases", description = "Execute all test cases for a measure")
    public ResponseEntity<List<TestCaseRunResult>> runAllTestCases(@PathVariable Long measureId) {
        requireOwnedMeasure(measureId);
        List<TestCaseRunResult> results = testCaseService.runAllTestCases(measureId);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/{measureId}/test-cases/{testCaseId}/run-with-coverage")
    @Operation(summary = "Run Test Case with Coverage", description = "Execute a test case and return per-expression coverage results")
    public ResponseEntity<CoverageResult> runWithCoverage(
            @PathVariable Long measureId,
            @PathVariable Long testCaseId) {
        requireOwnedMeasure(measureId);
        verifyTestCaseBelongsToMeasure(measureId, testCaseId);
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
    @Operation(summary = "Measure History", description = "Returns all versions of a measure by name (owner or admin only)")
    public ResponseEntity<List<MeasureDefinition>> getMeasureHistory(@PathVariable Long id) {
        MeasureDefinition measure = requireOwnedMeasure(id);
        return ResponseEntity.ok(definitionService.getHistory(measure.getName()));
    }

    @GetMapping("/version-compare")
    @Operation(summary = "Compare Measure Versions", description = "Returns CQL content and structured diff of two measure versions")
    public ResponseEntity<Map<String, Object>> compareMeasureVersions(
            @RequestParam Long oldId,
            @RequestParam Long newId) {
        requireOwnedMeasure(oldId);
        requireOwnedMeasure(newId);
        Map<String, Object> comparison = definitionService.compare(oldId, newId);
        return ResponseEntity.ok(comparison);
    }

    // ===== Measure Sharing & Permissions =====

    @PostMapping("/{id}/share")
    @Operation(summary = "Share Measure", description = "Shares a measure with another user")
    public ResponseEntity<MeasureDefinition> shareMeasure(
            @PathVariable Long id,
            @Valid @RequestBody UsernameRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.shareMeasure(id, request.getTargetUsername(), currentUser));
    }

    @PostMapping("/{id}/unshare")
    @Operation(summary = "Unshare Measure", description = "Removes sharing for a user")
    public ResponseEntity<MeasureDefinition> unshareMeasure(
            @PathVariable Long id,
            @Valid @RequestBody UsernameRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.unshareMeasure(id, request.getTargetUsername(), currentUser));
    }

    @PostMapping("/{id}/transfer")
    @Operation(summary = "Transfer Measure Ownership", description = "Transfers ownership to another user")
    public ResponseEntity<MeasureDefinition> transferMeasureOwnership(
            @PathVariable Long id,
            @Valid @RequestBody TransferOwnershipRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.transferOwnership(id, request.getNewOwner(), currentUser));
    }

    @PutMapping("/{id}/access")
    @Operation(summary = "Set Measure Access Level", description = "Sets measure access level (private/shared/public)")
    public ResponseEntity<MeasureDefinition> setMeasureAccessLevel(
            @PathVariable Long id,
            @Valid @RequestBody AccessUpdateRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.setAccessLevel(id, request.getAccessLevel(), currentUser));
    }

    @GetMapping("/owner/{username}")
    @Operation(summary = "Get Measures by Owner", description = "Returns all measures owned by a user (own user or admin only)")
    public ResponseEntity<List<MeasureDefinition>> getMeasuresByOwner(@PathVariable String username) {
        if (!ownershipVerifier.isAdmin() && !ownershipVerifier.getCurrentUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(definitionService.getMeasuresByOwner(username));
    }

    @GetMapping("/shared/{username}")
    @Operation(summary = "Get Shared Measures", description = "Returns measures shared with a user or public (own user or admin only)")
    public ResponseEntity<List<MeasureDefinition>> getSharedMeasures(@PathVariable String username) {
        if (!ownershipVerifier.isAdmin() && !ownershipVerifier.getCurrentUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(definitionService.getSharedMeasures(username));
    }

    // ===== Workflow =====

    @PostMapping("/{id}/submit-for-review")
    @Operation(summary = "Submit for Review", description = "Submits a draft measure for review")
    public ResponseEntity<MeasureDefinition> submitForReview(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowActionRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.submitForReview(id, currentUser));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve Measure", description = "Approves a measure and sets it to active")
    public ResponseEntity<MeasureDefinition> approveMeasure(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowActionRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.approveMeasure(id, currentUser));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject Measure", description = "Rejects a measure and returns it to draft")
    public ResponseEntity<MeasureDefinition> rejectMeasure(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowActionRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.rejectMeasure(id, request.getReason(), currentUser));
    }

    @PostMapping("/{id}/retire")
    @Operation(summary = "Retire Measure", description = "Retires an active measure")
    public ResponseEntity<MeasureDefinition> retireMeasure(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowActionRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.retireMeasure(id, currentUser));
    }

    // ===== Locking =====

    @PostMapping("/{id}/lock")
    @Operation(summary = "Lock Measure", description = "Locks a measure for exclusive editing")
    public ResponseEntity<MeasureDefinition> lockMeasure(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowActionRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.lockMeasure(id, currentUser));
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Unlock Measure", description = "Unlocks a measure to allow other users to edit")
    public ResponseEntity<MeasureDefinition> unlockMeasure(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowActionRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(definitionService.unlockMeasure(id, currentUser));
    }

    // ===== Validation =====

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate Measure", description = "Runs full validation on a measure (CQL, populations, metadata, test cases, QI-Core)")
    public ResponseEntity<ValidationReport> validateMeasure(@PathVariable Long id) {
        ValidationReport report = validationService.validateFull(id);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/{id}/validate/quick")
    @Operation(summary = "Quick Validate Measure", description = "Runs lightweight validation (CQL + populations only)")
    public ResponseEntity<ValidationReport> quickValidateMeasure(@PathVariable Long id) {
        ValidationReport report = validationService.validateQuick(id);
        return ResponseEntity.ok(report);
    }

    // ===== Dashboard =====

    @GetMapping("/dashboard")
    @Operation(summary = "Measure Dashboard", description = "Returns summary statistics for measures visible to the current user")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        List<MeasureDefinition> all = ownershipVerifier.isAdmin()
                ? definitionService.getAll()
                : definitionService.getAccessibleMeasures(ownershipVerifier.getCurrentUsername());
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("totalMeasures", all.size());

        Map<String, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getStatus() != null ? m.getStatus() : "draft",
                        Collectors.counting()));
        dashboard.put("byStatus", byStatus);

        Map<String, Long> byScoring = all.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getScoringType() != null ? m.getScoringType() : com.cqlplatform.model.measure.ScoringTypeConstants.PROPORTION,
                        Collectors.counting()));
        dashboard.put("byScoring", byScoring);

        Map<String, Long> bySteward = all.stream()
                .filter(m -> m.getSteward() != null && !m.getSteward().isBlank())
                .collect(Collectors.groupingBy(
                        MeasureDefinition::getSteward,
                        Collectors.counting()));
        dashboard.put("bySteward", bySteward);

        List<MeasureReportEntity> recentReports = filterReportsByCurrentUser(reportService.getRecentReports());
        List<Map<String, Object>> recentEvaluations = recentReports.stream()
                .limit(10)
                .map(r -> {
                    Map<String, Object> re = new LinkedHashMap<>();
                    re.put("id", r.getId());
                    re.put("measureName", r.getMeasureName());
                    re.put("score", r.getMeasureScore());
                    re.put("status", r.getStatus());
                    re.put("createdAt", r.getCreatedAt());
                    return re;
                })
                .toList();
        dashboard.put("recentEvaluations", recentEvaluations);

        List<Map<String, Object>> pendingReview = all.stream()
                .filter(m -> "in-review".equals(m.getStatus()))
                .map(m -> {
                    Map<String, Object> pr = new LinkedHashMap<>();
                    pr.put("id", m.getId());
                    pr.put("name", m.getTitle() != null ? m.getTitle() : m.getName());
                    pr.put("version", m.getVersion());
                    pr.put("owner", m.getOwnerUsername());
                    return pr;
                })
                .toList();
        dashboard.put("pendingReview", pendingReview);

        return ResponseEntity.ok(dashboard);
    }

    // ===== Batch Evaluation =====

    @PostMapping("/batch-evaluate")
    @Operation(summary = "Batch Evaluate", description = "Evaluates multiple measures and returns per-measure results")
    public ResponseEntity<BatchEvaluationService.BatchResult> batchEvaluate(
            @Valid @RequestBody BatchEvaluationService.BatchEvaluationRequest request) {
        InputValidator.requireValidUrl(request.fhirServerUrl());
        return ResponseEntity.ok(batchEvaluationService.evaluateBatch(request));
    }

    // ===== Audit Trail =====

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get Audit Trail", description = "Returns the audit trail for a measure (owner or admin only)")
    public ResponseEntity<List<MeasureAuditEntity>> getAuditTrail(@PathVariable Long id) {
        requireOwnedMeasure(id);
        return ResponseEntity.ok(definitionService.getAuditTrail(id));
    }

    // ===== Enhanced Dashboard =====

    @GetMapping("/dashboard/enhanced")
    @Operation(summary = "Enhanced Dashboard", description = "Returns enhanced dashboard with trends, alerts, department scores")
    public ResponseEntity<EnhancedDashboardData> getEnhancedDashboard(
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(dashboardService.getEnhancedDashboard(department));
    }

    @GetMapping("/dashboard/trends")
    @Operation(summary = "Score Trends", description = "Returns time-series trend data for measures")
    public ResponseEntity<List<EnhancedDashboardData.TrendDataPoint>> getTrends(
            @RequestParam(required = false) Long measureId,
            @RequestParam(defaultValue = "monthly") String periodType,
            @RequestParam(defaultValue = "12") int count) {
        int clampedCount = (int) Math.clamp(count, 1, 52);
        return ResponseEntity.ok(dashboardService.getTrends(measureId, periodType, clampedCount));
    }

    @GetMapping("/dashboard/department/{code}")
    @Operation(summary = "Department Drill-down", description = "Returns department-specific dashboard data")
    public ResponseEntity<Map<String, Object>> getDepartmentDrilldown(@PathVariable String code) {
        return ResponseEntity.ok(dashboardService.getDepartmentDrilldown(code));
    }

    @GetMapping("/dashboard/alerts")
    @Operation(summary = "Threshold Alerts", description = "Returns threshold violation alerts")
    public ResponseEntity<List<ThresholdAlert>> getAlerts(
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(dashboardService.getAlerts(department));
    }

    @PostMapping("/{id}/thresholds")
    @Operation(summary = "Set Threshold", description = "Sets a threshold for a measure (owner or admin only)")
    public ResponseEntity<com.cqlplatform.entity.MeasureThresholdEntity> setThreshold(
            @PathVariable Long id,
            @RequestBody com.cqlplatform.entity.MeasureThresholdEntity threshold) {
        requireOwnedMeasure(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(dashboardService.setThreshold(id, threshold));
    }

    @GetMapping("/{id}/thresholds")
    @Operation(summary = "Get Thresholds", description = "Returns thresholds for a measure")
    public ResponseEntity<List<com.cqlplatform.entity.MeasureThresholdEntity>> getThresholds(
            @PathVariable Long id) {
        return ResponseEntity.ok(dashboardService.getThresholds(id));
    }

    @GetMapping("/dashboard/report")
    @Operation(summary = "Quality Report", description = "Generates a quality report")
    public ResponseEntity<QualityReport> getQualityReport(
            @RequestParam(defaultValue = "monthly") String type,
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(dashboardService.generateReport(type, department));
    }
}
