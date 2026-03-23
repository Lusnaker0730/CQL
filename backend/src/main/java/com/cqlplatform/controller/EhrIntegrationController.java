package com.cqlplatform.controller;

import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.model.ehr.EhrConnectionRequest;
import com.cqlplatform.model.fhir.PatientImportPreview;
import com.cqlplatform.model.fhir.PatientSearchResult;
import com.cqlplatform.security.InputValidator;
import com.cqlplatform.service.fhir.EhrConnectionService;
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patientImportService.importAsTestCase(id, patientId, measureId));
    }

    // ===== Import History =====

    @GetMapping("/imports")
    @Operation(summary = "List Imports", description = "List patient import history")
    public ResponseEntity<List<PatientImportEntity>> listImports(
            @RequestParam(required = false) String importedBy) {
        return ResponseEntity.ok(patientImportService.listImports(importedBy));
    }
}
