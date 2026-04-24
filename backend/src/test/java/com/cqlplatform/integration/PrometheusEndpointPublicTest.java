package com.cqlplatform.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAT-113: legacy escape hatch — when {@code management.prometheus.public=true}
 * (dev / local smoke), anonymous access is explicitly allowed. Ops must never
 * set this in production but existing tooling (smoke scenarios, dev dashboards)
 * relies on the switch working so the flag stays.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.prometheus.public=true",
})
@DisplayName("Prometheus endpoint — legacy public=true (PAT-113)")
class PrometheusEndpointPublicTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/prometheus without auth → 200 when explicitly public")
    void allowsAnonymous() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}
