package com.cqlplatform.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAT-113: when the operator forgets to set {@code METRICS_SCRAPE_PASSWORD} the
 * endpoint must reject every caller — not quietly fall back to anonymous access.
 * This is the fail-loud default that keeps a misconfigured deploy from leaking
 * JVM metrics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.prometheus.public=false",
        "management.prometheus.auth.username=",
        "management.prometheus.auth.password=",
})
@DisplayName("Prometheus endpoint — credentials unset (PAT-113)")
class PrometheusEndpointUnconfiguredTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/prometheus without auth → 401")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /actuator/prometheus with any basic-auth → 401 (no user exists)")
    void rejectsBasicAuth() throws Exception {
        // prometheus:ghost
        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Basic cHJvbWV0aGV1czpnaG9zdA=="))
                .andExpect(status().isUnauthorized());
    }
}
