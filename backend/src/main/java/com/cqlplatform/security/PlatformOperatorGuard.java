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
        if (!isPlatformOperator(TenantContext.getCurrentTenantId())) {
            throw new AccessDeniedException("This operation is restricted to the platform operator");
        }
    }

    /**
     * Whether a caller in the given tenant is the platform operator. A null tenant is a
     * legacy platform account (tenant_id NULL, resolves to the default tenant) — allowed.
     * Used both by {@link #require()} (from the request thread's TenantContext) and by
     * the login response, which computes the flag from {@code user.tenantId} directly
     * because TenantContext isn't set yet on the login path.
     */
    public boolean isPlatformOperator(Long tenantId) {
        if (tenantId == null) {
            return true;
        }
        Long defaultTenantId = tenantRepository.findByCode(DEFAULT_TENANT_CODE)
                .map(TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
        return tenantId.equals(defaultTenantId);
    }
}
