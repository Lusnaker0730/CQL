package com.cqlplatform.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserSummary {
    private Long id;
    private String username;
    private String email;
    private String role;
    private Boolean enabled;
    private Boolean forcePasswordChange;
    private String createdAt;
}
