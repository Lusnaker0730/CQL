package com.cqlplatform.controller;

import com.cqlplatform.model.admin.ClinicApplicationResponse;
import com.cqlplatform.service.ClinicApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Operator review of clinic applications (#700). Same two-layer authorization as
 * tenant management: hasRole('ADMIN') here + PlatformOperatorGuard in the service.
 */
@RestController
@RequestMapping("/api/admin/clinic-applications")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Clinic Applications Admin", description = "Review and provision clinic applications")
public class ClinicApplicationAdminController {

    private final ClinicApplicationService clinicApplicationService;

    /**
     * Base URL for the setup link. Reuses APP_BASE_URL (same source as password-reset
     * links); when unset the link is returned relative — the operator prepends the
     * site origin when handing it over.
     */
    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    @GetMapping
    @Operation(summary = "List clinic applications (optionally by status)")
    public ResponseEntity<List<ClinicApplicationResponse>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(clinicApplicationService.list(status).stream()
                .map(ClinicApplicationResponse::from)
                .toList());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an application",
            description = "Provisions the tenant + first tenant-admin and returns a one-time setup link "
                    + "(also emailed best-effort). Response is no-store.")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id) {
        String reviewer = SecurityContextHolder.getContext().getAuthentication().getName();
        var result = clinicApplicationService.approve(id, reviewer, configuredBaseUrl);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of(
                        "application", ClinicApplicationResponse.from(result.application()),
                        "setupLink", result.setupLink()));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject an application")
    public ResponseEntity<ClinicApplicationResponse> reject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectRequest request) {
        String reviewer = SecurityContextHolder.getContext().getAuthentication().getName();
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(ClinicApplicationResponse.from(
                clinicApplicationService.reject(id, reason, reviewer)));
    }

    @Data
    public static class RejectRequest {
        @Size(max = 500)
        private String reason;
    }
}
