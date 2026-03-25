package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.FailedImportEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.repository.FailedImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportRetryServiceTest {

    @Mock
    private FailedImportRepository failedImportRepository;

    @Mock
    private PatientImportService patientImportService;

    @InjectMocks
    private ImportRetryService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(service, "retryInitialDelaySeconds", 60);
    }

    // ===== recordFailure =====

    @Test
    void recordFailure_shouldCreatePendingRecord() {
        when(failedImportRepository.save(any(FailedImportEntity.class)))
                .thenAnswer(inv -> {
                    FailedImportEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        FailedImportEntity result = service.recordFailure(
                5L, "Patient/123", 10L,
                new RuntimeException("Connection timeout"), "admin");

        assertThat(result.getConnectionId()).isEqualTo(5L);
        assertThat(result.getPatientFhirId()).isEqualTo("Patient/123");
        assertThat(result.getMeasureId()).isEqualTo(10L);
        assertThat(result.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(result.getErrorType()).isEqualTo("RuntimeException");
        assertThat(result.getStatus()).isEqualTo("pending");
        assertThat(result.getMaxRetries()).isEqualTo(3);
        assertThat(result.getCreatedBy()).isEqualTo("admin");
        assertThat(result.getNextRetryAt()).isNotNull();
    }

    @Test
    void recordFailure_withNullCreatedBy_shouldDefaultToSystem() {
        when(failedImportRepository.save(any(FailedImportEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FailedImportEntity result = service.recordFailure(
                5L, "Patient/123", null,
                new RuntimeException("Error"), null);

        assertThat(result.getCreatedBy()).isEqualTo("system");
    }

    @Test
    void recordFailure_withLongErrorMessage_shouldTruncate() {
        String longMessage = "x".repeat(3000);
        when(failedImportRepository.save(any(FailedImportEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FailedImportEntity result = service.recordFailure(
                5L, "Patient/123", null,
                new RuntimeException(longMessage), "admin");

        assertThat(result.getErrorMessage()).hasSize(2000);
    }

    // ===== retryImport =====

    @Test
    void retryImport_success_shouldResolve() {
        FailedImportEntity failed = createFailed(1L, "pending", 0);
        PatientImportEntity imported = new PatientImportEntity();
        imported.setId(100L);

        when(failedImportRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(failedImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientImportService.importAsTestCase(5L, "Patient/123", null)).thenReturn(imported);

        FailedImportEntity result = service.retryImport(1L);

        assertThat(result.getStatus()).isEqualTo("resolved");
        assertThat(result.getResolvedAt()).isNotNull();
        assertThat(result.getRetryCount()).isEqualTo(1);
    }

    @Test
    void retryImport_failure_shouldIncrementRetry() {
        FailedImportEntity failed = createFailed(1L, "pending", 0);

        when(failedImportRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(failedImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientImportService.importAsTestCase(5L, "Patient/123", null))
                .thenThrow(new RuntimeException("Still failing"));

        FailedImportEntity result = service.retryImport(1L);

        assertThat(result.getStatus()).isEqualTo("pending");
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getNextRetryAt()).isNotNull();
    }

    @Test
    void retryImport_exhausted_shouldMarkExhausted() {
        FailedImportEntity failed = createFailed(1L, "pending", 2); // Already retried twice

        when(failedImportRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(failedImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientImportService.importAsTestCase(5L, "Patient/123", null))
                .thenThrow(new RuntimeException("Still failing"));

        FailedImportEntity result = service.retryImport(1L);

        assertThat(result.getStatus()).isEqualTo("exhausted");
        assertThat(result.getRetryCount()).isEqualTo(3);
    }

    @Test
    void retryImport_alreadyResolved_shouldThrow() {
        FailedImportEntity failed = createFailed(1L, "resolved", 1);

        when(failedImportRepository.findById(1L)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.retryImport(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already resolved");
    }

    @Test
    void retryImport_exhaustedManual_shouldResetAndRetry() {
        FailedImportEntity failed = createFailed(1L, "exhausted", 3);
        PatientImportEntity imported = new PatientImportEntity();
        imported.setId(100L);

        when(failedImportRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(failedImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientImportService.importAsTestCase(5L, "Patient/123", null)).thenReturn(imported);

        FailedImportEntity result = service.retryImport(1L);

        assertThat(result.getStatus()).isEqualTo("resolved");
        assertThat(result.getRetryCount()).isEqualTo(1); // Reset to 0 then incremented
    }

    @Test
    void retryImport_notFound_shouldThrow() {
        when(failedImportRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryImport(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== processAutoRetries =====

    @Test
    void processAutoRetries_noDueRetries_shouldDoNothing() {
        when(failedImportRepository.findDueForRetry(any())).thenReturn(List.of());

        service.processAutoRetries();

        verify(patientImportService, never()).importAsTestCase(anyLong(), anyString(), any());
    }

    @Test
    void processAutoRetries_withDueRetries_shouldProcess() {
        FailedImportEntity failed = createFailed(1L, "pending", 0);
        PatientImportEntity imported = new PatientImportEntity();
        imported.setId(100L);

        when(failedImportRepository.findDueForRetry(any())).thenReturn(List.of(failed));
        when(failedImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientImportService.importAsTestCase(5L, "Patient/123", null)).thenReturn(imported);

        service.processAutoRetries();

        verify(patientImportService).importAsTestCase(5L, "Patient/123", null);
    }

    // ===== listFailedImports =====

    @Test
    void listFailedImports_withStatus_shouldFilter() {
        when(failedImportRepository.findByStatusOrderByCreatedAtDesc("pending")).thenReturn(List.of());
        service.listFailedImports("pending");
        verify(failedImportRepository).findByStatusOrderByCreatedAtDesc("pending");
    }

    @Test
    void listFailedImports_withoutStatus_shouldReturnAll() {
        when(failedImportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        service.listFailedImports(null);
        verify(failedImportRepository).findAllByOrderByCreatedAtDesc();
    }

    // ===== deleteFailedImport =====

    @Test
    void deleteFailedImport_exists_shouldDelete() {
        when(failedImportRepository.existsById(1L)).thenReturn(true);
        service.deleteFailedImport(1L);
        verify(failedImportRepository).deleteById(1L);
    }

    @Test
    void deleteFailedImport_notFound_shouldThrow() {
        when(failedImportRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.deleteFailedImport(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== calculateNextRetry =====

    @Test
    void calculateNextRetry_shouldUseExponentialBackoff() {
        LocalDateTime now = LocalDateTime.now();

        // retry 0: 60s
        LocalDateTime retry0 = service.calculateNextRetry(0);
        assertThat(retry0).isAfter(now.plusSeconds(59));
        assertThat(retry0).isBefore(now.plusSeconds(62));

        // retry 1: 120s
        LocalDateTime retry1 = service.calculateNextRetry(1);
        assertThat(retry1).isAfter(now.plusSeconds(119));

        // retry 2: 240s
        LocalDateTime retry2 = service.calculateNextRetry(2);
        assertThat(retry2).isAfter(now.plusSeconds(239));
    }

    @Test
    void calculateNextRetry_shouldCapAtOneHour() {
        // retry 10: 60 * 2^10 = 61440s, should be capped at 3600
        LocalDateTime retry10 = service.calculateNextRetry(10);
        assertThat(retry10).isBefore(LocalDateTime.now().plusSeconds(3602));
    }

    // ===== helper =====

    private FailedImportEntity createFailed(Long id, String status, int retryCount) {
        FailedImportEntity failed = new FailedImportEntity();
        failed.setId(id);
        failed.setConnectionId(5L);
        failed.setPatientFhirId("Patient/123");
        failed.setStatus(status);
        failed.setRetryCount(retryCount);
        failed.setMaxRetries(3);
        failed.setCreatedBy("admin");
        return failed;
    }
}
