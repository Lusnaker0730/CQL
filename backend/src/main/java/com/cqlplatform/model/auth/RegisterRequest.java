package com.cqlplatform.model.auth;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    @NoXss
    private String username;

    /**
     * PAT-157 — minimum complexity: 8–100 chars containing at least one
     * lowercase letter, one uppercase letter, and one digit. The legacy
     * "size only" rule waved through {@code 12345678} / {@code password1}
     * which a credential-stuffing attacker can guess in seconds.
     */
    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$",
            message = "must contain at least one lowercase letter, one uppercase letter, and one digit")
    private String password;

    @Email
    @Size(max = 200)
    private String email;
}
