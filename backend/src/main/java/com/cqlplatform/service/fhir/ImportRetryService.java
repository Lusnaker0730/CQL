package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.FailedImportEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.repository.FailedImportRepository;
import com.cqlplatform.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing failed patient imports and retry logic with exponential backoff.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportRetryService {

    private final FailedImportRepository failedImportRepository;
    private final PatientImportService patientImportService;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    @Value("${ehr.import.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${ehr.import.retry-initial-delay-seconds:60}")
    private int retryInitialDelaySeconds;

    /**
     * Record a failed import for later retry.
     */
    @Transactional
    public FailedImportEntity recordFailure(Long connectionId, String patientFhirId, Long measureId,
                                             Exception error, String createdBy) {
        FailedImportEntity failed = new FailedImportEntity();
        failed.setConnectionId(connectionId);
        failed.setPatientFhirId(patientFhirId);
        failed.setMeasureId(measureId);
        failed.setErrorMessage(StringUtils.truncate(error.getMessage(), 2000));
        failed.setErrorType(error.getClass().getSimpleName());
        failed.setMaxRetries(maxRetryAttempts);
        failed.setCreatedBy(createdBy != null ? createdBy : "system");
        failed.setNextRetryAt(calculateNextRetry(0));
        failed.setStatus("pending");
        failed.setTenantId(effectiveTenantId());

        failed = failedImportRepository.save(failed);
        log.info("Recorded failed import: connection={}, patient={}, error={}",
                connectionId, patientFhirId, error.getClass().getSimpleName());
        return failed;
    }

    /**
     * Manually retry a specific failed import.
     */
    @Transactional
    public FailedImportEntity retryImport(Long failedImportId) {
        // Tenant-scoped: a caller cannot retry (and thereby re-import PHI for) another
        // clinic's failed row. The retry then runs on the request thread under the
        // caller's TenantContext, which the scoped fetch guarantees matches the row.
        FailedImportEntity failed = failedImportRepository.findByIdAndTenantId(failedImportId, effectiveTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Failed import not found: " + failedImportId));

        if ("resolved".equals(failed.getStatus())) {
            throw new IllegalArgumentException("Import already resolved");
        }
        if ("exhausted".equals(failed.getStatus())) {
            // Allow manual retry even if exhausted — reset counter
            failed.setRetryCount(0);
            failed.setStatus("pending");
        }

        return executeRetry(failed);
    }

    /**
     * Scheduled automatic retry for pending failed imports that are due.
     */
    @Scheduled(fixedDelayString = "${ehr.import.retry-check-interval-ms:60000}")
    @Transactional
    public void processAutoRetries() {
        List<FailedImportEntity> dueForRetry = failedImportRepository.findDueForRetry(LocalDateTime.now());
        if (dueForRetry.isEmpty()) return;

        log.info("Processing {} auto-retries for failed imports", dueForRetry.size());
        for (FailedImportEntity failed : dueForRetry) {
            try {
                // Scheduler thread has no TenantContext — re-enter each row's own tenant so
                // the re-import (connection lookup + patient_import write) lands in the right
                // clinic (PAT-189 pattern). Legacy rows without a tenant use the default.
                Long rowTenant = failed.getTenantId() != null ? failed.getTenantId() : effectiveTenantId();
                com.cqlplatform.security.TenantContext.callWith(rowTenant, () -> executeRetry(failed));
            } catch (Exception e) {
                log.warn("Auto-retry failed for import {}: {}", failed.getId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<FailedImportEntity> listFailedImports(String status) {
        if (status != null && !status.isBlank()) {
            return failedImportRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(effectiveTenantId(), status);
        }
        return failedImportRepository.findByTenantIdOrderByCreatedAtDesc(effectiveTenantId());
    }

    @Transactional(readOnly = true)
    public FailedImportEntity getFailedImport(Long id) {
        return failedImportRepository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Failed import not found: " + id));
    }

    @Transactional
    public void deleteFailedImport(Long id) {
        FailedImportEntity entity = failedImportRepository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Failed import not found: " + id));
        failedImportRepository.delete(entity);
    }

    /** Caller's tenant ?? default — see EhrConnectionService for the canonical pattern. */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    private FailedImportEntity executeRetry(FailedImportEntity failed) {
        failed.setStatus("retrying");
        failed.setRetryCount(failed.getRetryCount() + 1);
        failed.setLastRetryAt(LocalDateTime.now());
        failedImportRepository.save(failed);

        try {
            PatientImportEntity imported = patientImportService.importAsTestCase(
                    failed.getConnectionId(), failed.getPatientFhirId(), failed.getMeasureId());

            failed.setStatus("resolved");
            failed.setResolvedAt(LocalDateTime.now());
            log.info("Retry succeeded for failed import {}: patient {} imported as {}",
                    failed.getId(), failed.getPatientFhirId(), imported.getId());
        } catch (Exception e) {
            failed.setErrorMessage(StringUtils.truncate(e.getMessage(), 2000));
            failed.setErrorType(e.getClass().getSimpleName());

            if (failed.getRetryCount() >= failed.getMaxRetries()) {
                failed.setStatus("exhausted");
                log.warn("Failed import {} exhausted all {} retries for patient {}",
                        failed.getId(), failed.getMaxRetries(), failed.getPatientFhirId());
            } else {
                failed.setStatus("pending");
                failed.setNextRetryAt(calculateNextRetry(failed.getRetryCount()));
                log.info("Retry {} of {} failed for import {}, next retry at {}",
                        failed.getRetryCount(), failed.getMaxRetries(),
                        failed.getId(), failed.getNextRetryAt());
            }
        }

        return failedImportRepository.save(failed);
    }

    /**
     * Calculate next retry time using exponential backoff.
     * delay = initialDelay * 2^retryCount
     */
    LocalDateTime calculateNextRetry(int retryCount) {
        long delaySeconds = (long) retryInitialDelaySeconds * (1L << retryCount);
        // Cap at 1 hour
        delaySeconds = Math.min(delaySeconds, 3600);
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

}
