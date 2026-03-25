package com.cqlplatform.controller;

import com.cqlplatform.model.audit.*;
import com.cqlplatform.service.AuditService;
import com.cqlplatform.util.CsvUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit log query and management APIs")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/logs")
    public ResponseEntity<AuditLogResponse> getLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) Long connectionId,
            @RequestParam(required = false) String patientFhirId,
            @RequestParam(required = false) Boolean phiAccess,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditLogSearchRequest request = new AuditLogSearchRequest();
        request.setUsername(username);
        request.setAction(action);
        request.setResourceType(resourceType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setStatusCode(statusCode);
        request.setConnectionId(connectionId);
        request.setPatientFhirId(patientFhirId);
        request.setPhiAccess(phiAccess);
        request.setPage(page);
        request.setSize(size);

        return ResponseEntity.ok(auditService.searchLogs(request));
    }

    @GetMapping("/logs/export")
    public ResponseEntity<byte[]> exportLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer statusCode) {

        AuditLogSearchRequest request = new AuditLogSearchRequest();
        request.setUsername(username);
        request.setAction(action);
        request.setResourceType(resourceType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setStatusCode(statusCode);

        List<AuditLogEntry> logs = auditService.exportLogs(request);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Username,Method,Path,Resource Type,Resource ID,Action,Status Code,IP Address,PHI Access,Connection ID,Patient FHIR ID,Connection Name,Request ID,Response Time (ms),Created At\n");
        for (AuditLogEntry log : logs) {
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    log.getId(),
                    CsvUtils.escapeCsv(log.getUsername()),
                    CsvUtils.escapeCsv(log.getMethod()),
                    CsvUtils.escapeCsv(log.getPath()),
                    CsvUtils.escapeCsv(log.getResourceType()),
                    CsvUtils.escapeCsv(log.getResourceId()),
                    CsvUtils.escapeCsv(log.getAction()),
                    log.getStatusCode() != null ? log.getStatusCode() : "",
                    CsvUtils.escapeCsv(log.getIpAddress()),
                    log.isPhiAccess(),
                    log.getConnectionId() != null ? log.getConnectionId() : "",
                    CsvUtils.escapeCsv(log.getPatientFhirId()),
                    CsvUtils.escapeCsv(log.getConnectionName()),
                    CsvUtils.escapeCsv(log.getRequestId()),
                    log.getResponseTimeMs() != null ? log.getResponseTimeMs() : "",
                    CsvUtils.escapeCsv(log.getCreatedAt())));
        }

        byte[] csvBytes = csv.toString().getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit_logs.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }

    @GetMapping("/stats")
    public ResponseEntity<AuditStatsResponse> getStats() {
        return ResponseEntity.ok(auditService.getStats());
    }

    @GetMapping("/phi-access")
    public ResponseEntity<AuditLogResponse> getPhiAccess(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate) {
        return ResponseEntity.ok(auditService.getPhiAccessLog(page, size, startDate));
    }

    @GetMapping("/login-activity")
    public ResponseEntity<AuditLogResponse> getLoginActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate) {
        return ResponseEntity.ok(auditService.getLoginActivity(page, size, startDate));
    }

    @GetMapping("/security-events")
    public ResponseEntity<AuditLogResponse> getSecurityEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String startDate) {
        return ResponseEntity.ok(auditService.getSecurityEvents(page, size, startDate));
    }

    @GetMapping("/ehr-operations")
    @Operation(summary = "EHR Operations Audit", description = "List EHR-specific operations for compliance review")
    public ResponseEntity<AuditLogResponse> ehrOperations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long connectionId,
            @RequestParam(required = false, defaultValue = "30") int days) {
        return ResponseEntity.ok(auditService.getEhrOperations(page, size, connectionId, days));
    }

    @GetMapping("/retention")
    @Operation(summary = "Retention Policy", description = "Get current audit retention policy settings")
    public ResponseEntity<Map<String, Object>> getRetentionPolicy() {
        return ResponseEntity.ok(Map.of(
                "retentionDays", auditService.getRetentionDays()
        ));
    }

    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manual Cleanup", description = "Manually trigger audit log retention cleanup")
    public ResponseEntity<Map<String, Object>> manualCleanup(
            @RequestParam(required = false) Integer olderThanDays) {
        int days = olderThanDays != null ? olderThanDays : auditService.getRetentionDays();
        int deleted = auditService.manualCleanup(days);
        return ResponseEntity.ok(Map.of(
                "deletedCount", deleted,
                "olderThanDays", days
        ));
    }
}
