package com.cqlplatform.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for an admin-initiated password reset.
 * The temporary password is intentionally excluded from this response (security fix H8):
 * it is delivered to the user's registered email address instead.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminResetPasswordResponse {
    private String username;
    private String message;
}
