package com.cqlplatform.controller;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.model.auth.EnabledUpdateRequest;
import com.cqlplatform.model.auth.TenantCreateUserRequest;
import com.cqlplatform.model.auth.TenantRoleUpdateRequest;
import com.cqlplatform.model.auth.UserSummary;
import com.cqlplatform.service.TenantUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Tenant-scoped staff management for a clinic ADMIN (PAT-214).
 *
 * <p>Distinct from {@link AdminController} (platform-operator, all users platform-wide): every
 * endpoint here operates only on the caller's own tenant via {@link TenantUserService}. The
 * class-level {@code hasRole('ADMIN')} is the first layer; the service confines each operation
 * to {@code effectiveTenantId()} and re-checks the target user's tenant, so a clinic admin can
 * only ever manage their own clinic's staff.
 */
@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Tenant Users", description = "A clinic admin manages the staff of their own tenant")
public class TenantUserController {

    private final TenantUserService tenantUserService;

    /** Same source as password-reset / clinic-onboarding setup links. */
    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    @GetMapping
    @Operation(summary = "List the staff of the caller's tenant")
    public ResponseEntity<List<UserSummary>> listUsers() {
        return ResponseEntity.ok(tenantUserService.listUsers().stream()
                .map(TenantUserController::toUserSummary)
                .toList());
    }

    @PostMapping
    @Operation(summary = "Create a staff account inside the caller's tenant")
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody TenantCreateUserRequest request) {
        UserEntity created = tenantUserService.createUser(
                request.getUsername(), request.getPassword(), request.getEmail(), request.getRole());
        return ResponseEntity.ok(toUserSummary(created));
    }

    @PutMapping("/{userId}/role")
    @Operation(summary = "Change a staff member's role (within the caller's tenant)")
    public ResponseEntity<UserSummary> updateRole(
            @PathVariable Long userId,
            @Valid @RequestBody TenantRoleUpdateRequest request) {
        UserEntity saved = tenantUserService.updateRole(userId, request.getRole(), callerUsername());
        return ResponseEntity.ok(toUserSummary(saved));
    }

    @PutMapping("/{userId}/enabled")
    @Operation(summary = "Enable / disable a staff member (within the caller's tenant)")
    public ResponseEntity<UserSummary> updateEnabled(
            @PathVariable Long userId,
            @Valid @RequestBody EnabledUpdateRequest request) {
        UserEntity saved = tenantUserService.setEnabled(userId, request.getEnabled(), callerUsername());
        return ResponseEntity.ok(toUserSummary(saved));
    }

    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "Reset a staff member's password; returns a one-time setup link")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long userId) {
        String setupLink = tenantUserService.resetPassword(userId, configuredBaseUrl);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("setupLink", setupLink));
    }

    private static String callerUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private static UserSummary toUserSummary(UserEntity user) {
        return UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail() != null ? user.getEmail() : "")
                .role(user.getRole().name())
                .enabled(user.getEnabled())
                .forcePasswordChange(Boolean.TRUE.equals(user.getForcePasswordChange()))
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : "LOCAL")
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")
                .build();
    }
}
