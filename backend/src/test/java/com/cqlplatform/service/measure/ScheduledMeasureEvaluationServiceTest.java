package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureScheduleEntity;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureScheduleRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Phase 2 — scheduled evaluations run off the request thread (no TenantContext). The scheduler
 * must resolve the measure's tenant and run the whole evaluation under it, so the tenant-scoped
 * getById + report save + parallel fan-out use the measure's tenant, not the default.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledMeasureEvaluationServiceTest {

    @Mock private MeasureScheduleRepository scheduleRepository;
    @Mock private MeasureDefinitionService definitionService;
    @Mock private MeasureEvaluationService evaluationService;
    @Mock private MeasureDefinitionRepository measureDefinitionRepository;

    @InjectMocks private ScheduledMeasureEvaluationService service;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void scheduledEvaluation_runsUnderMeasureTenant_andRestoresAfter() {
        ReflectionTestUtils.setField(service, "schedulingEnabled", true);

        MeasureScheduleEntity schedule = MeasureScheduleEntity.builder()
                .id(1L)
                .measureDefinitionId(5L)
                .periodType("quarterly")
                .enabled(true)
                .nextRunAt(LocalDateTime.now().minusHours(1)) // due
                .fhirServerUrl("http://fhir.example/fhir")
                .build();
        when(scheduleRepository.findByEnabledTrue()).thenReturn(List.of(schedule));

        // Measure belongs to tenant 42 (resolved via the unscoped system lookup).
        MeasureDefinitionEntity measureEntity = MeasureDefinitionEntity.builder()
                .id(5L).name("M").version("1.0").tenantId(42L).build();
        when(measureDefinitionRepository.findById(5L)).thenReturn(Optional.of(measureEntity));
        when(definitionService.getById(5L)).thenReturn(Optional.of(
                MeasureDefinition.builder().id(5L).cqlContent("library M version '1.0'").build()));

        // Capture the tenant visible while evaluateMeasure runs.
        AtomicReference<Long> tenantDuringEval = new AtomicReference<>();
        when(evaluationService.evaluateMeasure(any(), eq(5L), any())).thenAnswer(inv -> {
            tenantDuringEval.set(TenantContext.getCurrentTenantId());
            return MeasureEvaluationResult.builder().status("complete").build();
        });

        service.checkAndRunSchedules();

        assertThat(tenantDuringEval.get()).isEqualTo(42L);       // ran under the measure's tenant
        assertThat(TenantContext.getCurrentTenantId()).isNull(); // restored after the run
    }
}
