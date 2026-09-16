package com.cqlplatform.config;

import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-223 — proves the V70 {@code tenant_isolation} policies on a real PostgreSQL:
 * a connection scoped to tenant A cannot read, update, delete or insert tenant B's rows
 * <b>even through queries that carry no tenant condition at all</b> — the exact failure
 * mode of BUG-131 ~ BUG-139.
 *
 * <p>Setup goes through the Spring DataSource (in CI that user is a superuser, which
 * bypasses policies — see {@link #superuserBypassesPolicies_whichIsWhyTheAppRoleExists});
 * the assertions go through a raw JDBC connection as a NOSUPERUSER / NOBYPASSRLS probe
 * role, i.e. the position the backend is in once {@code DB_APP_USERNAME} is configured.
 *
 * <p>Runs only with {@code CI_PG_TEST=true} (the {@code ci-pg} profile). H2 has no RLS.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CI_PG_TEST", matches = "true")
class TenantRlsIntegrationTest {

    private static final String PROBE_ROLE = "cql_rls_probe";

    @Autowired
    private DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    private JdbcTemplate jdbc;
    private final String probePassword = "probe-" + UUID.randomUUID();
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    private long tenantA, tenantB, measureA, measureB, reportA, reportB, popA, popB, testCaseA, testCaseB;

    @BeforeEach
    void seed() {
        jdbc = new JdbcTemplate(dataSource);
        TenantContext.runWithRlsBypass(() -> {
            String pw = probePassword.replace("'", "''");
            Integer exists = jdbc.query("SELECT 1 FROM pg_roles WHERE rolname = ?",
                    rs -> rs.next() ? 1 : 0, PROBE_ROLE);
            if (exists != null && exists == 1) {
                jdbc.execute("ALTER ROLE " + PROBE_ROLE + " WITH LOGIN PASSWORD '" + pw + "' NOSUPERUSER NOBYPASSRLS");
            } else {
                jdbc.execute("CREATE ROLE " + PROBE_ROLE + " LOGIN PASSWORD '" + pw
                        + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS");
            }
            // Same grants as docker/postgres-init/10-app-role.sh.
            jdbc.execute("GRANT USAGE ON SCHEMA public TO " + PROBE_ROLE);
            jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + PROBE_ROLE);
            jdbc.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO " + PROBE_ROLE);

            tenantA = insertTenant("rls-a-" + suffix);
            tenantB = insertTenant("rls-b-" + suffix);
            measureA = insertMeasure("rls-measure-a-" + suffix, tenantA);
            measureB = insertMeasure("rls-measure-b-" + suffix, tenantB);
            reportA = insertReport(measureA, "rls-measure-a-" + suffix, tenantA);
            reportB = insertReport(measureB, "rls-measure-b-" + suffix, tenantB);
            popA = insertPopulation(reportA);
            popB = insertPopulation(reportB);
            testCaseA = insertTestCase(measureA);
            testCaseB = insertTestCase(measureB);
            return null;
        });
    }

    @AfterEach
    void cleanUp() {
        TenantContext.runWithRlsBypass(() -> {
            jdbc.update("DELETE FROM measure_report WHERE id IN (?, ?)", reportA, reportB);      // cascades group / population
            jdbc.update("DELETE FROM measure_definition WHERE id IN (?, ?)", measureA, measureB); // cascades test_case
            jdbc.update("DELETE FROM tenant WHERE id IN (?, ?)", tenantA, tenantB);
            jdbc.execute("DROP OWNED BY " + PROBE_ROLE);
            jdbc.execute("DROP ROLE IF EXISTS " + PROBE_ROLE);
            return null;
        });
    }

    // ---------------------------------------------------------------- tests

    @Test
    void probeRole_isNeitherSuperuserNorBypassRls() throws SQLException {
        try (Connection c = probe(); PreparedStatement ps = c.prepareStatement(TenantRlsStartupCheck.ROLE_SQL);
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("rolsuper")).isFalse();
            assertThat(rs.getBoolean("rolbypassrls")).isFalse();
        }
    }

    @Test
    void tenantScopedConnection_seesOnlyItsOwnRows_evenWithoutATenantCondition() throws SQLException {
        try (Connection c = probe()) {
            scope(c, tenantA, false);

            // measure_report: own tenant_id column
            assertThat(ids(c, "SELECT id FROM measure_report WHERE id IN (?, ?)", reportA, reportB))
                    .containsExactly(reportA);
            // test_case: tenant inherited from the parent measure (one join)
            assertThat(ids(c, "SELECT id FROM test_case WHERE id IN (?, ?)", testCaseA, testCaseB))
                    .containsExactly(testCaseA);
            // measure_report_population: tenant inherited via group → report (two joins)
            assertThat(ids(c, "SELECT id FROM measure_report_population WHERE id IN (?, ?)", popA, popB))
                    .containsExactly(popA);

            scope(c, tenantB, false);
            assertThat(ids(c, "SELECT id FROM measure_report WHERE id IN (?, ?)", reportA, reportB))
                    .containsExactly(reportB);
        }
    }

    @Test
    void unscopedConnection_seesNothing_failClosed() throws SQLException {
        try (Connection c = probe()) {
            scope(c, null, false);
            assertThat(ids(c, "SELECT id FROM measure_report WHERE id IN (?, ?)", reportA, reportB)).isEmpty();
            assertThat(ids(c, "SELECT id FROM test_case WHERE id IN (?, ?)", testCaseA, testCaseB)).isEmpty();
            assertThat(ids(c, "SELECT id FROM measure_report_population WHERE id IN (?, ?)", popA, popB)).isEmpty();
        }
    }

    @Test
    void explicitBypass_seesEveryTenant() throws SQLException {
        try (Connection c = probe()) {
            scope(c, null, true);
            assertThat(ids(c, "SELECT id FROM measure_report WHERE id IN (?, ?)", reportA, reportB))
                    .containsExactlyInAnyOrder(reportA, reportB);
            assertThat(ids(c, "SELECT id FROM test_case WHERE id IN (?, ?)", testCaseA, testCaseB))
                    .containsExactlyInAnyOrder(testCaseA, testCaseB);
        }
    }

    @Test
    void tenantScopedConnection_cannotWriteIntoAnotherTenant() throws SQLException {
        try (Connection c = probe()) {
            scope(c, tenantA, false);

            // INSERT for tenant B is refused by WITH CHECK (both the direct and the joined policy).
            assertThatThrownBy(() -> execute(c,
                    "INSERT INTO measure_report (measure_definition_id, measure_name, period_start, period_end, result_json, tenant_id) "
                            + "VALUES (?, 'x', DATE '2026-01-01', DATE '2026-01-31', '{}', ?)", measureB, tenantB))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
            assertThatThrownBy(() -> execute(c,
                    "INSERT INTO test_case (measure_definition_id, title) VALUES (?, 'cross-tenant')", measureB))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");

            // UPDATE / DELETE on tenant B's rows simply touch nothing — the rows are invisible.
            assertThat(execute(c, "UPDATE measure_report SET measure_name = 'tampered' WHERE id = ?", reportB)).isZero();
            assertThat(execute(c, "DELETE FROM test_case WHERE id = ?", testCaseB)).isZero();

            // ...while the same statements on tenant A's own rows work.
            assertThat(execute(c, "UPDATE measure_report SET measure_name = measure_name WHERE id = ?", reportA)).isEqualTo(1);
        }
    }

    @Test
    void superuserBypassesPolicies_whichIsWhyTheAppRoleExists() {
        // Documents the trap TenantRlsStartupCheck guards against: the owner role the Docker
        // image creates (POSTGRES_USER) is a superuser, and superusers ignore every policy.
        Boolean superuser = jdbc.query("SELECT rolsuper FROM pg_roles WHERE rolname = current_user",
                rs -> rs.next() && rs.getBoolean(1));
        Assumptions.assumeTrue(Boolean.TRUE.equals(superuser), "setup user is not a superuser here");

        Long visible = TenantContext.callWith(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM measure_report WHERE id IN (?, ?)", Long.class, reportA, reportB));
        assertThat(visible).isEqualTo(2L);
    }

    // ---------------------------------------------------------------- helpers

    private long insertTenant(String code) {
        return jdbc.queryForObject(
                "INSERT INTO tenant (code, name, active) VALUES (?, ?, TRUE) RETURNING id", Long.class, code, code);
    }

    private long insertMeasure(String name, long tenantId) {
        return jdbc.queryForObject(
                "INSERT INTO measure_definition (name, version, tenant_id) VALUES (?, '1.0.0', ?) RETURNING id",
                Long.class, name, tenantId);
    }

    private long insertReport(long measureId, String name, long tenantId) {
        return jdbc.queryForObject(
                "INSERT INTO measure_report (measure_definition_id, measure_name, period_start, period_end, result_json, tenant_id) "
                        + "VALUES (?, ?, DATE '2026-01-01', DATE '2026-06-30', '{}', ?) RETURNING id",
                Long.class, measureId, name, tenantId);
    }

    private long insertPopulation(long reportId) {
        long groupId = jdbc.queryForObject(
                "INSERT INTO measure_report_group (measure_report_id, group_id) VALUES (?, 'g') RETURNING id",
                Long.class, reportId);
        return jdbc.queryForObject(
                "INSERT INTO measure_report_population (measure_report_group_id, population_type, population_id, count) "
                        + "VALUES (?, 'initial-population', 'ip', 1) RETURNING id",
                Long.class, groupId);
    }

    private long insertTestCase(long measureId) {
        return jdbc.queryForObject(
                "INSERT INTO test_case (measure_definition_id, title) VALUES (?, 'rls probe') RETURNING id",
                Long.class, measureId);
    }

    private Connection probe() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, PROBE_ROLE, probePassword);
    }

    /** Exactly what TenantAwareDataSource does on every checkout. */
    private static void scope(Connection c, Long tenantId, boolean bypass) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(TenantAwareDataSource.APPLY_SCOPE_SQL)) {
            ps.setString(1, tenantId == null ? "" : Long.toString(tenantId));
            ps.setString(2, bypass ? "on" : "off");
            ps.execute();
        }
    }

    private static java.util.List<Long> ids(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<Long> out = new java.util.ArrayList<>();
                while (rs.next()) {
                    out.add(rs.getLong(1));
                }
                return out;
            }
        }
    }

    private static int execute(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }
}
