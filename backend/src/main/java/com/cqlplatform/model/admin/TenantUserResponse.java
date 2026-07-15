package com.cqlplatform.model.admin;

import com.cqlplatform.entity.UserEntity;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantUserResponse {

    private Long id;
    private String username;
    private String role;
    private Boolean enabled;
    /** Null for legacy default-tenant users whose tenant_id was never assigned. */
    private Long tenantId;

    public static TenantUserResponse from(UserEntity user) {
        return TenantUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .enabled(user.getEnabled())
                .tenantId(user.getTenantId())
                .build();
    }
}
