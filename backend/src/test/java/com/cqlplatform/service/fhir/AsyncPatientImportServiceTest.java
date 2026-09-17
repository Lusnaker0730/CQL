package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.BatchImportJobEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.ehr.BatchImportRequest;
import com.cqlplatform.repository.BatchImportJobRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncPatientImportServiceTest {

    @Mock
    private BatchImportJobRepository jobRepository;

    @Mock
    private PatientImportService patientImportService;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private AsyncPatientImportService service;

    @BeforeEach
    void setTenant() {
        // Exact-tenant assertions below rely on this (repo convention: tenant 7L).
        TenantContext.setCurrentTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ===== submitBatchImport =====

    @Test
    void submitBatchImport_shouldCreateJobWithPendingStatus() {
        BatchImportRequest request = new BatchImportRequest();
        request.setPatientIds(List.of("Patient/1", "Patient/2", "Patient/3"));
        request.setMeasureId(10L);

        BatchImportJobEntity savedJob = new BatchImportJobEntity();
        savedJob.setId(1L);
        savedJob.setConnectionId(5L);
        savedJob.setTotalPatients(3);
        savedJob.setStatus("pending");

        when(jobRepository.save(any(BatchImportJobEntity.class))).thenReturn(savedJob);

        BatchImportJobEntity result = service.submitBatchImport(5L, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTotalPatients()).isEqualTo(3);
        ArgumentCaptor<BatchImportJobEntity> captor = ArgumentCaptor.forClass(BatchImportJobEntity.class);
        verify(jobRepository).save(captor.capture());
        // Tenant captured on the request thread (Phase 2 — #698)
        assertThat(captor.getValue().getTenantId()).isEqualTo(7L);
    }

    @Test
    void submitBatchImport_withEmptyIds_shouldStillCreate() {
        BatchImportRequest request = new BatchImportRequest();
        request.setPatientIds(List.of());

        BatchImportJobEntity savedJob = new BatchImportJobEntity();
        savedJob.setId(1L);
        savedJob.setTotalPatients(0);

        when(jobRepository.save(any(BatchImportJobEntity.class))).thenReturn(savedJob);

        BatchImportJobEntity result = service.submitBatchImport(1L, request);
        assertThat(result.getTotalPatients()).isEqualTo(0);
    }

    // ===== executeBatchImport =====

    @Test
    void executeBatchImport_shouldProcessAllPatients() {
        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setId(1L);
        job.setConnectionId(5L);
        job.setPatientIds("[\"Patient/1\",\"Patient/2\"]");
        job.setTotalPatients(2);
        job.setStatus("pending");

        PatientImportEntity imported = new PatientImportEntity();
        imported.setId(100L);
        imported.setResourceCount(5);

        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientImportService.importAsTestCase(eq(5L), anyString(), isNull()))
                .thenReturn(imported);

        service.executeBatchImport(1L);

        verify(patientImportService, times(2)).importAsTestCase(eq(5L), anyString(), isNull());
        assertThat(job.getStatus()).isEqualTo("completed");
        assertThat(job.getCompletedCount()).isEqualTo(2);
        assertThat(job.getFailedCount()).isEqualTo(0);
    }

    @Test
    void executeBatchImport_withFailures_shouldRecordErrors() {
        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setId(1L);
        job.setConnectionId(5L);
        job.setPatientIds("[\"Patient/1\",\"Patient/2\"]");
        job.setTotalPatients(2);
        job.setStatus("pending");

        PatientImportEntity imported = new PatientImportEntity();
        imported.setId(100L);
        imported.setResourceCount(3);

        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientImportService.importAsTestCase(eq(5L), eq("Patient/1"), isNull()))
                .thenReturn(imported);
        when(patientImportService.importAsTestCase(eq(5L), eq("Patient/2"), isNull()))
                .thenThrow(new RuntimeException("FHIR server timeout"));

        service.executeBatchImport(1L);

        assertThat(job.getStatus()).isEqualTo("completed");
        assertThat(job.getCompletedCount()).isEqualTo(1);
        assertThat(job.getFailedCount()).isEqualTo(1);
        assertThat(job.getErrorMessage()).contains("1 out of 2");
    }

    @Test
    void executeBatchImport_allFailed_shouldSetStatusFailed() {
        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setId(1L);
        job.setConnectionId(5L);
        job.setPatientIds("[\"Patient/1\"]");
        job.setTotalPatients(1);
        job.setStatus("pending");

        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientImportService.importAsTestCase(eq(5L), eq("Patient/1"), isNull()))
                .thenThrow(new RuntimeException("Connection refused"));

        service.executeBatchImport(1L);

        assertThat(job.getStatus()).isEqualTo("failed");
        assertThat(job.getFailedCount()).isEqualTo(1);
    }

    @Test
    void executeBatchImport_cancelled_shouldStopEarly() {
        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setId(1L);
        job.setConnectionId(5L);
        job.setPatientIds("[\"Patient/1\",\"Patient/2\"]");
        job.setTotalPatients(2);
        job.setStatus("pending");

        // First findById returns running job, second returns cancelled
        BatchImportJobEntity cancelledJob = new BatchImportJobEntity();
        cancelledJob.setId(1L);
        cancelledJob.setCancelled(true);

        PatientImportEntity imported = new PatientImportEntity();
        imported.setId(100L);
        imported.setResourceCount(3);

        when(jobRepository.findById(1L))
                .thenReturn(Optional.of(job))      // initial load
                .thenReturn(Optional.of(cancelledJob)); // cancellation check

        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executeBatchImport(1L);

        assertThat(job.getStatus()).isEqualTo("cancelled");
        // Should not have processed any patients (cancelled before first iteration)
        verify(patientImportService, never()).importAsTestCase(anyLong(), anyString(), any());
    }

    @Test
    void executeBatchImport_jobNotFound_shouldThrow() {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executeBatchImport(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void executeBatchImport_reentersJobTenantOnAsyncThread() {
        // Simulate the async executor thread: NO TenantContext. The job row carries the
        // tenant captured at submit; execute must re-enter it via callWith so downstream
        // tenant-scoped lookups (connection, patient_import write) hit the right clinic.
        TenantContext.clear();

        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setId(1L);
        job.setConnectionId(5L);
        job.setPatientIds("[\"Patient/1\"]");
        job.setTotalPatients(1);
        job.setStatus("pending");
        job.setTenantId(42L);

        PatientImportEntity imported = new PatientImportEntity();
        imported.setId(100L);

        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        java.util.concurrent.atomic.AtomicReference<Long> seenTenant = new java.util.concurrent.atomic.AtomicReference<>();
        when(patientImportService.importAsTestCase(eq(5L), eq("Patient/1"), isNull()))
                .thenAnswer(inv -> {
                    seenTenant.set(TenantContext.getCurrentTenantId());
                    return imported;
                });

        service.executeBatchImport(1L);

        assertThat(seenTenant.get()).isEqualTo(42L);          // import ran under the job's tenant
        assertThat(TenantContext.getCurrentTenantId()).isNull(); // restored afterwards
    }

    // ===== getJob =====

    @Test
    void getJob_shouldReturnJob_tenantScoped() {
        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setId(1L);
        when(jobRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(job));

        BatchImportJobEntity result = service.getJob(1L);
        assertThat(result.getId()).isEqualTo(1L);
        verify(jobRepository, never()).findById(anyLong());
    }

    @Test
    void getJob_notFound_shouldThrow() {
        when(jobRepository.findByIdAndTenantId(999L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getJob(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== listJobs =====

    @Test
    void listJobs_withCreatedBy_shouldFilter_tenantScoped() {
        when(jobRepository.findByTenantIdAndCreatedByOrderByCreatedAtDesc(7L, "admin")).thenReturn(List.of());
        service.listJobs("admin");
        verify(jobRepository).findByTenantIdAndCreatedByOrderByCreatedAtDesc(7L, "admin");
        verify(jobRepository, never()).findByCreatedByOrderByCreatedAtDesc(anyString());
    }

    @Test
    void listJobs_withoutCreatedBy_shouldReturnTenantOnly() {
        when(jobRepository.findByTenantIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        service.listJobs(null);
        verify(jobRepository).findByTenantIdOrderByCreatedAtDesc(7L);
        verify(jobRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    // ===== cancelJob =====

    @Test
    void cancelJob_runningJob_shouldSetCancelled() {
        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setId(1L);
        job.setStatus("running");
        when(jobRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchImportJobEntity result = service.cancelJob(1L);
        assertThat(result.isCancelled()).isTrue();
    }

    @Test
    void cancelJob_completedJob_shouldThrow() {
        BatchImportJobEntity job = new BatchImportJobEntity();
        job.setId(1L);
        job.setStatus("completed");
        when(jobRepository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.cancelJob(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel");
    }
}
