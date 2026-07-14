package com.cqlplatform.service;

import com.cqlplatform.entity.AuditLogEntity;
import com.cqlplatform.model.audit.*;
import com.cqlplatform.repository.AuditLogRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private TenantRepository tenantRepository;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @InjectMocks
    private AuditService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(7L);
        ReflectionTestUtils.setField(service, "retentionDays", 365);
    }

    private AuditLogEntity createLogEntry(String username, String action) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(1L);
        entity.setUsername(username);
        entity.setAction(action);
        entity.setMethod("GET");
        entity.setPath("/api/test");
        entity.setStatusCode(200);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    // ===== searchLogs =====

    @Test
    @SuppressWarnings("unchecked")
    void searchLogs_shouldReturnPaginatedResponse() {
        AuditLogEntity log1 = createLogEntry("user1", "VIEW");
        Page<AuditLogEntity> page = new PageImpl<>(List.of(log1));

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        AuditLogSearchRequest request = new AuditLogSearchRequest();
        request.setPage(0);
        request.setSize(20);

        AuditLogResponse result = service.searchLogs(request);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("user1");
    }

    // ===== getStats =====

    @Test
    void getStats_shouldReturnAggregatedStats() {
        when(auditLogRepository.countByTenantIdAndCreatedAtAfter(eq(7L), any())).thenReturn(10L);
        when(auditLogRepository.countPhiAccess(eq(7L), any())).thenReturn(2L);
        when(auditLogRepository.countFailedLoginAttempts(eq(7L), any())).thenReturn(1L);
        when(auditLogRepository.countDistinctUsernameByCreatedAtAfter(eq(7L), any())).thenReturn(5L);
        when(auditLogRepository.countByActionGrouped(eq(7L), any())).thenReturn(List.of());
        when(auditLogRepository.findTopUsers(eq(7L), any(), any())).thenReturn(List.of());
        when(auditLogRepository.countDailyActivity(eq(7L), any())).thenReturn(List.of());

        AuditStatsResponse result = service.getStats();

        assertThat(result.getTotalEventsToday()).isEqualTo(10L);
        assertThat(result.getPhiAccessCount()).isEqualTo(2L);
        assertThat(result.getFailedLoginAttempts()).isEqualTo(1L);
        assertThat(result.getActiveUsersToday()).isEqualTo(5L);
    }

    // ===== exportLogs =====

    @Test
    @SuppressWarnings("unchecked")
    void exportLogs_shouldOverridePagination() {
        AuditLogEntity log1 = createLogEntry("user1", "EXPORT");
        Page<AuditLogEntity> page = new PageImpl<>(List.of(log1));

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        AuditLogSearchRequest request = new AuditLogSearchRequest();
        request.setPage(5);   // should be overridden to 0
        request.setSize(50);  // should be overridden to 10000

        List<AuditLogEntry> result = service.exportLogs(request);

        assertThat(result).hasSize(1);
        assertThat(request.getPage()).isEqualTo(0);
        assertThat(request.getSize()).isEqualTo(10000);
    }

    // ===== cleanupOldLogs =====

    @Test
    void cleanupOldLogs_shouldDeleteOldRecords() {
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(50);

        service.cleanupOldLogs();

        verify(auditLogRepository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    // ===== getPhiAccessLog =====

    @Test
    void getPhiAccessLog_withNullStartDate_shouldDefault30DaysAgo() {
        Page<AuditLogEntity> page = new PageImpl<>(List.of());
        when(auditLogRepository.findPhiAccess(eq(7L), any(), any())).thenReturn(page);

        AuditLogResponse result = service.getPhiAccessLog(0, 20, null);

        assertThat(result).isNotNull();
        verify(auditLogRepository).findPhiAccess(eq(7L), any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    void getPhiAccessLog_withValidDate_shouldParse() {
        Page<AuditLogEntity> page = new PageImpl<>(List.of());
        when(auditLogRepository.findPhiAccess(eq(7L), any(), any())).thenReturn(page);

        AuditLogResponse result = service.getPhiAccessLog(0, 20, "2024-01-01");

        assertThat(result).isNotNull();
    }

    // ===== getLoginActivity =====

    @Test
    void getLoginActivity_shouldReturnResponse() {
        Page<AuditLogEntity> page = new PageImpl<>(List.of());
        when(auditLogRepository.findLoginActivity(eq(7L), any(), any())).thenReturn(page);

        AuditLogResponse result = service.getLoginActivity(0, 20, null);

        assertThat(result).isNotNull();
    }

    // ===== getSecurityEvents =====

    @Test
    void getSecurityEvents_shouldReturnResponse() {
        Page<AuditLogEntity> page = new PageImpl<>(List.of());
        when(auditLogRepository.findSecurityEvents(eq(7L), any(), any())).thenReturn(page);

        AuditLogResponse result = service.getSecurityEvents(0, 20, null);

        assertThat(result).isNotNull();
    }

    // ===== getEhrOperations =====

    @Test
    void getEhrOperations_withConnectionId_shouldFilterByConnection() {
        AuditLogEntity log1 = createLogEntry("user1", "READ");
        log1.setConnectionId(5L);
        log1.setPatientFhirId("Patient/123");
        log1.setConnectionName("Hospital A");
        Page<AuditLogEntity> page = new PageImpl<>(List.of(log1));
        when(auditLogRepository.findByConnectionId(eq(7L), eq(5L), any())).thenReturn(page);

        AuditLogResponse result = service.getEhrOperations(0, 50, 5L, 30);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getConnectionId()).isEqualTo(5L);
        assertThat(result.getContent().get(0).getPatientFhirId()).isEqualTo("Patient/123");
        assertThat(result.getContent().get(0).getConnectionName()).isEqualTo("Hospital A");
    }

    @Test
    void getEhrOperations_withoutConnectionId_shouldReturnAllEhrOps() {
        Page<AuditLogEntity> page = new PageImpl<>(List.of());
        when(auditLogRepository.findEhrOperations(eq(7L), any(), any())).thenReturn(page);

        AuditLogResponse result = service.getEhrOperations(0, 50, null, 30);

        assertThat(result).isNotNull();
        verify(auditLogRepository).findEhrOperations(eq(7L), any(LocalDateTime.class), any(Pageable.class));
    }

    // ===== manualCleanup =====

    @Test
    void manualCleanup_shouldDeleteAndReturnCount() {
        when(auditLogRepository.deleteByCreatedAtBefore(any())).thenReturn(100);

        int deleted = service.manualCleanup(90);

        assertThat(deleted).isEqualTo(100);
        verify(auditLogRepository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    // ===== toEntry EHR fields =====

    @Test
    @SuppressWarnings("unchecked")
    void searchLogs_shouldIncludeEhrFieldsInResponse() {
        AuditLogEntity log1 = createLogEntry("user1", "CREATE");
        log1.setConnectionId(10L);
        log1.setPatientFhirId("Patient/456");
        log1.setConnectionName("Hospital B");
        log1.setRequestId("req-abc-123");
        Page<AuditLogEntity> page = new PageImpl<>(List.of(log1));

        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        AuditLogSearchRequest request = new AuditLogSearchRequest();
        AuditLogResponse result = service.searchLogs(request);

        AuditLogEntry entry = result.getContent().get(0);
        assertThat(entry.getConnectionId()).isEqualTo(10L);
        assertThat(entry.getPatientFhirId()).isEqualTo("Patient/456");
        assertThat(entry.getConnectionName()).isEqualTo("Hospital B");
        assertThat(entry.getRequestId()).isEqualTo("req-abc-123");
    }

    // ===== getRetentionDays =====

    @Test
    void getRetentionDays_shouldReturnConfiguredValue() {
        assertThat(service.getRetentionDays()).isEqualTo(365);
    }
}
