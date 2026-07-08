package com.cqlplatform.security;

/**
 * Per-request holder for the caller's tenant id. Populated by {@link JwtAuthenticationFilter}
 * from the JWT {@code tenant} claim and cleared at request end (ThreadLocal — must be cleared
 * to avoid leaking across pooled request threads).
 *
 * <p>The single source of "which tenant is calling" for row-level isolation (Phase 2).
 * Returns {@code null} when the caller has no tenant — e.g. a legacy user not yet assigned to
 * a tenant, or a non-JWT auth path (SSE ticket / API key). Enforcement code must treat a null
 * tenant explicitly rather than assuming a default.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenantId(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long getCurrentTenantId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
