package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.entity.MeasureReportGroupEntity;
import com.cqlplatform.entity.MeasureThresholdEntity;
import com.cqlplatform.model.measure.EnhancedDashboardData;
import com.cqlplatform.model.measure.QualityReport;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.model.measure.ThresholdAlert;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReportGroupRepository;
import com.cqlplatform.repository.MeasureReportRepository;
import com.cqlplatform.repository.MeasureThresholdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final MeasureDefinitionRepository definitionRepository;
    private final MeasureReportRepository reportRepository;
    private final MeasureReportGroupRepository reportGroupRepository;
    private final MeasureThresholdRepository thresholdRepository;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    /**
     * Caller's tenant ?? default — see EhrConnectionService for the canonical pattern.
     *
     * <p>BUG-136: every read in this service goes through here. The dashboard endpoints have
     * no controller gate beyond authenticated(), so this is the only thing standing between a
     * clinic's user and every other tenant's measures, scores and thresholds.
     */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    /**
     * Whether a measure's score is meaningful to average / aggregate alongside other
     * measures of different types. Proportion / ratio / composite all live on a 0–100%
     * scale (or unitless ratio that the dashboard renders as %); continuous-variable
     * scores are raw clinical values (e.g. HbA1c=5.6 mmol/L) — averaging them with %
     * yields nonsense. Cohort produces patient counts, not scores. The trend chart and
     * department-average code use this rule to pick which scores to combine.
     */
    static boolean isProportionScaleScoring(String scoringType) {
        if (scoringType == null) return true;
        return switch (scoringType) {
            case ScoringTypeConstants.CONTINUOUS_VARIABLE,
                 ScoringTypeConstants.COHORT -> false;
            default -> true;
        };
    }

    @Transactional(readOnly = true)
    public EnhancedDashboardData getEnhancedDashboard(String department) {
        boolean hasDept = department != null && !department.isBlank();
        List<MeasureDefinitionEntity> measures = hasDept
                ? definitionRepository.findByTenantIdAndDepartment(effectiveTenantId(), department)
                : definitionRepository.findByTenantId(effectiveTenantId());

        Map<String, Integer> byStatus = new HashMap<>();
        Map<String, Integer> byScoring = new HashMap<>();
        for (MeasureDefinitionEntity m : measures) {
            byStatus.merge(m.getStatus() != null ? m.getStatus() : "draft", 1, Integer::sum);
            byScoring.merge(m.getScoringType() != null ? m.getScoringType() : "unknown", 1, Integer::sum);
        }

        // Recent evaluations — use DB ordering + limit instead of findAll()
        List<MeasureReportEntity> reports = reportRepository.findTop10ByTenantIdOrderByCreatedAtDesc(effectiveTenantId());

        List<EnhancedDashboardData.DashboardEvaluation> recentEvals = reports.stream()
                .map(r -> EnhancedDashboardData.DashboardEvaluation.builder()
                        .id(r.getId())
                        .measureName(r.getMeasureName())
                        .score(r.getMeasureScore())
                        .status(r.getStatus())
                        .department(r.getDepartment())
                        .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        // Threshold alerts
        List<ThresholdAlert> alerts = computeAlerts(department);

        // Department scores
        Map<String, Double> deptScores = computeDepartmentScores();

        return EnhancedDashboardData.builder()
                .totalMeasures(measures.size())
                .byStatus(byStatus)
                .byScoring(byScoring)
                .departmentScores(deptScores)
                .alerts(alerts)
                .recentEvaluations(recentEvals)
                .build();
    }

    @Transactional(readOnly = true)
    public List<EnhancedDashboardData.TrendDataPoint> getTrends(Long measureId, String periodType, int count) {
        // Use DB-level filtering + ordering + limit instead of findAll()
        List<MeasureReportEntity> reports = reportRepository.findRecentByOptionalMeasure(effectiveTenantId(),
                measureId, PageRequest.of(0, count));

        // Reverse to show chronological order
        Collections.reverse(reports);

        // Resolve display name: prefer MeasureDefinition.title, fallback to name, fallback to report.measureName
        Set<Long> defIds = reports.stream()
                .map(MeasureReportEntity::getMeasureDefinitionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> displayNameById = definitionRepository
                .findByTenantIdAndIdIn(effectiveTenantId(), defIds).stream()
                .collect(Collectors.toMap(
                        MeasureDefinitionEntity::getId,
                        d -> {
                            String title = d.getTitle();
                            if (title != null && !title.isBlank()) return title;
                            return d.getName() != null ? d.getName() : String.valueOf(d.getId());
                        }));

        // Pull units from the normalized group rows (a single report can have multiple
        // groups, but the per-measure unit is stable across groups in practice; we take
        // the first non-null one). Only meaningful for continuous-variable; left null
        // for proportion/ratio/cohort where score is dimensionless.
        Map<Long, String> unitByReportId = new HashMap<>();
        for (MeasureReportEntity r : reports) {
            if (r.getId() == null) continue;
            List<MeasureReportGroupEntity> groups = reportGroupRepository
                    .findByMeasureReportIdOrderByOrdinalAsc(r.getId());
            for (MeasureReportGroupEntity g : groups) {
                if (g.getMeasureScoreUnit() != null && !g.getMeasureScoreUnit().isBlank()) {
                    unitByReportId.put(r.getId(), g.getMeasureScoreUnit());
                    break;
                }
            }
        }

        return reports.stream()
                .map(r -> EnhancedDashboardData.TrendDataPoint.builder()
                        .period(formatPeriodLabel(r.getPeriodStart(), r.getPeriodEnd()))
                        .measureName(displayNameById.getOrDefault(r.getMeasureDefinitionId(), r.getMeasureName()))
                        .measureId(r.getMeasureDefinitionId())
                        .score(r.getMeasureScore())
                        .scoringType(r.getScoringType())
                        .unit(unitByReportId.get(r.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Format period label concisely based on the date range.
     * Same month → "Jan 2026", same year → "Jan-Mar 2026", cross-year → "Dec 25-Jan 26"
     */
    private String formatPeriodLabel(LocalDate start, LocalDate end) {
        if (start == null && end == null) return "?";
        if (start == null) return end.toString();
        if (end == null) return start.toString();
        String sMonth = start.getMonth().name().substring(0, 3);
        String eMonth = end.getMonth().name().substring(0, 3);
        if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
            return sMonth + " " + start.getYear();
        }
        if (start.getYear() == end.getYear()) {
            return sMonth + "-" + eMonth + " " + start.getYear();
        }
        return sMonth + " " + (start.getYear() % 100) + "-" + eMonth + " " + (end.getYear() % 100);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDepartmentDrilldown(String departmentCode) {
        // Use DB-level filtering instead of findAll() + stream filter
        List<MeasureDefinitionEntity> measures = definitionRepository.findByTenantIdAndDepartment(effectiveTenantId(), departmentCode);
        List<MeasureReportEntity> reports = reportRepository.findByTenantIdAndDepartmentOrderByCreatedAtDesc(effectiveTenantId(), departmentCode);

        // Latest score per measure
        Map<String, Double> latestScores = new LinkedHashMap<>();
        Map<Long, MeasureReportEntity> latestByMeasure = new LinkedHashMap<>();
        for (MeasureReportEntity r : reports) {
            Long mId = r.getMeasureDefinitionId();
            if (mId != null && r.getCreatedAt() != null) {
                latestByMeasure.putIfAbsent(mId, r); // Already sorted DESC, first is latest
            }
        }
        for (MeasureReportEntity r : latestByMeasure.values()) {
            if (r.getMeasureScore() != null) {
                latestScores.put(r.getMeasureName(), r.getMeasureScore());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("department", departmentCode);
        result.put("measureCount", measures.size());
        result.put("reportCount", reports.size());
        result.put("latestScores", latestScores);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ThresholdAlert> getAlerts(String department) {
        return computeAlerts(department);
    }

    @Transactional
    public MeasureThresholdEntity setThreshold(Long measureId, MeasureThresholdEntity threshold) {
        threshold.setMeasureDefinitionId(measureId);
        return thresholdRepository.save(threshold);
    }

    @Transactional(readOnly = true)
    public List<MeasureThresholdEntity> getThresholds(Long measureId) {
        return thresholdRepository.findByMeasureDefinitionIdAndActiveTrue(measureId);
    }

    @Transactional(readOnly = true)
    public QualityReport generateReport(String reportType, String department) {
        boolean hasDept = department != null && !department.isBlank();
        List<MeasureDefinitionEntity> measures = hasDept
                ? definitionRepository.findByTenantIdAndDepartment(effectiveTenantId(), department)
                : definitionRepository.findByTenantId(effectiveTenantId());

        List<MeasureThresholdEntity> allThresholds = thresholdRepository.findActiveByTenantId(effectiveTenantId());

        // Get latest scores
        Map<Long, Double> latestScores = getLatestScoresMap(measures);
        Map<Long, Double> targetMap = new HashMap<>();
        for (MeasureThresholdEntity t : allThresholds) {
            if ("target".equals(t.getThresholdType())) {
                targetMap.put(t.getMeasureDefinitionId(), t.getThresholdValue());
            }
        }

        List<QualityReport.MeasureScoreSummary> scoreSummaries = new ArrayList<>();
        int above = 0, below = 0;
        // Only average proportion-compatible measures (proportion, ratio, cohort)
        // Continuous-variable scores represent raw values (e.g., HbA1c 5.6),
        // not percentages, so mixing them with proportion scores is meaningless.
        double proportionTotal = 0;
        int proportionScored = 0;

        for (MeasureDefinitionEntity m : measures) {
            Double score = latestScores.get(m.getId());
            Double target = targetMap.get(m.getId());
            String scoring = m.getScoringType() != null ? m.getScoringType() : "proportion";
            String status = "no_data";
            if (score != null) {
                if (isProportionScaleScoring(scoring)) {
                    proportionScored++;
                    proportionTotal += score;
                }
                if (target != null) {
                    status = score >= target ? "above_target" : "below_target";
                    if (score >= target) above++;
                    else below++;
                }
            }
            scoreSummaries.add(QualityReport.MeasureScoreSummary.builder()
                    .measureId(m.getId())
                    .measureName(m.getName())
                    .score(score)
                    .status(status)
                    .targetThreshold(target)
                    .scoringType(scoring)
                    .build());
        }

        return QualityReport.builder()
                .reportType(reportType)
                .periodLabel(LocalDate.now().getMonth().name() + " " + LocalDate.now().getYear())
                .department(department)
                .totalMeasures(measures.size())
                .measuresAboveTarget(above)
                .measuresBelowTarget(below)
                .averageScore(proportionScored > 0 ? proportionTotal / proportionScored : 0)
                .measureScores(scoreSummaries)
                .departmentAverages(computeDepartmentScores())
                .build();
    }

    private List<ThresholdAlert> computeAlerts(String department) {
        List<MeasureThresholdEntity> thresholds = department != null
                ? thresholdRepository.findActiveByTenantIdAndDepartment(effectiveTenantId(), department)
                : thresholdRepository.findActiveByTenantId(effectiveTenantId());

        // Only load measure names for the measures that have thresholds
        Set<Long> measureIds = thresholds.stream()
                .map(MeasureThresholdEntity::getMeasureDefinitionId)
                .collect(Collectors.toSet());

        Map<Long, String> measureNames = new HashMap<>();
        Map<Long, Double> latestScores = new HashMap<>();
        for (Long mId : measureIds) {
            definitionRepository.findByIdAndTenantId(mId, effectiveTenantId()).ifPresent(m -> measureNames.put(m.getId(), m.getName()));
            List<MeasureReportEntity> latest = reportRepository.findLatestByMeasureDefinitionId(effectiveTenantId(), mId);
            if (!latest.isEmpty() && latest.get(0).getMeasureScore() != null) {
                latestScores.put(mId, latest.get(0).getMeasureScore());
            }
        }

        List<ThresholdAlert> alerts = new ArrayList<>();
        for (MeasureThresholdEntity t : thresholds) {
            Double score = latestScores.get(t.getMeasureDefinitionId());
            if (score == null) continue;

            boolean violated = isThresholdViolated(score, t.getThresholdValue(), t.getComparisonOperator());
            if (violated) {
                alerts.add(ThresholdAlert.builder()
                        .measureId(t.getMeasureDefinitionId())
                        .measureName(measureNames.getOrDefault(t.getMeasureDefinitionId(), "Unknown"))
                        .thresholdType(t.getThresholdType())
                        .thresholdValue(t.getThresholdValue())
                        .actualScore(score)
                        .comparisonOperator(t.getComparisonOperator())
                        .department(t.getDepartment())
                        .severity("critical".equals(t.getThresholdType()) ? "critical" : "warning")
                        .build());
            }
        }
        return alerts;
    }

    private boolean isThresholdViolated(double actual, double threshold, String operator) {
        return switch (operator) {
            case ">=" -> actual < threshold;
            case ">" -> actual <= threshold;
            case "<=" -> actual > threshold;
            case "<" -> actual >= threshold;
            default -> actual < threshold;
        };
    }

    /**
     * Get latest scores for a specific set of measures (avoids loading all reports).
     */
    private Map<Long, Double> getLatestScoresMap(List<MeasureDefinitionEntity> measures) {
        Map<Long, Double> scores = new HashMap<>();
        for (MeasureDefinitionEntity m : measures) {
            List<MeasureReportEntity> latest = reportRepository.findLatestByMeasureDefinitionId(effectiveTenantId(), m.getId());
            if (!latest.isEmpty() && latest.get(0).getMeasureScore() != null) {
                scores.put(m.getId(), latest.get(0).getMeasureScore());
            }
        }
        return scores;
    }

    /**
     * Average latest score per department, restricted to proportion-scale measures.
     * Mixing CV (raw clinical values) and cohort (patient counts) into the same average
     * as proportions used to produce nonsense numbers like 42.6% — see PAT-124.
     */
    private Map<String, Double> computeDepartmentScores() {
        record MeasureMeta(Long id, String department, String scoringType) {}

        List<MeasureMeta> measureMeta = new ArrayList<>();
        for (MeasureDefinitionEntity m : definitionRepository.findByTenantId(effectiveTenantId())) {
            if (m.getDepartment() == null) continue;
            measureMeta.add(new MeasureMeta(m.getId(), m.getDepartment(), m.getScoringType()));
        }

        Map<String, List<Double>> scoresByDept = new HashMap<>();
        for (MeasureMeta meta : measureMeta) {
            if (!isProportionScaleScoring(meta.scoringType())) continue;
            List<MeasureReportEntity> latest = reportRepository.findLatestByMeasureDefinitionId(effectiveTenantId(), meta.id());
            if (!latest.isEmpty() && latest.get(0).getMeasureScore() != null) {
                scoresByDept.computeIfAbsent(meta.department(), k -> new ArrayList<>())
                        .add(latest.get(0).getMeasureScore());
            }
        }

        Map<String, Double> averages = new LinkedHashMap<>();
        for (var entry : scoresByDept.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            averages.put(entry.getKey(), Math.round(avg * 100.0) / 100.0);
        }
        return averages;
    }
}
