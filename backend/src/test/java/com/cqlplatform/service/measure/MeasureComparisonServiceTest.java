package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureComparisonResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.GroupResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.PopulationResult;
import com.cqlplatform.model.measure.MeasureTrendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasureComparisonServiceTest {

    @Mock
    private MeasureReportService reportService;

    @InjectMocks
    private MeasureComparisonService service;

    private MeasureReportEntity createReport(Double score, int totalPatients) {
        MeasureReportEntity report = new MeasureReportEntity();
        report.setMeasureScore(score);
        report.setTotalPatients(totalPatients);
        report.setPeriodStart(LocalDate.of(2024, 1, 1));
        report.setPeriodEnd(LocalDate.of(2024, 6, 30));

        PopulationResult ipPop = PopulationResult.builder()
                .populationType("initial-population")
                .count(totalPatients)
                .build();

        GroupResult group = GroupResult.builder()
                .populations(List.of(ipPop))
                .build();

        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .groups(List.of(group))
                .build();
        report.setEvaluationResult(result);

        return report;
    }

    // ===== comparePeriods =====

    @Test
    void comparePeriods_bothPeriodsHaveData_shouldCalculateDelta() {
        LocalDate p1Start = LocalDate.of(2024, 1, 1);
        LocalDate p1End = LocalDate.of(2024, 6, 30);
        LocalDate p2Start = LocalDate.of(2024, 7, 1);
        LocalDate p2End = LocalDate.of(2024, 12, 31);

        when(reportService.getReportsForPeriodById(1L, p1Start, p1End))
                .thenReturn(List.of(createReport(0.70, 100)));
        when(reportService.getReportsForPeriodById(1L, p2Start, p2End))
                .thenReturn(List.of(createReport(0.85, 120)));

        MeasureComparisonResult result = service.comparePeriods(1L, "Test", p1Start, p1End, p2Start, p2End);

        assertThat(result.getMeasureName()).isEqualTo("Test");
        assertThat(result.getScoreDelta()).isCloseTo(0.15, within(0.001));
        assertThat(result.getTrend()).isEqualTo("stable"); // delta < 1.0 threshold
    }

    @Test
    void comparePeriods_largeImprovement_shouldShowImproving() {
        LocalDate p1Start = LocalDate.of(2024, 1, 1);
        LocalDate p1End = LocalDate.of(2024, 6, 30);
        LocalDate p2Start = LocalDate.of(2024, 7, 1);
        LocalDate p2End = LocalDate.of(2024, 12, 31);

        when(reportService.getReportsForPeriodById(1L, p1Start, p1End))
                .thenReturn(List.of(createReport(50.0, 100)));
        when(reportService.getReportsForPeriodById(1L, p2Start, p2End))
                .thenReturn(List.of(createReport(55.0, 120)));

        MeasureComparisonResult result = service.comparePeriods(1L, "Test", p1Start, p1End, p2Start, p2End);

        assertThat(result.getScoreDelta()).isCloseTo(5.0, within(0.01));
        assertThat(result.getTrend()).isEqualTo("improving");
    }

    @Test
    void comparePeriods_largeDecline_shouldShowDeclining() {
        LocalDate p1Start = LocalDate.of(2024, 1, 1);
        LocalDate p1End = LocalDate.of(2024, 6, 30);
        LocalDate p2Start = LocalDate.of(2024, 7, 1);
        LocalDate p2End = LocalDate.of(2024, 12, 31);

        when(reportService.getReportsForPeriodById(1L, p1Start, p1End))
                .thenReturn(List.of(createReport(80.0, 100)));
        when(reportService.getReportsForPeriodById(1L, p2Start, p2End))
                .thenReturn(List.of(createReport(70.0, 100)));

        MeasureComparisonResult result = service.comparePeriods(1L, "Test", p1Start, p1End, p2Start, p2End);

        assertThat(result.getScoreDelta()).isCloseTo(-10.0, within(0.01));
        assertThat(result.getTrend()).isEqualTo("declining");
    }

    @Test
    void comparePeriods_oneEmptyPeriod_shouldHaveNullDelta() {
        LocalDate p1Start = LocalDate.of(2024, 1, 1);
        LocalDate p1End = LocalDate.of(2024, 6, 30);
        LocalDate p2Start = LocalDate.of(2024, 7, 1);
        LocalDate p2End = LocalDate.of(2024, 12, 31);

        when(reportService.getReportsForPeriodById(1L, p1Start, p1End))
                .thenReturn(List.of(createReport(0.70, 100)));
        when(reportService.getReportsForPeriodById(1L, p2Start, p2End))
                .thenReturn(List.of()); // empty period

        MeasureComparisonResult result = service.comparePeriods(1L, "Test", p1Start, p1End, p2Start, p2End);

        assertThat(result.getScoreDelta()).isNull();
        assertThat(result.getTrend()).isEqualTo("stable");
    }

    // ===== getTrend =====

    @Test
    void getTrend_shouldReturnOrderedDataPoints() {
        MeasureReportEntity r1 = createReport(0.70, 100);
        r1.setPeriodStart(LocalDate.of(2024, 1, 1));
        r1.setPeriodEnd(LocalDate.of(2024, 3, 31));

        MeasureReportEntity r2 = createReport(0.80, 110);
        r2.setPeriodStart(LocalDate.of(2024, 4, 1));
        r2.setPeriodEnd(LocalDate.of(2024, 6, 30));

        when(reportService.getReportsByMeasureIdOrderByPeriod(1L))
                .thenReturn(List.of(r1, r2));

        MeasureTrendResult result = service.getTrend(1L, "Test", 5);

        assertThat(result.getMeasureName()).isEqualTo("Test");
        assertThat(result.getDataPoints()).hasSize(2);
        assertThat(result.getDataPoints().get(0).getScore()).isEqualTo(0.70);
        assertThat(result.getDataPoints().get(1).getScore()).isEqualTo(0.80);
    }

    @Test
    void getTrend_shouldLimitToPeriodCount() {
        MeasureReportEntity r1 = createReport(0.60, 90);
        MeasureReportEntity r2 = createReport(0.70, 100);
        MeasureReportEntity r3 = createReport(0.80, 110);

        when(reportService.getReportsByMeasureIdOrderByPeriod(1L))
                .thenReturn(List.of(r1, r2, r3));

        MeasureTrendResult result = service.getTrend(1L, "Test", 2);

        assertThat(result.getDataPoints()).hasSize(2);
    }
}
