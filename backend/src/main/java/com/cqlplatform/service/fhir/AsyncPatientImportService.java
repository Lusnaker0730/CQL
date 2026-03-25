package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.BatchImportJobEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.ehr.BatchImportRequest;
import com.cqlplatform.repository.BatchImportJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        job.setCreatedBy(getCurrentUsername());

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
     */
    @Async("cqlExecutionExecutor")
    public void executeBatchImport(Long jobId) {
        BatchImportJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch import job not found: " + jobId));

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

        for (String patientId : patientIds) {
            // Check for cancellation
            BatchImportJobEntity current = jobRepository.findById(jobId).orElse(null);
            if (current == null || current.isCancelled()) {
                job.setStatus("cancelled");
                job.setCompletedAt(LocalDateTime.now());
                jobRepository.save(job);
                log.info("Batch import job {} cancelled after processing {}/{} patients",
                        jobId, completed, patientIds.size());
                return;
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
                result.put("error", truncate(e.getMessage(), 500));
                failed++;
                log.warn("Batch import job {}: failed to import patient {}: {}",
                        jobId, patientId, e.getMessage());
            }

            results.add(result);

            // Update progress
            job.setCompletedCount(completed);
            job.setFailedCount(failed);
            jobRepository.save(job);
        }

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
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch import job not found: " + jobId));
    }

    @Transactional(readOnly = true)
    public List<BatchImportJobEntity> listJobs(String createdBy) {
        if (createdBy != null && !createdBy.isBlank()) {
            return jobRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
        }
        return jobRepository.findAllByOrderByCreatedAtDesc();
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

    private String getCurrentUsername() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not determine current user: {}", e.getMessage());
        }
        return "system";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
