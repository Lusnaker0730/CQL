package com.cqlplatform.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PAT-223 — verifies at boot that Row-Level Security is actually enforced for the role the
 * application connects with, and exposes the answer as the {@code tenant_rls_effective} gauge
 * (1 = enforced, 0 = not) for Prometheus.
 *
 * <p>Why this exists: a PostgreSQL <b>superuser</b> or a role with <b>BYPASSRLS</b> ignores
 * every policy silently. The Docker stack's {@code POSTGRES_USER} is a superuser, so a
 * deployment that has not switched the backend to the least-privilege app role
 * ({@code DB_APP_USERNAME} / {@code DB_APP_PASSWORD}, created by
 * {@code docker/postgres-init/10-app-role.sh}) would believe it has RLS while having none.
 * Loud ERROR by default; {@code TENANT_RLS_STRICT=true} refuses to start instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantRlsStartupCheck {

    static final String ROLE_SQL = "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user";
    static final String POLICIES_SQL = "SELECT tablename FROM pg_policies WHERE policyname = 'tenant_isolation' ORDER BY tablename";

    private final DataSource dataSource;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    @Value("${app.tenant-rls.strict:false}")
    private boolean strict;

    private final AtomicInteger effective = new AtomicInteger(0);

    @PostConstruct
    void registerGauge() {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry != null) {
            Gauge.builder("tenant_rls_effective", effective, AtomicInteger::get)
                    .description("1 when PostgreSQL row-level security is enforced for the application role, 0 otherwise")
                    .register(registry);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        try (Connection c = dataSource.getConnection()) {
            String url = c.getMetaData().getURL();
            if (url == null || !url.startsWith("jdbc:postgresql:")) {
                log.info("Tenant RLS check skipped: not PostgreSQL ({})", url);
                return;
            }
            String role = null;
            boolean superuser = false;
            boolean bypassRls = false;
            try (PreparedStatement ps = c.prepareStatement(ROLE_SQL); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    role = rs.getString(1);
                    superuser = rs.getBoolean(2);
                    bypassRls = rs.getBoolean(3);
                }
            }
            List<String> tables = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(POLICIES_SQL); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            boolean enforced = !superuser && !bypassRls && !tables.isEmpty();
            effective.set(enforced ? 1 : 0);
            if (enforced) {
                log.info("Tenant RLS ENFORCED for role '{}' on {} tables: {}", role, tables.size(), tables);
                return;
            }
            String why = tables.isEmpty()
                    ? "no tenant_isolation policies found (V70 not applied?)"
                    : String.format("role '%s' has superuser=%s bypassrls=%s", role, superuser, bypassRls);
            String message = "Tenant RLS is NOT enforced: " + why
                    + ". Policies exist on " + tables.size() + " tables. Connect the backend as a NOSUPERUSER "
                    + "NOBYPASSRLS role (DB_APP_USERNAME / DB_APP_PASSWORD, see docker/postgres-init/10-app-role.sh"
                    + " and DEPLOYMENT_GUIDE §3.6). Isolation currently rests on repository conditions only.";
            if (strict) {
                throw new IllegalStateException(message + " (app.tenant-rls.strict=true)");
            }
            log.error(message);
        } catch (SQLException e) {
            effective.set(0);
            if (strict) {
                throw new IllegalStateException("Tenant RLS check failed: " + e.getMessage(), e);
            }
            log.error("Tenant RLS check failed: {}", e.getMessage(), e);
        }
    }
}
