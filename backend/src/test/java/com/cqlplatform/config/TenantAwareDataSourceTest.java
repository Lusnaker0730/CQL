package com.cqlplatform.config;

import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-223 — the connection-scoping contract of {@link TenantAwareDataSource}: every checkout
 * sets {@code app.tenant_id} / {@code app.rls_bypass} from {@link TenantContext}, a missing
 * tenant falls back to the default tenant, bypass is explicit, and a connection whose scope
 * could not be applied is never handed out.
 */
@ExtendWith(MockitoExtension.class)
class TenantAwareDataSourceTest {

    @Mock private DataSource target;
    @Mock private Connection connection;
    @Mock private PreparedStatement scopeStatement;
    @Mock private PreparedStatement defaultTenantStatement;
    @Mock private ResultSet defaultTenantRows;

    private TenantAwareDataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new TenantAwareDataSource(target);
        // lenient: the pure-ThreadLocal test below never touches the DataSource.
        org.mockito.Mockito.lenient().when(target.getConnection()).thenReturn(connection);
        org.mockito.Mockito.lenient().when(connection.prepareStatement(TenantAwareDataSource.APPLY_SCOPE_SQL)).thenReturn(scopeStatement);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void checkout_withTenantInContext_scopesConnectionToThatTenant() throws SQLException {
        TenantContext.setCurrentTenantId(42L);

        Connection c = dataSource.getConnection();

        assertThat(c).isSameAs(connection);
        verify(scopeStatement).setString(1, "42");
        verify(scopeStatement).setString(2, "off");
        verify(scopeStatement).execute();
        verify(connection, never()).prepareStatement(TenantAwareDataSource.DEFAULT_TENANT_SQL);
    }

    @Test
    void checkout_withoutTenant_fallsBackToDefaultTenantAndCachesIt() throws SQLException {
        when(connection.prepareStatement(TenantAwareDataSource.DEFAULT_TENANT_SQL)).thenReturn(defaultTenantStatement);
        when(defaultTenantStatement.executeQuery()).thenReturn(defaultTenantRows);
        when(defaultTenantRows.next()).thenReturn(true);
        when(defaultTenantRows.getLong(1)).thenReturn(1L);

        dataSource.getConnection();
        dataSource.getConnection();   // second checkout must reuse the cached id

        verify(connection, org.mockito.Mockito.times(1)).prepareStatement(TenantAwareDataSource.DEFAULT_TENANT_SQL);
        verify(scopeStatement, org.mockito.Mockito.times(2)).setString(1, "1");
        verify(scopeStatement, org.mockito.Mockito.times(2)).setString(2, "off");
    }

    @Test
    void checkout_withoutTenantAndNoDefaultRow_scopesToNothing() throws SQLException {
        when(connection.prepareStatement(TenantAwareDataSource.DEFAULT_TENANT_SQL)).thenReturn(defaultTenantStatement);
        when(defaultTenantStatement.executeQuery()).thenReturn(defaultTenantRows);
        when(defaultTenantRows.next()).thenReturn(false);

        dataSource.getConnection();

        // '' → app_current_tenant() IS NULL → no row matches: fail-closed, not fail-open.
        verify(scopeStatement).setString(1, "");
        verify(scopeStatement).setString(2, "off");
    }

    @Test
    void checkout_insideRlsBypass_setsBypassOnAndSkipsDefaultTenantLookup() throws SQLException {
        TenantContext.runWithRlsBypass(() -> {
            try {
                dataSource.getConnection();
            } catch (SQLException e) {
                throw new AssertionError(e);
            }
            return null;
        });

        verify(scopeStatement).setString(1, "");
        verify(scopeStatement).setString(2, "on");
        verify(connection, never()).prepareStatement(TenantAwareDataSource.DEFAULT_TENANT_SQL);
    }

    @Test
    void bypassFlag_isRestoredAfterTheScope() {
        assertThat(TenantContext.isRlsBypass()).isFalse();
        TenantContext.runWithRlsBypass(() -> {
            assertThat(TenantContext.isRlsBypass()).isTrue();
            return null;
        });
        assertThat(TenantContext.isRlsBypass()).isFalse();
    }

    @Test
    void checkout_whenScopingFails_closesTheConnectionAndPropagates() throws SQLException {
        TenantContext.setCurrentTenantId(7L);
        when(scopeStatement.execute()).thenThrow(new SQLException("set_config failed"));

        assertThatThrownBy(() -> dataSource.getConnection())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("set_config failed");

        // An unscoped connection must never reach the caller.
        var order = inOrder(scopeStatement, connection);
        order.verify(scopeStatement).execute();
        order.verify(connection).close();
    }
}
