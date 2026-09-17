package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.BatchImportJobEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.ehr.BatchImportRequest;
import com.cqlplatform.repository.BatchImportJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cqlplatform.util.SecurityUtils;
import com.cqlplatform.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncPatientImportService {

    private final BatchImportJobRepository jobRepository;
    private final PatientImportService patientImportService;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * Create a batch import job and return immediately. The caller should trigger
     * {@link #executeBatchImport(Long)} asynchronously after this returns.
     */
    @Transactional
    public BatchImportJobEntity submitBatchImport(Long connectionId, BatchImportRequest request) {
        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setConnectionId(connectionId);
        job.setTotalPatients(request.getPatientIds().size());
        job.setStatus("pending");
        job.setMeasureId(request.getMeasureId());
        job.setCreatedBy(SecurityUtils.getCurrentUsername("system"));
        // Captured on the request thread; executeBatchImport re-enters this tenant on the
        // async executor thread via TenantContext.callWith (the ThreadLocal doesn't cross).
        job.setTenantId(effectiveTenantId());

        try {
            job.setPatientIds(MAPPER.writeValueAsString(request.getPatientIds()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid patient IDs format");
        }

        job = jobRepository.save(job);
        log.info("Created batch import job {} for {} patients on connection {}",
                job.getId(), job.getTotalPatients(), connectionId);

        return job;
    }

    /**
     * Execute the batch import asynchronously.
     *
     * <p>Runs on the {@code patientImportExecutor} thread where the request thread's
     * {@code TenantContext} ThreadLocal is NOT visible. Without re-entering the job's
     * tenant, every downstream tenant-scoped lookup would resolve against the default
     * tenant — most immediately {@code EhrConnectionService.getById} (tenant-scoped since
     * PAT-180), which would fail the whole batch for any non-default clinic. Same
     * propagation pattern as PAT-181/184/189: capture at submit, re-enter via callWith.
     */
    @Async("patientImportExecutor")
    public void executeBatchImport(Long jobId) {
        // System lookup by id — the tenant to run under comes from the job row itself
        // (assigned on the request thread in submitBatchImport).
        BatchImportJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch import job not found: " + jobId));
        Long jobTenant = job.getTenantId() != null ? job.getTenantId() : effectiveTenantId();
        com.cqlplatform.security.TenantContext.callWith(jobTenant, () -> {
            doExecuteBatchImport(job);
            return null;
        });
    }

    private void doExecuteBatchImport(BatchImportJobEntity job) {
        Long jobId = job.getId();

        job.setStatus("running");
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        List<String> patientIds;
        try {
            patientIds = MAPPER.readValue(job.getPatientIds(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            job.setStatus("failed");
            job.setErrorMessage("Failed to parse patient IDs: " + e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            return;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int completed = 0;
        int failed = 0;
        int processed = 0;

        for (String patientId : patientIds) {
            // Check for cancellation and save progress every 10 patients
            if (processed % 10 == 0) {
                BatchImportJobEntity current = jobRepository.findById(jobId).orElse(null);
                if (current == null || current.isCancelled()) {
                    job.setStatus("cancelled");
                    job.setCompletedAt(LocalDateTime.now());
                    jobRepository.save(job);
                    log.info("Batch import job {} cancelled after processing {}/{} patients",
                            jobId, completed, patientIds.size());
                    return;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("patientId", patientId);

            try {
                PatientImportEntity imported = patientImportService.importAsTestCase(
                        job.getConnectionId(), patientId, job.getMeasureId());
                result.put("status", "success");
                result.put("importId", imported.getId());
                result.put("resourceCount", imported.getResourceCount());
                completed++;
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("error", StringUtils.truncate(e.getMessage(), 500));
                failed++;
                log.warn("Batch import job {}: failed to import patient {}: {}",
                        jobId, patientId, e.getMessage());
            }

            results.add(result);
            processed++;

            // Update progress every 10 patients
            if (processed % 10 == 0) {
                job.setCompletedCount(completed);
                job.setFailedCount(failed);
                jobRepository.save(job);
            }
        }

        // Save final progress
        job.setCompletedCount(completed);
        job.setFailedCount(failed);

        // Finalize
        job.setStatus(failed == patientIds.size() ? "failed" : "completed");
        job.setCompletedAt(LocalDateTime.now());
        try {
            job.setResultSummary(MAPPER.writeValueAsString(results));
        } catch (JsonProcessingException e) {
            job.setResultSummary("[]");
        }
        if (failed > 0) {
            job.setErrorMessage(failed + " out of " + patientIds.size() + " patients failed to import");
        }
        jobRepository.save(job);

        log.info("Batch import job {} completed: {}/{} succeeded, {} failed",
                jobId, completed, patientIds.size(), failed);
    }

    @Transactional(readOnly = true)
    public BatchImportJobEntity getJob(Long jobId) {
        return jobRepository.findByIdAndTenantId(jobId, effectiveTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch import job not found: " + jobId));
    }

    @Transactional(readOnly = true)
    public List<BatchImportJobEntity> listJobs(String createdBy) {
        if (createdBy != null && !createdBy.isBlank()) {
            return jobRepository.findByTenantIdAndCreatedByOrderByCreatedAtDesc(effectiveTenantId(), createdBy);
        }
        return jobRepository.findByTenantIdOrderByCreatedAtDesc(effectiveTenantId());
    }

    @Transactional
    public BatchImportJobEntity cancelJob(Long jobId) {
        BatchImportJobEntity job = getJob(jobId);
        if (!"running".equals(job.getStatus()) && !"pending".equals(job.getStatus())) {
            throw new IllegalArgumentException("Cannot cancel job in status: " + job.getStatus());
        }
        job.setCancelled(true);
        return jobRepository.save(job);
    }

}
