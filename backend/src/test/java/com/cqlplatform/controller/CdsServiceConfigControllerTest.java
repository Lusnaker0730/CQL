package com.cqlplatform.controller;

import com.cqlplatform.model.cds.CdsServiceAnalyticsDTO;
import com.cqlplatform.model.cds.CdsServiceConfigResponse;
import com.cqlplatform.service.cds.CdsAnalyticsService;
import com.cqlplatform.service.cds.CdsHooksService;
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

    @MockBean
    private CdsAnalyticsService analyticsService;

    private static final String CREATE_REQUEST = """
            {"id":"test-svc","hook":"patient-view","title":"Test Service","description":"desc","cqlContent":"library T version '1.0'"}
            """;

    @Test
    @WithMockUser(username = "testuser")
    void getAllServices_shouldReturnList() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").hook("patient-view").title("Service 1").enabled(true)
                .ownerUsername("testuser").build();
        when(cdsHooksService.getServicesForUser("testuser")).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/cds/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("svc-1"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllServices_admin_shouldReturnAll() throws Exception {
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
    @WithMockUser(username = "testuser")
    void createService_shouldReturn201() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("test-svc").hook("patient-view").title("Test Service")
                .ownerUsername("testuser").build();
        when(cdsHooksService.createService(any(), eq("testuser"))).thenReturn(resp);

        mockMvc.perform(post("/api/cds/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-svc"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createService_duplicate_shouldReturn400() throws Exception {
        when(cdsHooksService.createService(any(), eq("testuser")))
                .thenThrow(new IllegalArgumentException("Already exists"));

        mockMvc.perform(post("/api/cds/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateService_shouldReturn200() throws Exception {
        // PAT-138: ownership now enforced inside the service via
        // updateServiceIfOwnedBy → returns response on success.
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").hook("patient-view").title("Updated").build();
        when(cdsHooksService.updateServiceIfOwnedBy(eq("svc-1"), any(), eq("testuser"), eq(false)))
                .thenReturn(resp);

        mockMvc.perform(put("/api/cds/services/svc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateService_notOwner_shouldReturn403() throws Exception {
        // Service throws AccessDeniedException → mapped to 403 by GlobalExceptionHandler.
        when(cdsHooksService.updateServiceIfOwnedBy(eq("svc-1"), any(), eq("testuser"), eq(false)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException(
                        "You can only modify your own services"));

        mockMvc.perform(put("/api/cds/services/svc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteService_admin_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/cds/services/svc-1"))
                .andExpect(status().isNoContent());

        verify(cdsHooksService).deleteServiceIfOwnedBy("svc-1", "admin", true);
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void deleteService_owner_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/cds/services/svc-1"))
                .andExpect(status().isNoContent());

        verify(cdsHooksService).deleteServiceIfOwnedBy("svc-1", "testuser", false);
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void deleteService_notOwner_shouldReturn403() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException(
                "You can only delete your own services"))
                .when(cdsHooksService).deleteServiceIfOwnedBy("svc-1", "testuser", false);

        mockMvc.perform(delete("/api/cds/services/svc-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void enableService_shouldReturn200() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").enabled(true).build();
        when(cdsHooksService.toggleServiceEnabledIfOwnedBy(eq("svc-1"), eq(true), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/cds/services/svc-1/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @WithMockUser
    void disableService_shouldReturn200() throws Exception {
        CdsServiceConfigResponse resp = CdsServiceConfigResponse.builder()
                .id("svc-1").enabled(false).build();
        when(cdsHooksService.toggleServiceEnabledIfOwnedBy(eq("svc-1"), eq(false), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(resp);

        mockMvc.perform(patch("/api/cds/services/svc-1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createService_invalidHookType_shouldReturn400() throws Exception {
        when(cdsHooksService.createService(any(), eq("testuser")))
                .thenThrow(new IllegalArgumentException("Invalid hook type: 'bad-hook'"));

        String badRequest = """
                {"id":"bad-svc","hook":"bad-hook","title":"Bad Service"}
                """;

        mockMvc.perform(post("/api/cds/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser
    void getAnalytics_shouldReturnData() throws Exception {
        CdsServiceAnalyticsDTO dto = CdsServiceAnalyticsDTO.builder()
                .serviceId("svc-1").invocationCount(10).errorCount(2)
                .avgResponseTimeMs(150.0).errorRate(20.0)
                .feedbackAcceptedCount(5).feedbackOverriddenCount(1)
                .build();
        when(analyticsService.getAllServiceAnalytics()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/cds/services/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceId").value("svc-1"))
                .andExpect(jsonPath("$[0].invocationCount").value(10));
    }
}
