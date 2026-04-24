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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAT-113: {@code /actuator/prometheus} must require HTTP Basic when
 * {@code management.prometheus.public=false} (the default), and should NOT
 * accept any request when the credentials are blank. This class covers the
 * "configured" happy path — the unconfigured and legacy-public modes live in
 * {@link PrometheusEndpointUnconfiguredTest} and {@link PrometheusEndpointPublicTest}
 * because they need separate Spring contexts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.prometheus.public=false",
        "management.prometheus.auth.username=prometheus",
        "management.prometheus.auth.password=test-scrape-secret-12345",
})
@DisplayName("Prometheus endpoint — Basic auth configured (PAT-113)")
class PrometheusEndpointAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("correct basic auth → 200 + Micrometer metrics body")
    void acceptsCorrectBasicAuth() throws Exception {
        String header = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("prometheus:test-scrape-secret-12345".getBytes());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", header))
                .andExpect(status().isOk())
                // Micrometer always emits jvm_* metrics — cheap proof the body is a
                // real scrape payload rather than a login redirect.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_")));
    }

    @Test
    @DisplayName("wrong password → 401")
    void rejectsWrongPassword() throws Exception {
        String header = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("prometheus:wrong".getBytes());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", header))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("no auth header → 401 (never anonymous)")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }
}
