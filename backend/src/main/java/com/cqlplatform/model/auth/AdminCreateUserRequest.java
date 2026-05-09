package com.cqlplatform.model.auth;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCreateUserRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    @NoXss
    private String username;

    // PAT-157 — keep complexity rule in sync with RegisterRequest.password.
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
    @Pattern(regexp = "ADMIN|USER")
    private String role;
}
