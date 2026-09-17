package com.cqlplatform.service.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.entity.ConnectionHealthCheckEntity;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.repository.ConnectionHealthCheckRepository;
import com.cqlplatform.util.StringUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionHealthService {

    private final ConnectionHealthCheckRepository healthCheckRepository;
    private final EhrConnectionService connectionService;
    private final FhirClientFactory fhirClientFactory;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${ehr.health.check-interval-minutes:15}")
    private int checkIntervalMinutes;

    @Value("${ehr.health.retention-days:7}")
    private int retentionDays;

    /**
     * Perform a health check for a specific connection.
     */
    @Transactional
    public ConnectionHealthCheckEntity checkConnection(Long connectionId) {
        EhrConnectionEntity connection = connectionService.getById(connectionId);
        ConnectionHealthCheckEntity check = new ConnectionHealthCheckEntity();
        check.setConnectionId(connectionId);

        long startTime = System.currentTimeMillis();
        try {
            IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);
            CapabilityStatement capabilities = client.capabilities()
                    .ofType(CapabilityStatement.class)
                    .execute();

            long responseTime = System.currentTimeMillis() - startTime;
            check.setResponseTimeMs(responseTime);
            check.setStatus(responseTime > 5000 ? "degraded" : "healthy");
            check.setFhirVersion(capabilities.getFhirVersion() != null ? capabilities.getFhirVersion().toCode() : null);
            check.setServerName(capabilities.getSoftware() != null ? capabilities.getSoftware().getName() : null);
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            check.setResponseTimeMs(responseTime);
            check.setStatus("down");
            check.setErrorMessage(StringUtils.truncate(e.getMessage(), 1000));
        }

        check = healthCheckRepository.save(check);
        log.debug("Health check for connection {}: {} ({}ms)", connectionId, check.getStatus(), check.getResponseTimeMs());
        return check;
    }

    /**
     * Get health overview for all active connections.
     */
    @Transactional(readOnly = true)
    public List<ConnectionHealthOverview> getHealthOverview() {
        List<EhrConnectionEntity> connections = connectionService.list(null);
        List<ConnectionHealthOverview> overview = new ArrayList<>();

        for (EhrConnectionEntity connection : connections) {
            Optional<ConnectionHealthCheckEntity> latest =
                    healthCheckRepository.findFirstByConnectionIdOrderByCheckedAtDesc(connection.getId());

            // Calculate avg response time from last 24h
            LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
            List<ConnectionHealthCheckEntity> recentChecks =
                    healthCheckRepository.findRecentByConnectionId(connection.getId(), dayAgo);

            Double avgResponseTime = recentChecks.stream()
                    .filter(c -> c.getResponseTimeMs() != null)
                    .mapToLong(ConnectionHealthCheckEntity::getResponseTimeMs)
                    .average().orElse(0);

            long errorCount = recentChecks.stream()
                    .filter(c -> "down".equals(c.getStatus()))
                    .count();

            double availability = recentChecks.isEmpty() ? 0 :
                    (1.0 - (double) errorCount / recentChecks.size()) * 100;

            overview.add(new ConnectionHealthOverview(
                    connection.getId(),
                    connection.getName(),
                    connection.getFhirServerUrl(),
                    latest.map(ConnectionHealthCheckEntity::getStatus).orElse("unknown"),
                    latest.map(ConnectionHealthCheckEntity::getResponseTimeMs).orElse(null),
                    latest.map(ConnectionHealthCheckEntity::getCheckedAt).orElse(null),
                    avgResponseTime,
                    Math.round(availability * 10) / 10.0,
                    recentChecks.size(),
                    errorCount
            ));
        }

        return overview;
    }

    /**
     * Get health check history for a connection.
     */
    @Transactional(readOnly = true)
    public List<ConnectionHealthCheckEntity> getHistory(Long connectionId, int hours) {
        // BUG-138: connection_health_check has no tenant_id — its tenant is its parent
        // connection's, so the parent gate IS the boundary. getById is tenant-scoped
        // (findByIdAndTenantId), so another tenant's connection reads as not-found. The
        // endpoint itself has no @PreAuthorize at all, so this is the only check.
        connectionService.getById(connectionId);
        LocalDateTime after = LocalDateTime.now().minusHours(hours);
        return healthCheckRepository.findRecentByConnectionId(connectionId, after);
    }

    /**
     * Get circuit breaker statuses.
     */
    public List<CircuitBreakerStatus> getCircuitBreakerStatuses() {
        return circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .map(cb -> new CircuitBreakerStatus(
                        cb.getName(),
                        cb.getState().name(),
                        cb.getMetrics().getFailureRate(),
                        cb.getMetrics().getSlowCallRate(),
                        cb.getMetrics().getNumberOfBufferedCalls(),
                        cb.getMetrics().getNumberOfFailedCalls(),
                        cb.getMetrics().getNumberOfSuccessfulCalls()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Scheduled: check all active connections periodically.
     */
    @Scheduled(fixedDelayString = "${ehr.health.check-interval-ms:900000}")
    public void scheduledHealthCheck() {
        List<EhrConnectionEntity> connections = connectionService.list(null);
        log.info("Running scheduled health check for {} connections", connections.size());
        connections.parallelStream().forEach(connection -> {
            try {
                checkConnection(connection.getId());
            } catch (Exception e) {
                log.warn("Scheduled health check failed for connection {}: {}", connection.getId(), e.getMessage());
            }
        });
    }

    /**
     * Scheduled: clean up old health check records.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldChecks() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = healthCheckRepository.deleteByCheckedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} old health check records (older than {} days)", deleted, retentionDays);
        }
    }

    // DTOs
    public record ConnectionHealthOverview(
            Long connectionId,
            String connectionName,
            String fhirServerUrl,
            String currentStatus,
            Long lastResponseTimeMs,
            LocalDateTime lastCheckedAt,
            Double avgResponseTimeMs24h,
            Double availability24h,
            long totalChecks24h,
            long errorCount24h
    ) {}

    public record CircuitBreakerStatus(
            String name,
            String state,
            float failureRate,
            float slowCallRate,
            int bufferedCalls,
            int failedCalls,
            int successfulCalls
    ) {}
}
