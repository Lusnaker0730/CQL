package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.FailedImportEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.repository.FailedImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
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
        failed.setErrorMessage(truncate(error.getMessage(), 2000));
        failed.setErrorType(error.getClass().getSimpleName());
        failed.setMaxRetries(maxRetryAttempts);
        failed.setCreatedBy(createdBy != null ? createdBy : "system");
        failed.setNextRetryAt(calculateNextRetry(0));
        failed.setStatus("pending");

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
        FailedImportEntity failed = failedImportRepository.findById(failedImportId)
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
                executeRetry(failed);
            } catch (Exception e) {
                log.warn("Auto-retry failed for import {}: {}", failed.getId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<FailedImportEntity> listFailedImports(String status) {
        if (status != null && !status.isBlank()) {
            return failedImportRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return failedImportRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public FailedImportEntity getFailedImport(Long id) {
        return failedImportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Failed import not found: " + id));
    }

    @Transactional
    public void deleteFailedImport(Long id) {
        if (!failedImportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Failed import not found: " + id);
        }
        failedImportRepository.deleteById(id);
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
            failed.setErrorMessage(truncate(e.getMessage(), 2000));
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

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
