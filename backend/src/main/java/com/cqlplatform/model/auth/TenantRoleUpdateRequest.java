package com.cqlplatform.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * A clinic ADMIN changing a staff member's role within their OWN tenant (PAT-214).
 * Allows all three roles (including {@code DEPARTMENT_ADMIN}); the service confines the
 * change to the caller's tenant.
 */
@Data
public class TenantRoleUpdateRequest {
    @NotBlank
    @Pattern(regexp = "ADMIN|USER|DEPARTMENT_ADMIN")
    private String role;
}
