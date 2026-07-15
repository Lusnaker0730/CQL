package com.cqlplatform.model.auth;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClinicApplicationRequest {

    @NotBlank
    @Size(max = 200)
    @NoXss
    private String clinicName;

    /** Requested tenant code — same slug rules as TenantCreateRequest. */
    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "tenantCode must be a lowercase slug (a-z, 0-9, -)")
    private String tenantCode;

    @NotBlank
    @Size(min = 3, max = 50)
    @NoXss
    private String adminUsername;

    @NotBlank
    @Email
    @Size(max = 200)
    private String adminEmail;
}
