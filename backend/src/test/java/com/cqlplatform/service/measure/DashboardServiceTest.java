package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.entity.MeasureReportGroupEntity;
import com.cqlplatform.entity.MeasureThresholdEntity;
import com.cqlplatform.model.measure.EnhancedDashboardData;
import com.cqlplatform.model.measure.QualityReport;
import com.cqlplatform.model.measure.ThresholdAlert;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReportGroupRepository;
import com.cqlplatform.repository.MeasureReportRepository;
import com.cqlplatform.repository.MeasureThresholdRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private MeasureDefinitionRepository definitionRepository;

    @Mock
    private MeasureReportRepository reportRepository;

    @Mock
    private MeasureReportGroupRepository reportGroupRepository;

    @Mock
    private MeasureThresholdRepository thresholdRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private DashboardService service;

    // BUG-136: every dashboard read is tenant-scoped. With TenantContext set,
    // effectiveTenantId() returns early and never touches tenantRepository.
    private static final Long TENANT = 7L;

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

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
        when(definitionRepository.findByTenantId(TENANT)).thenReturn(List.of());
        when(reportRepository.findTop10ByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
        when(thresholdRepository.findActiveByTenantId(TENANT)).thenReturn(List.of());

        EnhancedDashboardData result = service.getEnhancedDashboard(null);

        assertThat(result.getTotalMeasures()).isEqualTo(0);
        assertThat(result.getByStatus()).isEmpty();
        assertThat(result.getByScoring()).isEmpty();
    }

    @Test
    void getEnhancedDashboard_withMeasures_shouldAggregateCorrectly() {
        when(definitionRepository.findByTenantId(TENANT)).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology"),
                createMeasure(2L, "M2", "draft", "proportion", "oncology"),
                createMeasure(3L, "M3", "active", "ratio", "cardiology")
        ));
        when(reportRepository.findTop10ByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
        when(thresholdRepository.findActiveByTenantId(TENANT)).thenReturn(List.of());

        EnhancedDashboardData result = service.getEnhancedDashboard(null);

        assertThat(result.getTotalMeasures()).isEqualTo(3);
        assertThat(result.getByStatus()).containsEntry("active", 2);
        assertThat(result.getByStatus()).containsEntry("draft", 1);
        assertThat(result.getByScoring()).containsEntry("proportion", 2);
        assertThat(result.getByScoring()).containsEntry("ratio", 1);
    }

    @Test
    void getEnhancedDashboard_withDepartmentFilter_shouldFilterMeasures() {
        when(definitionRepository.findByTenantIdAndDepartment(TENANT, "cardiology")).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology")
        ));
        when(reportRepository.findTop10ByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
        when(thresholdRepository.findActiveByTenantIdAndDepartment(TENANT, "cardiology")).thenReturn(List.of());
        // computeDepartmentScores still calls findAll
        when(definitionRepository.findByTenantId(TENANT)).thenReturn(List.of(
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
        when(reportRepository.findRecentByOptionalMeasure(eq(TENANT), eq((Long) null), any(PageRequest.class)))
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

        when(reportRepository.findRecentByOptionalMeasure(eq(TENANT), eq(10L), any(PageRequest.class)))
                .thenReturn(new java.util.ArrayList<>(List.of(r1)));

        List<EnhancedDashboardData.TrendDataPoint> trends = service.getTrends(10L, "monthly", 10);

        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).getMeasureName()).isEqualTo("M1");
    }

    @Test
    void getTrends_shouldPropagateScoringTypeAndUnit() {
        // PAT-124: trend points must carry scoringType + unit so the dashboard can pick
        // the right Y-axis (% vs raw clinical value) and unit label per measure family.
        MeasureReportEntity cv = createReport(1L, 10L, "HbA1c", 5.6, null);
        cv.setScoringType("continuous-variable");
        cv.setCreatedAt(LocalDateTime.now());
        cv.setPeriodStart(null);
        cv.setPeriodEnd(null);

        MeasureReportGroupEntity cvGroup = MeasureReportGroupEntity.builder()
                .measureReportId(1L)
                .groupId("g1")
                .measureScore(5.6)
                .measureScoreUnit("mmol/L")
                .build();

        when(reportRepository.findRecentByOptionalMeasure(eq(TENANT), eq((Long) null), any(PageRequest.class)))
                .thenReturn(new java.util.ArrayList<>(List.of(cv)));
        when(reportGroupRepository.findByMeasureReportIdOrderByOrdinalAsc(1L))
                .thenReturn(List.of(cvGroup));

        List<EnhancedDashboardData.TrendDataPoint> trends = service.getTrends(null, "monthly", 10);

        assertThat(trends).hasSize(1);
        assertThat(trends.get(0).getScoringType()).isEqualTo("continuous-variable");
        assertThat(trends.get(0).getUnit()).isEqualTo("mmol/L");
    }

    @Test
    void getEnhancedDashboard_departmentAverages_shouldExcludeContinuousVariable() {
        // PAT-124: department averaging mixed proportion% with CV raw values (e.g. HbA1c=5.6)
        // and produced nonsense numbers like 42.6. Only proportion-scale measures should
        // contribute to a department average.
        MeasureDefinitionEntity proportion = createMeasure(1L, "M1", "active", "proportion", "endo");
        MeasureDefinitionEntity cv = createMeasure(2L, "HbA1c", "active", "continuous-variable", "endo");
        when(definitionRepository.findByTenantId(TENANT)).thenReturn(List.of(proportion, cv));
        when(reportRepository.findTop10ByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
        when(thresholdRepository.findActiveByTenantId(TENANT)).thenReturn(List.of());

        MeasureReportEntity propReport = createReport(1L, 1L, "M1", 85.0, "endo");
        // CV must NOT be queried — verifies it's skipped before hitting the repo
        when(reportRepository.findLatestByMeasureDefinitionId(TENANT, 1L)).thenReturn(List.of(propReport));

        EnhancedDashboardData result = service.getEnhancedDashboard(null);

        assertThat(result.getDepartmentScores()).containsEntry("endo", 85.0);
        // Sanity: if CV had been mixed in, the average would be (85 + 5.6) / 2 = 45.3
        assertThat(result.getDepartmentScores().get("endo")).isNotEqualTo(45.3);
    }

    @Test
    void isProportionScaleScoring_classifiesScoringTypes() {
        assertThat(DashboardService.isProportionScaleScoring("proportion")).isTrue();
        assertThat(DashboardService.isProportionScaleScoring("ratio")).isTrue();
        assertThat(DashboardService.isProportionScaleScoring("composite")).isTrue();
        assertThat(DashboardService.isProportionScaleScoring(null)).isTrue();
        assertThat(DashboardService.isProportionScaleScoring("continuous-variable")).isFalse();
        assertThat(DashboardService.isProportionScaleScoring("cohort")).isFalse();
    }

    // ===== getDepartmentDrilldown =====

    @Test
    void getDepartmentDrilldown_shouldGroupByMeasure() {
        when(definitionRepository.findByTenantIdAndDepartment(TENANT, "cardiology")).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology")
        ));
        MeasureReportEntity report = createReport(1L, 1L, "M1", 0.75, "cardiology");
        when(reportRepository.findByTenantIdAndDepartmentOrderByCreatedAtDesc(TENANT, "cardiology"))
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
        when(definitionRepository.findByTenantIdAndDepartment(TENANT, "cardiology")).thenReturn(List.of(
                createMeasure(1L, "M1", "active", "proportion", "cardiology")
        ));

        // findByDepartmentOrderByCreatedAtDesc returns newest first
        MeasureReportEntity newer = createReport(2L, 1L, "M1", 0.9, "cardiology");
        newer.setCreatedAt(LocalDateTime.now());
        MeasureReportEntity older = createReport(1L, 1L, "M1", 0.5, "cardiology");
        older.setCreatedAt(LocalDateTime.now().minusDays(10));

        when(reportRepository.findByTenantIdAndDepartmentOrderByCreatedAtDesc(TENANT, "cardiology"))
                .thenReturn(List.of(newer, older));

        Map<String, Object> result = service.getDepartmentDrilldown("cardiology");

        @SuppressWarnings("unchecked")
        Map<String, Double> scores = (Map<String, Double>) result.get("latestScores");
        assertThat(scores.get("M1")).isEqualTo(0.9);
    }

    // ===== getAlerts =====

    @Test
    void getAlerts_shouldReturnThresholdViolations() {
        when(thresholdRepository.findActiveByTenantId(TENANT)).thenReturn(List.of(
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
        when(reportRepository.findLatestByMeasureDefinitionId(TENANT, 1L)).thenReturn(List.of(report));
        when(definitionRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(
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
        when(definitionRepository.findByTenantId(TENANT)).thenReturn(List.of(measure));
        when(thresholdRepository.findActiveByTenantId(TENANT)).thenReturn(List.of());

        MeasureReportEntity report = createReport(1L, 1L, "M1", 0.85, null);
        report.setCreatedAt(LocalDateTime.now());
        when(reportRepository.findLatestByMeasureDefinitionId(TENANT, 1L)).thenReturn(List.of(report));

        QualityReport result = service.generateReport("monthly", null);

        assertThat(result.getReportType()).isEqualTo("monthly");
        assertThat(result.getTotalMeasures()).isEqualTo(1);
        assertThat(result.getMeasureScores()).hasSize(1);
    }

    // ===== Tenant boundary (BUG-136) =====
    //
    // The dashboard endpoints have NO controller gate beyond authenticated(), so
    // effectiveTenantId() inside this service is the entire cross-tenant boundary:
    // /dashboard/enhanced, /trends, /department/{code}, /alerts and /report were all
    // reachable by any authenticated user of any tenant and read findAll() straight out.
    // These lock the tenant into each of the three repositories the service touches.

    @Test
    void getEnhancedDashboard_shouldScopeMeasuresAndReportsAndThresholdsToTenant() {
        when(definitionRepository.findByTenantId(TENANT)).thenReturn(List.of());
        when(reportRepository.findTop10ByTenantIdOrderByCreatedAtDesc(TENANT)).thenReturn(List.of());
        when(thresholdRepository.findActiveByTenantId(TENANT)).thenReturn(List.of());

        service.getEnhancedDashboard(null);

        // atLeastOnce: getEnhancedDashboard reads measures directly AND again via
        // computeDepartmentScores (the old findAll() at line 352) — both scoped, which is
        // exactly what we want to assert.
        verify(definitionRepository, atLeastOnce()).findByTenantId(TENANT);
        verify(reportRepository).findTop10ByTenantIdOrderByCreatedAtDesc(TENANT);
        verify(thresholdRepository, atLeastOnce()).findActiveByTenantId(TENANT);
    }

    @Test
    void getTrends_shouldScopeReportsToTenant() {
        when(reportRepository.findRecentByOptionalMeasure(eq(TENANT), eq((Long) null), any(PageRequest.class)))
                .thenReturn(new ArrayList<>());

        service.getTrends(null, "monthly", 10);

        verify(reportRepository).findRecentByOptionalMeasure(eq(TENANT), eq((Long) null), any(PageRequest.class));
    }

    @Test
    void getDepartmentDashboard_shouldScopeToTenant() {
        when(definitionRepository.findByTenantIdAndDepartment(TENANT, "cardiology")).thenReturn(List.of());
        when(reportRepository.findByTenantIdAndDepartmentOrderByCreatedAtDesc(TENANT, "cardiology"))
                .thenReturn(List.of());

        service.getDepartmentDrilldown("cardiology");

        verify(definitionRepository).findByTenantIdAndDepartment(TENANT, "cardiology");
        verify(reportRepository).findByTenantIdAndDepartmentOrderByCreatedAtDesc(TENANT, "cardiology");
    }

    @Test
    void getAlerts_shouldScopeThresholdsToTenant() {
        when(thresholdRepository.findActiveByTenantId(TENANT)).thenReturn(List.of());

        service.getAlerts(null);

        // measure_threshold has no tenant_id — this asserts the join-through-parent query
        // is the one being used, not the old platform-wide findByActiveTrue().
        verify(thresholdRepository).findActiveByTenantId(TENANT);
    }
}
