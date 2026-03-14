package com.cqlplatform.model.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank
    @Email
    @Size(max = 200)
    private String email;
}
