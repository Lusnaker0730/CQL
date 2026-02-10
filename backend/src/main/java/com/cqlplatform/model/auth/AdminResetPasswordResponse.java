package com.cqlplatform.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AdminResetPasswordResponse {
    private String temporaryPassword;
    private String username;
    private String message;
}
