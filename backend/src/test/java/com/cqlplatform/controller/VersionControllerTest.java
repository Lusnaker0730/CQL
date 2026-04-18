package com.cqlplatform.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * /api/version must be reachable without auth — the SPA polls it from the login
     * screen, during token refresh races, and on intervals where the session may
     * have briefly expired. If this becomes authenticated, the cache-bust signal
     * dies silently for anonymous / refreshing clients.
     */
    @Test
    void getVersion_shouldReturnStartupFields_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startupTime").isNotEmpty())
                .andExpect(jsonPath("$.commitSha").isNotEmpty())
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void getVersion_startupTimeShouldBeStableWithinSameBootedInstance() throws Exception {
        // Two requests to the same running backend must return identical startupTime —
        // the frontend's change detection depends on this being a per-process constant.
        String first = mockMvc.perform(get("/api/version"))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get("/api/version"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(second);
    }
}
