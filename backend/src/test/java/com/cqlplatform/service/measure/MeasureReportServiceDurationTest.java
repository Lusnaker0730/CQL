package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReportRepository;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BUG-145 — {@code measure_report.evaluation_duration_ms} carries a CHECK (>= 0) since V51. The
 * duration used to be a wall-clock subtraction, so a clock stepping backwards during an evaluation
 * produced a negative value, the INSERT was rejected, and the whole report was lost — for the sake
 * of a piece of metadata. The report is the clinical record; the duration is not. A duration that
 * cannot be right must never cost the report.
 */
@ExtendWith(MockitoExtension.class)
class MeasureReportServiceDurationTest {

    @Mock private MeasureReportRepository repository;
    @Mock private MeasureReportNormalizer normalizer;
    @Mock private MeasureDefinitionRepository measureDefinitionRepository;
    @Mock private TenantRepository tenantRepository;
    @InjectMocks private MeasureReportService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(7L);
        when(repository.save(any(MeasureReportEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static MeasureEvaluationResult result() {
        return MeasureEvaluationResult.builder().measureId("m1").measureName("M1").status("complete")
                .reportType("summary").periodStart(LocalDate.of(2024, 1, 1)).periodEnd(LocalDate.of(2024, 12, 31)).build();
    }

    private MeasureReportEntity saved() {
        ArgumentCaptor<MeasureReportEntity> captor = ArgumentCaptor.forClass(MeasureReportEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void aNegativeDuration_isStoredAsUnknown_soTheReportIsNotRejectedByTheDatabase() {
        service.saveReport(result(), null, "http://fhir", "alice", -37L);

        // NULL is what the constraint allows for "not known"; 0 would claim the evaluation was instantaneous.
        assertThat(saved().getEvaluationDurationMs()).isNull();
        assertThat(saved().getMeasureName()).isEqualTo("M1");
    }

    @Test
    void aRealDuration_includingZero_isStoredAsItIs() {
        service.saveReport(result(), null, "http://fhir", "alice", 0L);
        assertThat(saved().getEvaluationDurationMs()).isZero();
    }

    @Test
    void aPositiveDuration_isStoredAsItIs() {
        service.saveReport(result(), null, "http://fhir", "alice", 1234L);
        assertThat(saved().getEvaluationDurationMs()).isEqualTo(1234L);
    }
}
