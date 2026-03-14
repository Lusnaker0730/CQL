package com.cqlplatform.service;

import com.cqlplatform.entity.AuditLogEntity;
import com.cqlplatform.model.audit.*;
import com.cqlplatform.repository.AuditLogRepository;
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

    @InjectMocks
    private AuditService service;

    @BeforeEach
    void setUp() {
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
        when(auditLogRepository.countByCreatedAtAfter(any())).thenReturn(10L);
        when(auditLogRepository.countPhiAccess(any())).thenReturn(2L);
        when(auditLogRepository.countFailedLoginAttempts(any())).thenReturn(1L);
        when(auditLogRepository.countDistinctUsernameByCreatedAtAfter(any())).thenReturn(5L);
        when(auditLogRepository.countByActionGrouped(any())).thenReturn(List.of());
        when(auditLogRepository.findTopUsers(any(), any())).thenReturn(List.of());
        when(auditLogRepository.countDailyActivity(any())).thenReturn(List.of());

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
        when(auditLogRepository.findPhiAccess(any(), any())).thenReturn(page);

        AuditLogResponse result = service.getPhiAccessLog(0, 20, null);

        assertThat(result).isNotNull();
        verify(auditLogRepository).findPhiAccess(any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    void getPhiAccessLog_withValidDate_shouldParse() {
        Page<AuditLogEntity> page = new PageImpl<>(List.of());
        when(auditLogRepository.findPhiAccess(any(), any())).thenReturn(page);

        AuditLogResponse result = service.getPhiAccessLog(0, 20, "2024-01-01");

        assertThat(result).isNotNull();
    }

    // ===== getLoginActivity =====

    @Test
    void getLoginActivity_shouldReturnResponse() {
        Page<AuditLogEntity> page = new PageImpl<>(List.of());
        when(auditLogRepository.findLoginActivity(any(), any())).thenReturn(page);

        AuditLogResponse result = service.getLoginActivity(0, 20, null);

        assertThat(result).isNotNull();
    }

    // ===== getSecurityEvents =====

    @Test
    void getSecurityEvents_shouldReturnResponse() {
        Page<AuditLogEntity> page = new PageImpl<>(List.of());
        when(auditLogRepository.findSecurityEvents(any(), any())).thenReturn(page);

        AuditLogResponse result = service.getSecurityEvents(0, 20, null);

        assertThat(result).isNotNull();
    }
}
