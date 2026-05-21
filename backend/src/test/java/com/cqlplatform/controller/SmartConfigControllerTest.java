package com.cqlplatform.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void smartConfiguration_shouldReturnConfigWithoutAuth() throws Exception {
        mockMvc.perform(get("/.well-known/smart-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities").isArray())
                .andExpect(jsonPath("$.grant_types_supported").isArray())
                .andExpect(jsonPath("$.scopes_supported").isArray());
    }

    @Test
    void smartConfiguration_shouldContainRequiredFields() throws Exception {
        mockMvc.perform(get("/.well-known/smart-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").exists())
                .andExpect(jsonPath("$.authorization_endpoint").exists())
                .andExpect(jsonPath("$.token_endpoint").exists());
    }
}
