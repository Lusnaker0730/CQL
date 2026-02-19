package com.cqlplatform.service.cds;

import com.cqlplatform.entity.CdsServiceAnalyticsEntity;
import com.cqlplatform.model.cds.CdsServiceAnalyticsDTO;
import com.cqlplatform.repository.CdsFeedbackRepository;
import com.cqlplatform.repository.CdsServiceAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cqlplatform.model.cds.CdsConstants;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CdsAnalyticsService {

    private final CdsServiceAnalyticsRepository analyticsRepository;
    private final CdsFeedbackRepository feedbackRepository;

    @Transactional
    public void recordInvocation(String serviceId, long responseTimeMs, boolean success) {
        CdsServiceAnalyticsEntity analytics = analyticsRepository
                .findCurrentPeriodByServiceId(serviceId)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    return CdsServiceAnalyticsEntity.builder()
                            .serviceId(serviceId)
                            .invocationCount(0L)
                            .errorCount(0L)
                            .totalResponseTimeMs(0L)
                            .periodStart(now.truncatedTo(ChronoUnit.DAYS))
                            .build();
                });

        analytics.setInvocationCount(analytics.getInvocationCount() + 1);
        analytics.setTotalResponseTimeMs(analytics.getTotalResponseTimeMs() + responseTimeMs);
        analytics.setLastInvokedAt(LocalDateTime.now());

        if (!success) {
            analytics.setErrorCount(analytics.getErrorCount() + 1);
        }

        analyticsRepository.save(analytics);
    }

    @Transactional(readOnly = true)
    public CdsServiceAnalyticsDTO getServiceAnalytics(String serviceId) {
        CdsServiceAnalyticsEntity analytics = analyticsRepository
                .findCurrentPeriodByServiceId(serviceId)
                .orElse(null);

        long acceptedCount = feedbackRepository.countByServiceIdAndOutcome(serviceId, CdsConstants.FEEDBACK_ACCEPTED);
        long overriddenCount = feedbackRepository.countByServiceIdAndOutcome(serviceId, CdsConstants.FEEDBACK_OVERRIDDEN);

        if (analytics == null) {
            return CdsServiceAnalyticsDTO.builder()
                    .serviceId(serviceId)
                    .invocationCount(0)
                    .errorCount(0)
                    .avgResponseTimeMs(0)
                    .errorRate(0)
                    .feedbackAcceptedCount(acceptedCount)
                    .feedbackOverriddenCount(overriddenCount)
                    .build();
        }

        return buildDto(analytics, acceptedCount, overriddenCount);
    }

    @Transactional(readOnly = true)
    public List<CdsServiceAnalyticsDTO> getAllServiceAnalytics() {
        List<CdsServiceAnalyticsEntity> allAnalytics = analyticsRepository.findAllCurrentPeriod();

        return allAnalytics.stream()
                .map(analytics -> {
                    long acceptedCount = feedbackRepository.countByServiceIdAndOutcome(
                            analytics.getServiceId(), CdsConstants.FEEDBACK_ACCEPTED);
                    long overriddenCount = feedbackRepository.countByServiceIdAndOutcome(
                            analytics.getServiceId(), CdsConstants.FEEDBACK_OVERRIDDEN);
                    return buildDto(analytics, acceptedCount, overriddenCount);
                })
                .collect(Collectors.toList());
    }

    private CdsServiceAnalyticsDTO buildDto(CdsServiceAnalyticsEntity analytics,
                                            long acceptedCount, long overriddenCount) {
        double avgResponseTime = analytics.getInvocationCount() > 0
                ? (double) analytics.getTotalResponseTimeMs() / analytics.getInvocationCount()
                : 0;

        double errorRate = analytics.getInvocationCount() > 0
                ? (double) analytics.getErrorCount() / analytics.getInvocationCount() * 100
                : 0;

        return CdsServiceAnalyticsDTO.builder()
                .serviceId(analytics.getServiceId())
                .invocationCount(analytics.getInvocationCount())
                .errorCount(analytics.getErrorCount())
                .avgResponseTimeMs(Math.round(avgResponseTime * 100.0) / 100.0)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .lastInvokedAt(analytics.getLastInvokedAt())
                .feedbackAcceptedCount(acceptedCount)
                .feedbackOverriddenCount(overriddenCount)
                .build();
    }
}
