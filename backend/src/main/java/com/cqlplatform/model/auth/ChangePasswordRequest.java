package com.cqlplatform.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank
    private String currentPassword;

    // PAT-157 — keep complexity rule in sync with RegisterRequest.password.
    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$",
            message = "must contain at least one lowercase letter, one uppercase letter, and one digit")
    private String newPassword;
}
