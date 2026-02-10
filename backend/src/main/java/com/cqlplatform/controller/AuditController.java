package com.cqlplatform.controller;

import com.cqlplatform.model.audit.*;
import com.cqlplatform.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditLogSearchRequest request = new AuditLogSearchRequest();
        request.setUsername(username);
        request.setAction(action);
        request.setResourceType(resourceType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setStatusCode(statusCode);
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
        csv.append("ID,Username,Method,Path,Resource Type,Resource ID,Action,Status Code,IP Address,Response Time (ms),Created At\n");
        for (AuditLogEntry log : logs) {
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    log.getId(),
                    escapeCsv(log.getUsername()),
                    escapeCsv(log.getMethod()),
                    escapeCsv(log.getPath()),
                    escapeCsv(log.getResourceType()),
                    escapeCsv(log.getResourceId()),
                    escapeCsv(log.getAction()),
                    log.getStatusCode() != null ? log.getStatusCode() : "",
                    escapeCsv(log.getIpAddress()),
                    log.getResponseTimeMs() != null ? log.getResponseTimeMs() : "",
                    escapeCsv(log.getCreatedAt())));
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

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
