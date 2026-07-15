package com.cqlplatform.security;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Platform-operator boundary (transition model, PAT-201/#699): until a dedicated
 * SUPER_ADMIN role exists, the platform operator is an ADMIN whose own tenant is the
 * DEFAULT tenant. Controllers enforce {@code hasRole('ADMIN')}; this guard enforces
 * the tenant half — a clinic tenant's ADMIN gets 403 on platform-level operations
 * (tenant lifecycle, clinic applications).
 */
@Component
@RequiredArgsConstructor
public class PlatformOperatorGuard {

    public static final String DEFAULT_TENANT_CODE = "default";

    private final TenantRepository tenantRepository;

    public void require() {
        Long defaultTenantId = tenantRepository.findByCode(DEFAULT_TENANT_CODE)
                .map(TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
        Long callerTenant = TenantContext.getCurrentTenantId();
        // A null claim is a legacy platform account (tenant_id NULL) — resolves to default.
        if (callerTenant == null || callerTenant.equals(defaultTenantId)) {
            return;
        }
        throw new AccessDeniedException("This operation is restricted to the platform operator");
    }
}
