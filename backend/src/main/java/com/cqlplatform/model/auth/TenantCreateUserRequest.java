package com.cqlplatform.model.auth;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A clinic ADMIN creating a staff account within their OWN tenant (PAT-214).
 *
 * <p>Unlike {@link AdminCreateUserRequest} (platform-operator, {@code ADMIN|USER}), a tenant
 * admin may assign any of the three roles WITHIN their tenant, so this allows
 * {@code DEPARTMENT_ADMIN} too. The service always stamps the caller's tenant id, so this
 * DTO carries no tenant field — a clinic admin can never target another tenant.
 */
@Data
public class TenantCreateUserRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    @NoXss
    private String username;

    // Keep the complexity rule in sync with RegisterRequest / AdminCreateUserRequest.
    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$",
            message = "must contain at least one lowercase letter, one uppercase letter, and one digit")
    private String password;

    @Email
    @Size(max = 200)
    private String email;

    @NotNull
    @Pattern(regexp = "ADMIN|USER|DEPARTMENT_ADMIN")
    private String role;
}
