package com.cqlplatform.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PAT-209 — the status endpoint is public (no auth) and reports coarse reachability. Under the
 * test profile the H2 datasource is reachable, so overall status is "operational".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void status_isPublic_andReportsOperationalWhenDbReachable() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("operational"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.components[?(@.name=='database')].ok").value(true))
                .andExpect(jsonPath("$.components[?(@.name=='api')].ok").value(true));
    }
}
