package com.cqlplatform.config;

import com.cqlplatform.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PAT-223 — scopes every PostgreSQL connection checkout to the caller's tenant so the
 * {@code tenant_isolation} Row-Level Security policies (V70) can enforce isolation below
 * the hand-written repository conditions.
 *
 * <p>On each {@link #getConnection()} it runs
 * {@code SELECT set_config('app.tenant_id', ?, false), set_config('app.rls_bypass', ?, false)}
 * with values taken from {@link TenantContext}:
 * <ul>
 *   <li>tenant present → that id;</li>
 *   <li>tenant absent and no bypass → the {@code default} tenant's id (what every service's
 *       {@code effectiveTenantId()} already does for legacy / null-tenant callers), or {@code ''}
 *       if that tenant does not exist — which makes {@code app_current_tenant()} NULL and hides
 *       every row (fail-closed);</li>
 *   <li>{@link TenantContext#isRlsBypass()} → {@code app.rls_bypass = 'on'}.</li>
 * </ul>
 * Session-level ({@code is_local = false}) on purpose: the values survive across the
 * transaction boundary Spring opens after checkout, and because EVERY checkout overwrites
 * both variables a pooled connection can never carry a previous caller's scope.
 *
 * <p>Only wrapped for PostgreSQL URLs (see {@link TenantRlsDataSourceConfig}); H2 tests are
 * untouched. Flyway uses its own DataSource ({@code spring.flyway.user} = the table owner)
 * and is not wrapped.
 */
@Slf4j
public class TenantAwareDataSource extends DelegatingDataSource {

    static final String APPLY_SCOPE_SQL =
            "SELECT set_config('app.tenant_id', ?, false), set_config('app.rls_bypass', ?, false)";
    static final String DEFAULT_TENANT_SQL = "SELECT id FROM tenant WHERE code = 'default'";

    private volatile Long cachedDefaultTenantId;

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyScope(obtainTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyScope(obtainTargetDataSource().getConnection(username, password));
    }

    private Connection applyScope(Connection connection) throws SQLException {
        Long tenantId = TenantContext.getCurrentTenantId();
        boolean bypass = TenantContext.isRlsBypass();
        try {
            if (tenantId == null && !bypass) {
                tenantId = defaultTenantId(connection);
            }
            try (PreparedStatement ps = connection.prepareStatement(APPLY_SCOPE_SQL)) {
                ps.setString(1, tenantId == null ? "" : Long.toString(tenantId));
                ps.setString(2, bypass ? "on" : "off");
                ps.execute();
            }
            return connection;
        } catch (SQLException e) {
            // Never hand out an unscoped connection: close it and surface the failure.
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private Long defaultTenantId(Connection connection) throws SQLException {
        Long cached = cachedDefaultTenantId;
        if (cached != null) {
            return cached;
        }
        try (PreparedStatement ps = connection.prepareStatement(DEFAULT_TENANT_SQL);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                cached = rs.getLong(1);
                cachedDefaultTenantId = cached;
                return cached;
            }
        }
        // Before V57 has seeded the default tenant (first boot) there is nothing to fall back
        // to; the scope is left empty and RLS-protected tables read as empty until it exists.
        log.warn("No 'default' tenant row found — connections without a tenant are scoped to nothing");
        return null;
    }
}
