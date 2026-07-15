package com.cqlplatform.controller;

import com.cqlplatform.model.auth.ClinicApplicationRequest;
import com.cqlplatform.service.ClinicApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public clinic application intake (#700). permitAll (SecurityConfig) + AUTH-tier
 * rate limit (RateLimitFilter). The response is deliberately uniform: nothing about
 * existing tenants/usernames is revealed (same anti-enumeration stance as register).
 */
@RestController
@RequestMapping("/api/auth/clinic-applications")
@RequiredArgsConstructor
@Tag(name = "Clinic Applications", description = "Public clinic onboarding applications")
public class ClinicApplicationController {

    static final Map<String, String> APPLICATION_RECEIVED_RESPONSE = Map.of(
            "message", "Application received. The platform team will review it and contact you by email.");

    private final ClinicApplicationService clinicApplicationService;

    @PostMapping
    @Operation(summary = "Submit a clinic application", description = "Public — reviewed by the platform operator")
    public ResponseEntity<Map<String, String>> submit(@Valid @RequestBody ClinicApplicationRequest request) {
        clinicApplicationService.submit(
                request.getClinicName(),
                request.getTenantCode(),
                request.getAdminUsername(),
                request.getAdminEmail());
        return ResponseEntity.ok(APPLICATION_RECEIVED_RESPONSE);
    }
}
