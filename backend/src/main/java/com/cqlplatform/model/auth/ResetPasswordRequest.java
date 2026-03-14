package com.cqlplatform.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    @Size(max = 256)
    private String token;

    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}
