package com.cqlplatform.controller;

import com.cqlplatform.model.cds.CdsServiceConfigResponse;
import com.cqlplatform.service.cds.CdsHooksService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CdsServiceConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CdsHooksService cdsHooksService;

    private static final String CREATE_REQUEST = """
            {"id":"test-svc","hook":"patient-view","title":"Test Service","description":"desc","cqlContent":"library T version '1.0'"}
            """;

    @Test
    @WithMockUser
    void getAllServices_shouldReturnList() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").hook("patient-view").title("Service 1").enabled(true).build();
        when(cdsHooksService.getAllServices()).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/cds/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("svc-1"));
    }

    @Test
    @WithMockUser
    void getService_existing_shouldReturn200() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").hook("patient-view").title("Service 1").build();
        when(cdsHooksService.getService("svc-1")).thenReturn(resp);

        mockMvc.perform(get("/api/cds/services/svc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("svc-1"));
    }

    @Test
    @WithMockUser
    void getService_notFound_shouldReturn404() throws Exception {
        when(cdsHooksService.getService("nonexistent"))
                .thenThrow(new IllegalArgumentException("Not found"));

        mockMvc.perform(get("/api/cds/services/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createService_shouldReturn201() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("test-svc").hook("patient-view").title("Test Service").build();
        when(cdsHooksService.createService(any())).thenReturn(resp);

        mockMvc.perform(post("/api/cds/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-svc"));
    }

    @Test
    @WithMockUser
    void createService_duplicate_shouldReturn400() throws Exception {
        when(cdsHooksService.createService(any()))
                .thenThrow(new IllegalArgumentException("Already exists"));

        mockMvc.perform(post("/api/cds/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void updateService_shouldReturn200() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").hook("patient-view").title("Updated").build();
        when(cdsHooksService.updateService(eq("svc-1"), any())).thenReturn(resp);

        mockMvc.perform(put("/api/cds/services/svc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteService_admin_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/cds/services/svc-1"))
                .andExpect(status().isNoContent());

        verify(cdsHooksService).deleteService("svc-1");
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteService_userRole_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/cds/services/svc-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void enableService_shouldReturn200() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").enabled(true).build();
        when(cdsHooksService.toggleServiceEnabled("svc-1", true)).thenReturn(resp);

        mockMvc.perform(patch("/api/cds/services/svc-1/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @WithMockUser
    void disableService_shouldReturn200() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").enabled(false).build();
        when(cdsHooksService.toggleServiceEnabled("svc-1", false)).thenReturn(resp);

        mockMvc.perform(patch("/api/cds/services/svc-1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
