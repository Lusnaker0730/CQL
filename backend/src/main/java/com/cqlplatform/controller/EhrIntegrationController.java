package com.cqlplatform.controller;

import com.cqlplatform.entity.BatchImportJobEntity;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.entity.FailedImportEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.model.ehr.BatchImportRequest;
import com.cqlplatform.model.ehr.EhrConnectionRequest;
import com.cqlplatform.model.fhir.PatientImportPreview;
import com.cqlplatform.model.fhir.PatientSearchResult;
import com.cqlplatform.security.InputValidator;
import com.cqlplatform.service.fhir.AsyncPatientImportService;
import com.cqlplatform.service.fhir.EhrConnectionService;
import com.cqlplatform.service.fhir.ImportRetryService;
import com.cqlplatform.service.fhir.PatientImportService;
import com.cqlplatform.service.fhir.PatientSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ehr")
@RequiredArgsConstructor
@Tag(name = "EHR Integration", description = "EHR/HIS Connection and Patient Import APIs")
public class EhrIntegrationController {

    private final EhrConnectionService connectionService;
    private final PatientSearchService patientSearchService;
    private final PatientImportService patientImportService;
    private final AsyncPatientImportService asyncImportService;
    private final ImportRetryService importRetryService;

    // ===== Connection Management =====

    @GetMapping("/connections")
    @Operation(summary = "List Connections", description = "List all active EHR connections, optionally filtered by department")
    public ResponseEntity<List<EhrConnectionEntity>> listConnections(
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(connectionService.list(department));
    }

    @GetMapping("/connections/{id}")
    @Operation(summary = "Get Connection", description = "Get an EHR connection by ID")
    public ResponseEntity<EhrConnectionEntity> getConnection(@PathVariable Long id) {
        return ResponseEntity.ok(connectionService.getById(id));
    }

    @PostMapping("/connections")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Create Connection", description = "Create a new EHR connection")
    public ResponseEntity<EhrConnectionEntity> createConnection(@Valid @RequestBody EhrConnectionRequest request) {
        InputValidator.requireValidUrl(request.getFhirServerUrl());
        if (request.getTokenEndpoint() != null && !request.getTokenEndpoint().isBlank()) {
            InputValidator.requireValidUrl(request.getTokenEndpoint());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(connectionService.create(request));
    }

    @PutMapping("/connections/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Update Connection", description = "Update an existing EHR connection")
    public ResponseEntity<EhrConnectionEntity> updateConnection(
            @PathVariable Long id,
            @Valid @RequestBody EhrConnectionRequest request) {
        InputValidator.requireValidUrl(request.getFhirServerUrl());
        if (request.getTokenEndpoint() != null && !request.getTokenEndpoint().isBlank()) {
            InputValidator.requireValidUrl(request.getTokenEndpoint());
        }
        return ResponseEntity.ok(connectionService.update(id, request));
    }

    @DeleteMapping("/connections/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Delete Connection", description = "Soft-delete an EHR connection")
    public ResponseEntity<Void> deleteConnection(@PathVariable Long id) {
        connectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/connections/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Test Connection", description = "Test connectivity to an EHR FHIR server")
    public ResponseEntity<EhrConnectionEntity> testConnection(@PathVariable Long id) {
        return ResponseEntity.ok(connectionService.testConnection(id));
    }

    // ===== Patient Search & Import =====

    @GetMapping("/connections/{id}/patients")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Search Patients", description = "Search for patients on a connected EHR server")
    public ResponseEntity<List<PatientSearchResult>> searchPatients(
            @PathVariable Long id,
            @RequestParam(required = false) String nationalId,
            @RequestParam(required = false) String mrn,
            @RequestParam(required = false) String family,
            @RequestParam(required = false) String given) {
        return ResponseEntity.ok(patientSearchService.searchPatients(id, nationalId, mrn, family, given));
    }

    @GetMapping("/connections/{id}/patients/{patientId}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Patient Import Preview", description = "Preview what data is available for a patient before importing")
    public ResponseEntity<PatientImportPreview> getPatientPreview(
            @PathVariable Long id,
            @PathVariable String patientId) {
        return ResponseEntity.ok(patientSearchService.getPatientPreview(id, patientId));
    }

    @PostMapping("/connections/{id}/patients/{patientId}/import")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Import Patient", description = "Import patient data as a test case bundle")
    public ResponseEntity<PatientImportEntity> importPatient(
            @PathVariable Long id,
            @PathVariable String patientId,
            @RequestParam(required = false) Long measureId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(patientImportService.importAsTestCase(id, patientId, measureId));
        } catch (Exception e) {
            // Record the failure for later retry
            importRetryService.recordFailure(id, patientId, measureId, e, null);
            throw e;
        }
    }

    // ===== Batch Import =====

    @PostMapping("/connections/{id}/patients/batch-import")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Batch Import Patients", description = "Submit a batch import job for multiple patients")
    public ResponseEntity<BatchImportJobEntity> batchImport(
            @PathVariable Long id,
            @Valid @RequestBody BatchImportRequest request) {
        BatchImportJobEntity job = asyncImportService.submitBatchImport(id, request);
        asyncImportService.executeBatchImport(job.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/batch-imports/{jobId}")
    @Operation(summary = "Get Batch Import Job", description = "Get the status and progress of a batch import job")
    public ResponseEntity<BatchImportJobEntity> getBatchImportJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(asyncImportService.getJob(jobId));
    }

    @GetMapping("/batch-imports")
    @Operation(summary = "List Batch Import Jobs", description = "List batch import job history")
    public ResponseEntity<List<BatchImportJobEntity>> listBatchImportJobs(
            @RequestParam(required = false) String createdBy) {
        return ResponseEntity.ok(asyncImportService.listJobs(createdBy));
    }

    @PostMapping("/batch-imports/{jobId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Cancel Batch Import", description = "Cancel a running or pending batch import job")
    public ResponseEntity<BatchImportJobEntity> cancelBatchImport(@PathVariable Long jobId) {
        return ResponseEntity.ok(asyncImportService.cancelJob(jobId));
    }

    // ===== Import History =====

    @GetMapping("/imports")
    @Operation(summary = "List Imports", description = "List patient import history")
    public ResponseEntity<List<PatientImportEntity>> listImports(
            @RequestParam(required = false) String importedBy) {
        return ResponseEntity.ok(patientImportService.listImports(importedBy));
    }

    // ===== Failed Import Management =====

    @GetMapping("/failed-imports")
    @Operation(summary = "List Failed Imports", description = "List failed patient imports for error recovery")
    public ResponseEntity<List<FailedImportEntity>> listFailedImports(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(importRetryService.listFailedImports(status));
    }

    @GetMapping("/failed-imports/{id}")
    @Operation(summary = "Get Failed Import", description = "Get details of a specific failed import")
    public ResponseEntity<FailedImportEntity> getFailedImport(@PathVariable Long id) {
        return ResponseEntity.ok(importRetryService.getFailedImport(id));
    }

    @PostMapping("/failed-imports/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Retry Failed Import", description = "Manually retry a failed patient import")
    public ResponseEntity<FailedImportEntity> retryFailedImport(@PathVariable Long id) {
        return ResponseEntity.ok(importRetryService.retryImport(id));
    }

    @DeleteMapping("/failed-imports/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")
    @Operation(summary = "Delete Failed Import", description = "Remove a failed import record")
    public ResponseEntity<Void> deleteFailedImport(@PathVariable Long id) {
        importRetryService.deleteFailedImport(id);
        return ResponseEntity.noContent().build();
    }
}
