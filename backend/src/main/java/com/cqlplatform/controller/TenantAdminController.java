package com.cqlplatform.controller;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.model.admin.TenantActiveRequest;
import com.cqlplatform.model.admin.TenantCreateRequest;
import com.cqlplatform.model.admin.TenantResponse;
import com.cqlplatform.model.admin.TenantUserResponse;
import com.cqlplatform.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tenant (clinic) management — platform operator only (PAT-201 / #699).
 *
 * <p>Authorization is two-layered: {@code hasRole('ADMIN')} here (note: deliberately
 * NOT DEPARTMENT_ADMIN, unlike the rest of {@code /api/admin/**}) plus the
 * platform-operator tenant boundary inside {@link TenantService} — a clinic tenant's
 * ADMIN gets 403 from the service even though the role check passes.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Tenant Admin", description = "Clinic (tenant) provisioning and user assignment")
public class TenantAdminController {

    private final TenantService tenantService;

    @GetMapping
    @Operation(summary = "List all tenants (active and deactivated)")
    public ResponseEntity<List<TenantResponse>> listTenants() {
        return ResponseEntity.ok(tenantService.listAll().stream().map(TenantResponse::from).toList());
    }

    @PostMapping
    @Operation(summary = "Provision a new clinic tenant")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantCreateRequest request) {
        TenantEntity created = tenantService.createTenant(request.getCode(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(created));
    }

    @PutMapping("/{id}/active")
    @Operation(summary = "Activate or deactivate a tenant")
    public ResponseEntity<TenantResponse> setActive(
            @PathVariable Long id,
            @Valid @RequestBody TenantActiveRequest request) {
        return ResponseEntity.ok(TenantResponse.from(tenantService.setActive(id, request.getActive())));
    }

    @GetMapping("/{id}/users")
    @Operation(summary = "List a tenant's users")
    public ResponseEntity<List<TenantUserResponse>> listUsers(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.listUsers(id).stream().map(TenantUserResponse::from).toList());
    }

    @PutMapping("/{id}/users/{userId}")
    @Operation(summary = "Assign a user to this tenant",
            description = "Invalidates the user's existing sessions — the tenant claim is baked into the JWT")
    public ResponseEntity<TenantUserResponse> assignUser(
            @PathVariable Long id,
            @PathVariable Long userId) {
        String caller = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity assigned = tenantService.assignUser(userId, id, caller);
        return ResponseEntity.ok(TenantUserResponse.from(assigned));
    }
}
