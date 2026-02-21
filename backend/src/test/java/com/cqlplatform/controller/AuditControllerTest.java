package com.cqlplatform.controller;

import com.cqlplatform.model.audit.AuditLogEntry;
import com.cqlplatform.model.audit.AuditLogResponse;
import com.cqlplatform.model.audit.AuditStatsResponse;
import com.cqlplatform.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    private AuditLogResponse createLogResponse() {
        AuditLogEntry entry = AuditLogEntry.builder()
                .id(1L)
                .username("testuser")
                .method("GET")
                .path("/api/cql/libraries")
                .action("READ")
                .statusCode(200)
                .build();

        return AuditLogResponse.builder()
                .content(List.of(entry))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();
    }

    @Test
    void getLogs_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/audit/logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getLogs_shouldReturnAuditLogs() throws Exception {
        when(auditService.searchLogs(any())).thenReturn(createLogResponse());

        mockMvc.perform(get("/api/admin/audit/logs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("testuser"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getLogs_withFilters_shouldPassFilters() throws Exception {
        when(auditService.searchLogs(any())).thenReturn(createLogResponse());

        mockMvc.perform(get("/api/admin/audit/logs")
                        .param("username", "testuser")
                        .param("action", "READ")
                        .param("resourceType", "CqlLibrary"))
                .andExpect(status().isOk());

        verify(auditService).searchLogs(argThat(req ->
                "testuser".equals(req.getUsername()) &&
                "READ".equals(req.getAction()) &&
                "CqlLibrary".equals(req.getResourceType())));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void exportLogs_shouldReturnCsv() throws Exception {
        AuditLogEntry entry = AuditLogEntry.builder()
                .id(1L)
                .username("testuser")
                .method("GET")
                .path("/api/cql/libraries")
                .action("READ")
                .statusCode(200)
                .build();

        when(auditService.exportLogs(any())).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/admin/audit/logs/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=audit_logs.csv"))
                .andExpect(content().contentType("text/csv"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getStats_shouldReturnStats() throws Exception {
        AuditStatsResponse stats = AuditStatsResponse.builder()
                .totalEventsToday(50)
                .totalEventsWeek(300)
                .totalEventsMonth(1200)
                .phiAccessCount(10)
                .failedLoginAttempts(3)
                .activeUsersToday(5)
                .actionCounts(Map.of("READ", 100L, "WRITE", 50L))
                .topUsers(List.of())
                .dailyActivity(List.of())
                .build();

        when(auditService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/admin/audit/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEventsToday").value(50))
                .andExpect(jsonPath("$.failedLoginAttempts").value(3));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getPhiAccess_shouldReturnLogs() throws Exception {
        when(auditService.getPhiAccessLog(anyInt(), anyInt(), any())).thenReturn(createLogResponse());

        mockMvc.perform(get("/api/admin/audit/phi-access")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getLoginActivity_shouldReturnLogs() throws Exception {
        when(auditService.getLoginActivity(anyInt(), anyInt(), any())).thenReturn(createLogResponse());

        mockMvc.perform(get("/api/admin/audit/login-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getSecurityEvents_shouldReturnLogs() throws Exception {
        when(auditService.getSecurityEvents(anyInt(), anyInt(), any())).thenReturn(createLogResponse());

        mockMvc.perform(get("/api/admin/audit/security-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
