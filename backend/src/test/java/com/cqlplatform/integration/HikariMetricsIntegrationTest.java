package com.cqlplatform.integration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-109: HikariCP pool metrics must be exposed on /actuator/prometheus so that
 * {@code hikaricp_connections_pending} and {@code hikaricp_connections_timeout_total}
 * can drive saturation alerts. Spring Boot's {@code DataSourcePoolMetricsAutoConfiguration}
 * wires these in automatically, but silently skips if the DataSource is wrapped or
 * the HikariDataSource class is not detected — hence an integration-level lock rather
 * than trusting framework behavior.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("HikariCP Micrometer metrics — auto-registered via Spring Boot")
class HikariMetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("connection-pool gauges are registered and readable")
    void hikariGaugesRegistered() {
        // These are the metric names emitted by Micrometer's HikariCPMetrics binder.
        // If any of these disappear, Prometheus alerts based on them will silently
        // evaluate to zero and never fire.
        assertGaugePresent("hikaricp.connections");
        assertGaugePresent("hikaricp.connections.active");
        assertGaugePresent("hikaricp.connections.idle");
        assertGaugePresent("hikaricp.connections.pending");
        assertGaugePresent("hikaricp.connections.max");
        assertGaugePresent("hikaricp.connections.min");
    }

    @Test
    @DisplayName("pool is tagged with the configured pool-name")
    void hikariPoolNameTagged() {
        Gauge gauge = meterRegistry.find("hikaricp.connections").gauge();
        assertThat(gauge).isNotNull();
        // Pool name comes from application.yml `spring.datasource.hikari.pool-name`
        // (CqlPlatformPool in main, may differ in test profile). Just assert a non-empty
        // pool tag exists so multi-pool deployments remain distinguishable.
        assertThat(gauge.getId().getTag("pool"))
                .as("hikaricp gauge must carry a 'pool' tag so metrics are distinguishable per-pool")
                .isNotBlank();
    }

    private void assertGaugePresent(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        assertThat(gauge)
                .as("Micrometer gauge '%s' must be registered", name)
                .isNotNull();
    }
}
