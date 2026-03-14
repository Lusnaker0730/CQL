package com.cqlplatform.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RoleUpdateRequest {
    @NotBlank
    @Pattern(regexp = "ADMIN|USER")
    private String role;
}
