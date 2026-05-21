package com.cqlplatform.controller;

import com.cqlplatform.service.fhir.VsacService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VsacService vsacService;

    @Test
    void getVsacStatus_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/settings/vsac-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "regular", roles = {"USER"})
    void getVsacStatus_regularUserAuthenticated_shouldReturn403() throws Exception {
        // PAT-145 regression: settings GET endpoints leak the configured VSAC URL
        // and AI provider/model — operator-only info, not for regular users.
        // Class-level @PreAuthorize("hasRole('ADMIN')") closes this gap; this test
        // locks the rule against future weakening.
        mockMvc.perform(get("/api/settings/vsac-status"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getVsacStatus_configured_shouldReturnStatus() throws Exception {
        when(vsacService.isConfigured()).thenReturn(true);
        when(vsacService.getApiUrl()).thenReturn("https://vsac.nlm.nih.gov/vsac/svs/RetrieveMultipleValueSets");

        mockMvc.perform(get("/api/settings/vsac-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.url").value("https://vsac.nlm.nih.gov/vsac/svs/RetrieveMultipleValueSets"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getVsacStatus_notConfigured_shouldReturnFalse() throws Exception {
        when(vsacService.isConfigured()).thenReturn(false);
        when(vsacService.getApiUrl()).thenReturn("");

        mockMvc.perform(get("/api/settings/vsac-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateVsacApiKey_shouldUpdateAndReturnStatus() throws Exception {
        doNothing().when(vsacService).updateApiKey("new-api-key");
        when(vsacService.isConfigured()).thenReturn(true);

        mockMvc.perform(put("/api/settings/vsac-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"new-api-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.message").value("VSAC API key updated"));
    }
}
