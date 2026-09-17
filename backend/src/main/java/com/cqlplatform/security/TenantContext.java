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
 *
 * <p>PAT-223: the same ThreadLocal now also feeds PostgreSQL Row-Level Security.
 * {@code TenantAwareDataSource} copies the current tenant (or the default tenant when none
 * is set, mirroring every service's {@code effectiveTenantId()}) into the session variable
 * {@code app.tenant_id} on every connection checkout, so the database itself only exposes
 * that tenant's rows. {@link #runWithRlsBypass} is the explicit escape hatch for system
 * work that legitimately spans tenants (startup backfill, platform-operator maintenance).
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RLS_BYPASS = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenantId(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long getCurrentTenantId() {
        return CURRENT.get();
    }

    /** Clears BOTH the tenant and any RLS bypass flag — call at the end of every request. */
    public static void clear() {
        CURRENT.remove();
        RLS_BYPASS.remove();
    }

    /**
     * Run {@code action} with {@code tenantId} set in the context, restoring the previous
     * value afterwards. Used to propagate the caller's tenant onto async executor threads
     * (e.g. parallel measure evaluation) where the request-thread ThreadLocal isn't visible.
     */
    public static <T> T callWith(Long tenantId, java.util.function.Supplier<T> action) {
        Long previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    /** True while inside {@link #runWithRlsBypass}: connections are scoped with {@code app.rls_bypass = on}. */
    public static boolean isRlsBypass() {
        return Boolean.TRUE.equals(RLS_BYPASS.get());
    }

    /**
     * PAT-223: run {@code action} with Row-Level Security bypassed for connections checked
     * out on this thread. This is the ONLY way to read or write rows across tenants, so keep
     * the scope as small as possible and never use it on a request thread on behalf of a
     * caller — it is for system work (startup backfill, migrations, platform maintenance)
     * that has no single tenant. Nested calls are safe; the previous flag is restored.
     */
    public static <T> T runWithRlsBypass(java.util.function.Supplier<T> action) {
        Boolean previous = RLS_BYPASS.get();
        RLS_BYPASS.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (previous != null) {
                RLS_BYPASS.set(previous);
            } else {
                RLS_BYPASS.remove();
            }
        }
    }
}
