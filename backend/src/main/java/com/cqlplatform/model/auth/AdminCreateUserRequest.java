package com.cqlplatform.model.auth;

import com.cqlplatform.security.NoXss;
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

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @Size(max = 200)
    private String email;

    @NotNull
    @Pattern(regexp = "ADMIN|USER")
    private String role;
}
