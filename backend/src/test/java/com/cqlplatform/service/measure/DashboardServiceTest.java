package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.entity.MeasureThresholdEntity;
import com.cqlplatform.model.measure.EnhancedDashboardData;
import com.cqlplatform.model.measure.QualityReport;
import com.cqlplatform.model.measure.ThresholdAlert;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReportRepository;
import com.cqlplatform.repository.MeasureThresholdRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private MeasureDefinitionRepository definitionRepository;

    @Mock
    private MeasureReportRepository reportRepository;

    @Mock
    private MeasureThresholdRepository thresholdRepository;

    @InjectMocks
    private DashboardService service;

    private MeasureDefinitionEntity createMeasure(Long id, String name, String status, String scoring, String department) {
        return MeasureDefinitionEntity.builder()
                .id(id)
                .name(name)
                .status(status)
                .scoringType(scoring)
                .department(department)
                .build();
    }

    private MeasureReportEntity createReport(Long id, Long measureId, String measureName, Double score, String dept) {
        return MeasureReportEntity.builder()
                .id(id)
                .measureDefinitionId(measureId)
                .measureName(measureName)
                .measureScore(score)
                .department(dept)
                .status("complete")
                .createdAt(LocalDateTime.now().minusDays(id))
                .build();
    }

    // ===== getEnhancedDashboard =====

    @Test
    void getEnhancedDashboard_noMeasures_shouldReturnEmptyDashboard() {
        when(definitionRepository.findAll()).thenReturn(List.of());
        when(reportRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(thresholdRepository.findByActiveTrue()).thenReturn(List.of());

        EnhancedDashboardData result = service.getEnhancedDashboard(null);

        assertThat(result.getTotalMeasures()).isEqualTo(0);
        assertThat(result.getByStatus()).isEmpty();
        assertThat(result.getByScoring()).isEmpty();
    }

    @Test
    void getEnhancedDashboard_withMeasures_shouldAggregateCorrectly() {
        when(definitionRepository.findAll()).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology"),
                createMeasure(2L, "M2", "draft", "proportion", "oncology"),
                createMeasure(3L, "M3", "active", "ratio", "cardiology")
        ));
        when(reportRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(thresholdRepository.findByActiveTrue()).thenReturn(List.of());

        EnhancedDashboardData result = service.getEnhancedDashboard(null);

        assertThat(result.getTotalMeasures()).isEqualTo(3);
        assertThat(result.getByStatus()).containsEntry("active", 2);
        assertThat(result.getByStatus()).containsEntry("draft", 1);
        assertThat(result.getByScoring()).containsEntry("proportion", 2);
        assertThat(result.getByScoring()).containsEntry("ratio", 1);
    }

    @Test
    void getEnhancedDashboard_withDepartmentFilter_shouldFilterMeasures() {
        when(definitionRepository.findByDepartment("cardiology")).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology")
        ));
        when(reportRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(thresholdRepository.findByDepartmentAndActiveTrue("cardiology")).thenReturn(List.of());
        // computeDepartmentScores still calls findAll
        when(definitionRepository.findAll()).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology")
        ));

        EnhancedDashboardData result = service.getEnhancedDashboard("cardiology");

        assertThat(result.getTotalMeasures()).isEqualTo(1);
    }

    // ===== getTrends =====

    @Test
    void getTrends_shouldReturnChronologicalOrder() {
        MeasureReportEntity r1 = createReport(2L, 10L, "M1", 0.8, null);
        r1.setCreatedAt(LocalDateTime.now().minusDays(2));
        r1.setPeriodStart(null);
        r1.setPeriodEnd(null);
        MeasureReportEntity r2 = createReport(1L, 10L, "M1", 0.9, null);
        r2.setCreatedAt(LocalDateTime.now().minusDays(1));
        r2.setPeriodStart(null);
        r2.setPeriodEnd(null);

        // findRecentByOptionalMeasure returns DESC order (r2 first, r1 second)
        // Must be mutable list since service calls Collections.reverse()
        when(reportRepository.findRecentByOptionalMeasure(eq((Long) null), any(PageRequest.class)))
                .thenReturn(new java.util.ArrayList<>(List.of(r2, r1)));

        List<EnhancedDashboardData.TrendDataPoint> trends = service.getTrends(null, "monthly", 10);

        assertThat(trends).hasSize(2);
        // After reverse: r1 first (older), r2 second (newer)
        assertThat(trends.get(0).getScore()).isEqualTo(0.8);
        assertThat(trends.get(1).getScore()).isEqualTo(0.9);
    }

    @Test
    void getTrends_withMeasureIdFilter_shouldFilterByMeasure() {
        MeasureReportEntity r1 = createReport(1L, 10L, "M1", 0.8, null);
        r1.setCreatedAt(LocalDateTime.now());
        r1.setPeriodStart(null);
        r1.setPeriodEnd(null);

        when(reportRepository.findRecentByOptionalMeasure(eq(10L), any(PageRequest.class)))
                .thenReturn(new java.util.ArrayList<>(List.of(r1)));

        List<EnhancedDashboardData.TrendDataPoint> trends = service.getTrends(10L, "monthly", 10);

        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).getMeasureName()).isEqualTo("M1");
    }

    // ===== getDepartmentDrilldown =====

    @Test
    void getDepartmentDrilldown_shouldGroupByMeasure() {
        when(definitionRepository.findByDepartment("cardiology")).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology")
        ));
        MeasureReportEntity report = createReport(1L, 1L, "M1", 0.75, "cardiology");
        when(reportRepository.findByDepartmentOrderByCreatedAtDesc("cardiology"))
                .thenReturn(List.of(report));

        Map<String, Object> result = service.getDepartmentDrilldown("cardiology");

        assertThat(result.get("department")).isEqualTo("cardiology");
        assertThat(result.get("measureCount")).isEqualTo(1);
        assertThat(result.get("reportCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Double> scores = (Map<String, Double>) result.get("latestScores");
        assertThat(scores).containsEntry("M1", 0.75);
    }

    @Test
    void getDepartmentDrilldown_shouldPickLatestScorePerMeasure() {
        when(definitionRepository.findByDepartment("cardiology")).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology")
        ));

        // findByDepartmentOrderByCreatedAtDesc returns newest first
        MeasureReportEntity newer = createReport(2L, 1L, "M1", 0.9, "cardiology");
        newer.setCreatedAt(LocalDateTime.now());
        MeasureReportEntity older = createReport(1L, 1L, "M1", 0.5, "cardiology");
        older.setCreatedAt(LocalDateTime.now().minusDays(10));

        when(reportRepository.findByDepartmentOrderByCreatedAtDesc("cardiology"))
                .thenReturn(List.of(newer, older));

        Map<String, Object> result = service.getDepartmentDrilldown("cardiology");

        @SuppressWarnings("unchecked")
        Map<String, Double> scores = (Map<String, Double>) result.get("latestScores");
        assertThat(scores.get("M1")).isEqualTo(0.9);
    }

    // ===== getAlerts =====

    @Test
    void getAlerts_shouldReturnThresholdViolations() {
        when(thresholdRepository.findByActiveTrue()).thenReturn(List.of(
                MeasureThresholdEntity.builder()
                        .measureDefinitionId(1L)
                        .thresholdType("target")
                        .thresholdValue(0.8)
                        .comparisonOperator(">=")
                        .active(true)
                        .build()
        ));

        MeasureReportEntity report = createReport(1L, 1L, "M1", 0.5, null);
        report.setCreatedAt(LocalDateTime.now());
        when(reportRepository.findLatestByMeasureDefinitionId(1L)).thenReturn(List.of(report));
        when(definitionRepository.findById(1L)).thenReturn(Optional.of(
                createMeasure(1L, "M1", "active", "proportion", null)
        ));

        List<ThresholdAlert> alerts = service.getAlerts(null);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMeasureName()).isEqualTo("M1");
        assertThat(alerts.get(0).getActualScore()).isEqualTo(0.5);
    }

    // ===== setThreshold =====

    @Test
    void setThreshold_shouldSaveAndReturn() {
        MeasureThresholdEntity threshold = MeasureThresholdEntity.builder()
                .thresholdType("target")
                .thresholdValue(0.8)
                .comparisonOperator(">=")
                .build();
        when(thresholdRepository.save(threshold)).thenReturn(threshold);

        MeasureThresholdEntity result = service.setThreshold(1L, threshold);

        assertThat(result.getMeasureDefinitionId()).isEqualTo(1L);
    }

    // ===== generateReport =====

    @Test
    void generateReport_shouldReturnReportWithScores() {
        MeasureDefinitionEntity measure = createMeasure(1L, "M1", "active", "proportion", null);
        when(definitionRepository.findAll()).thenReturn(List.of(measure));
        when(thresholdRepository.findByActiveTrue()).thenReturn(List.of());

        MeasureReportEntity report = createReport(1L, 1L, "M1", 0.85, null);
        report.setCreatedAt(LocalDateTime.now());
        when(reportRepository.findLatestByMeasureDefinitionId(1L)).thenReturn(List.of(report));

        QualityReport result = service.generateReport("monthly", null);

        assertThat(result.getReportType()).isEqualTo("monthly");
        assertThat(result.getTotalMeasures()).isEqualTo(1);
        assertThat(result.getMeasureScores()).hasSize(1);
    }
}
