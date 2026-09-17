package com.cqlplatform.service.cds;

import com.cqlplatform.entity.CdsServiceAnalyticsEntity;
import com.cqlplatform.model.cds.CdsServiceAnalyticsDTO;
import com.cqlplatform.repository.CdsFeedbackRepository;
import com.cqlplatform.repository.CdsServiceAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CdsAnalyticsServiceTest {

    @Mock
    private CdsServiceAnalyticsRepository analyticsRepository;
    @Mock
    private CdsFeedbackRepository feedbackRepository;
    @Mock
    private com.cqlplatform.repository.CdsServiceConfigRepository configRepository;
    @Mock
    private com.cqlplatform.repository.TenantRepository tenantRepository;

    // BUG-137: analytics/feedback are gated on the parent service config being in the
    // caller's tenant. With TenantContext set, effectiveTenantId() never hits tenantRepository.
    private static final Long TENANT = 7L;

    private CdsAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new CdsAnalyticsService(analyticsRepository, feedbackRepository,
                configRepository, tenantRepository);
        com.cqlplatform.security.TenantContext.setCurrentTenantId(TENANT);
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        com.cqlplatform.security.TenantContext.clear();
    }

    @Test
    void recordInvocation_newService_shouldCreateAnalyticsRecord() {
        when(analyticsRepository.findCurrentPeriodByServiceId("svc-1"))
                .thenReturn(Optional.empty());
        when(analyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        analyticsService.recordInvocation("svc-1", 150, true);

        ArgumentCaptor<CdsServiceAnalyticsEntity> captor = ArgumentCaptor.forClass(CdsServiceAnalyticsEntity.class);
        verify(analyticsRepository).save(captor.capture());

        CdsServiceAnalyticsEntity saved = captor.getValue();
        assertThat(saved.getServiceId()).isEqualTo("svc-1");
        assertThat(saved.getInvocationCount()).isEqualTo(1);
        assertThat(saved.getErrorCount()).isEqualTo(0);
        assertThat(saved.getTotalResponseTimeMs()).isEqualTo(150);
    }

    @Test
    void recordInvocation_existingService_shouldIncrementCounts() {
        CdsServiceAnalyticsEntity existing = CdsServiceAnalyticsEntity.builder()
                .serviceId("svc-1").invocationCount(5L).errorCount(1L)
                .totalResponseTimeMs(500L).periodStart(LocalDateTime.now())
                .build();

        when(analyticsRepository.findCurrentPeriodByServiceId("svc-1"))
                .thenReturn(Optional.of(existing));
        when(analyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        analyticsService.recordInvocation("svc-1", 200, true);

        assertThat(existing.getInvocationCount()).isEqualTo(6);
        assertThat(existing.getErrorCount()).isEqualTo(1);
        assertThat(existing.getTotalResponseTimeMs()).isEqualTo(700);
    }

    @Test
    void recordInvocation_error_shouldIncrementErrorCount() {
        CdsServiceAnalyticsEntity existing = CdsServiceAnalyticsEntity.builder()
                .serviceId("svc-1").invocationCount(5L).errorCount(1L)
                .totalResponseTimeMs(500L).periodStart(LocalDateTime.now())
                .build();

        when(analyticsRepository.findCurrentPeriodByServiceId("svc-1"))
                .thenReturn(Optional.of(existing));
        when(analyticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        analyticsService.recordInvocation("svc-1", 100, false);

        assertThat(existing.getInvocationCount()).isEqualTo(6);
        assertThat(existing.getErrorCount()).isEqualTo(2);
    }

    @Test
    void getServiceAnalytics_shouldReturnDTO() {
        // BUG-137: getServiceAnalytics gates on the parent service config being in the caller's tenant.
        when(configRepository.findByIdAndTenantIdWithPrefetch("svc-1", TENANT))
                .thenReturn(Optional.of(com.cqlplatform.entity.CdsServiceConfigEntity.builder()
                        .id("svc-1").tenantId(TENANT).build()));
        CdsServiceAnalyticsEntity analytics = CdsServiceAnalyticsEntity.builder()
                .serviceId("svc-1").invocationCount(10L).errorCount(2L)
                .totalResponseTimeMs(1500L).periodStart(LocalDateTime.now())
                .lastInvokedAt(LocalDateTime.now())
                .build();

        when(analyticsRepository.findCurrentPeriodByServiceId("svc-1"))
                .thenReturn(Optional.of(analytics));
        when(feedbackRepository.countByServiceIdAndOutcome("svc-1", "accepted")).thenReturn(5L);
        when(feedbackRepository.countByServiceIdAndOutcome("svc-1", "overridden")).thenReturn(2L);

        CdsServiceAnalyticsDTO dto = analyticsService.getServiceAnalytics("svc-1");

        assertThat(dto.getServiceId()).isEqualTo("svc-1");
        assertThat(dto.getInvocationCount()).isEqualTo(10);
        assertThat(dto.getErrorCount()).isEqualTo(2);
        assertThat(dto.getAvgResponseTimeMs()).isEqualTo(150.0);
        assertThat(dto.getErrorRate()).isEqualTo(20.0);
        assertThat(dto.getFeedbackAcceptedCount()).isEqualTo(5);
        assertThat(dto.getFeedbackOverriddenCount()).isEqualTo(2);
    }

    @Test
    void getServiceAnalytics_noData_shouldReturnEmptyDTO() {
        // BUG-137: getServiceAnalytics gates on the parent service config being in the caller's tenant.
        when(configRepository.findByIdAndTenantIdWithPrefetch("svc-new", TENANT))
                .thenReturn(Optional.of(com.cqlplatform.entity.CdsServiceConfigEntity.builder()
                        .id("svc-new").tenantId(TENANT).build()));
        when(analyticsRepository.findCurrentPeriodByServiceId("svc-new"))
                .thenReturn(Optional.empty());
        when(feedbackRepository.countByServiceIdAndOutcome("svc-new", "accepted")).thenReturn(0L);
        when(feedbackRepository.countByServiceIdAndOutcome("svc-new", "overridden")).thenReturn(0L);

        CdsServiceAnalyticsDTO dto = analyticsService.getServiceAnalytics("svc-new");

        assertThat(dto.getServiceId()).isEqualTo("svc-new");
        assertThat(dto.getInvocationCount()).isEqualTo(0);
        assertThat(dto.getErrorRate()).isEqualTo(0);
    }
}
