package com.cqlplatform.service;

import com.cqlplatform.entity.AuditLogEntity;
import com.cqlplatform.model.audit.*;
import com.cqlplatform.repository.AuditLogRepository;
import com.cqlplatform.repository.AuditLogSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Value("${audit.retention-days:365}")
    private int retentionDays;

    public AuditLogResponse searchLogs(AuditLogSearchRequest request) {
        var spec = AuditLogSpecification.fromSearchRequest(request);
        var pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogEntity> page = auditLogRepository.findAll(spec, pageable);

        return AuditLogResponse.builder()
                .content(page.getContent().stream().map(this::toEntry).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    public AuditStatsResponse getStats() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek = startOfToday.minusDays(7);
        LocalDateTime startOfMonth = startOfToday.minusDays(30);

        long eventsToday = auditLogRepository.countByCreatedAtAfter(startOfToday);
        long eventsWeek = auditLogRepository.countByCreatedAtAfter(startOfWeek);
        long eventsMonth = auditLogRepository.countByCreatedAtAfter(startOfMonth);
        long phiAccess = auditLogRepository.countPhiAccess(startOfToday);
        long failedLogins = auditLogRepository.countFailedLoginAttempts(startOfToday);
        long activeUsers = auditLogRepository.countDistinctUsernameByCreatedAtAfter(startOfToday);

        // Action distribution (last 30 days)
        List<Object[]> actionRows = auditLogRepository.countByActionGrouped(startOfMonth);
        Map<String, Long> actionCounts = new LinkedHashMap<>();
        for (Object[] row : actionRows) {
            actionCounts.put((String) row[0], (Long) row[1]);
        }

        // Top users (last 30 days)
        List<Object[]> topUserRows = auditLogRepository.findTopUsers(startOfMonth, PageRequest.of(0, 10));
        List<UserActivitySummary> topUsers = topUserRows.stream()
                .map(row -> UserActivitySummary.builder()
                        .username((String) row[0])
                        .eventCount((Long) row[1])
                        .lastActivityAt(row[2] != null ? row[2].toString() : null)
                        .build())
                .toList();

        // Daily activity (last 14 days)
        LocalDateTime twoWeeksAgo = startOfToday.minusDays(14);
        List<Object[]> dailyRows = auditLogRepository.countDailyActivity(twoWeeksAgo);
        List<DailyActivityCount> dailyActivity = dailyRows.stream()
                .map(row -> DailyActivityCount.builder()
                        .date(row[0].toString())
                        .count((Long) row[1])
                        .build())
                .toList();

        return AuditStatsResponse.builder()
                .totalEventsToday(eventsToday)
                .totalEventsWeek(eventsWeek)
                .totalEventsMonth(eventsMonth)
                .phiAccessCount(phiAccess)
                .failedLoginAttempts(failedLogins)
                .activeUsersToday(activeUsers)
                .actionCounts(actionCounts)
                .topUsers(topUsers)
                .dailyActivity(dailyActivity)
                .build();
    }

    public AuditLogResponse getPhiAccessLog(int page, int size, String startDate) {
        LocalDateTime after = parseStartDate(startDate);
        var pageable = PageRequest.of(page, size);
        Page<AuditLogEntity> result = auditLogRepository.findPhiAccess(after, pageable);
        return toResponse(result);
    }

    public AuditLogResponse getLoginActivity(int page, int size, String startDate) {
        LocalDateTime after = parseStartDate(startDate);
        var pageable = PageRequest.of(page, size);
        Page<AuditLogEntity> result = auditLogRepository.findLoginActivity(after, pageable);
        return toResponse(result);
    }

    public AuditLogResponse getSecurityEvents(int page, int size, String startDate) {
        LocalDateTime after = parseStartDate(startDate);
        var pageable = PageRequest.of(page, size);
        Page<AuditLogEntity> result = auditLogRepository.findSecurityEvents(after, pageable);
        return toResponse(result);
    }

    public List<AuditLogEntry> exportLogs(AuditLogSearchRequest request) {
        // Override pagination for export, cap at 10,000
        request.setPage(0);
        request.setSize(10000);
        var spec = AuditLogSpecification.fromSearchRequest(request);
        var pageable = PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogEntity> page = auditLogRepository.findAll(spec, pageable);
        return page.getContent().stream().map(this::toEntry).toList();
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Audit log retention cleanup: deleted {} records older than {} days", deleted, retentionDays);
        }
    }

    private LocalDateTime parseStartDate(String startDate) {
        if (startDate != null && !startDate.isBlank()) {
            return LocalDate.parse(startDate).atStartOfDay();
        }
        return LocalDate.now().minusDays(30).atStartOfDay();
    }

    private AuditLogResponse toResponse(Page<AuditLogEntity> page) {
        return AuditLogResponse.builder()
                .content(page.getContent().stream().map(this::toEntry).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    public AuditLogResponse getEhrOperations(int page, int size, Long connectionId, int days) {
        var pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogEntity> result;
        if (connectionId != null) {
            result = auditLogRepository.findByConnectionId(connectionId, pageable);
        } else {
            LocalDateTime after = LocalDateTime.now().minusDays(days);
            result = auditLogRepository.findEhrOperations(after, pageable);
        }
        return toResponse(result);
    }

    @Transactional
    public int manualCleanup(int olderThanDays) {
        LocalDateTime before = LocalDateTime.now().minusDays(olderThanDays);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(before);
        log.info("Manual audit cleanup: {} logs deleted (older than {} days)", deleted, olderThanDays);
        return deleted;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    private AuditLogEntry toEntry(AuditLogEntity entity) {
        return AuditLogEntry.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .method(entity.getMethod())
                .path(entity.getPath())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .action(entity.getAction())
                .statusCode(entity.getStatusCode())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .responseTimeMs(entity.getResponseTimeMs())
                .phiAccess(entity.isPhiAccess())
                .queryParameters(entity.getQueryParameters())
                .requestId(entity.getRequestId())
                .connectionId(entity.getConnectionId())
                .patientFhirId(entity.getPatientFhirId())
                .connectionName(entity.getConnectionName())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .build();
    }
}
