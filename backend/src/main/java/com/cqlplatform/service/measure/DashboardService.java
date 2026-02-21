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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final MeasureThresholdRepository thresholdRepository;

    @Transactional(readOnly = true)
    public EnhancedDashboardData getEnhancedDashboard(String department) {
        List<MeasureDefinitionEntity> measures = definitionRepository.findAll();
        if (department != null && !department.isBlank()) {
            measures = measures.stream()
                    .filter(m -> department.equals(m.getDepartment()))
                    .collect(Collectors.toList());
        }

        Map<String, Integer> byStatus = new HashMap<>();
        Map<String, Integer> byScoring = new HashMap<>();
        for (MeasureDefinitionEntity m : measures) {
            byStatus.merge(m.getStatus() != null ? m.getStatus() : "draft", 1, Integer::sum);
            byScoring.merge(m.getScoringType() != null ? m.getScoringType() : "unknown", 1, Integer::sum);
        }

        // Recent evaluations
        List<MeasureReportEntity> reports = reportRepository.findAll().stream()
                .sorted(Comparator.comparing(MeasureReportEntity::getCreatedAt).reversed())
                .limit(10)
                .collect(Collectors.toList());

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
        List<MeasureReportEntity> reports = reportRepository.findAll().stream()
                .filter(r -> measureId == null || measureId.equals(r.getMeasureDefinitionId()))
                .sorted(Comparator.comparing(MeasureReportEntity::getCreatedAt).reversed())
                .limit(count)
                .collect(Collectors.toList());

        // Reverse to show chronological order
        Collections.reverse(reports);

        return reports.stream()
                .map(r -> EnhancedDashboardData.TrendDataPoint.builder()
                        .period(r.getPeriodStart() + " to " + r.getPeriodEnd())
                        .measureName(r.getMeasureName())
                        .measureId(r.getMeasureDefinitionId())
                        .score(r.getMeasureScore())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDepartmentDrilldown(String departmentCode) {
        List<MeasureDefinitionEntity> measures = definitionRepository.findAll().stream()
                .filter(m -> departmentCode.equals(m.getDepartment()))
                .collect(Collectors.toList());

        List<MeasureReportEntity> reports = reportRepository.findAll().stream()
                .filter(r -> departmentCode.equals(r.getDepartment()))
                .collect(Collectors.toList());

        // Latest score per measure
        Map<String, Double> latestScores = new LinkedHashMap<>();
        Map<Long, MeasureReportEntity> latestByMeasure = new LinkedHashMap<>();
        for (MeasureReportEntity r : reports) {
            Long mId = r.getMeasureDefinitionId();
            if (mId != null) {
                MeasureReportEntity existing = latestByMeasure.get(mId);
                if (existing == null || r.getCreatedAt().isAfter(existing.getCreatedAt())) {
                    latestByMeasure.put(mId, r);
                }
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
        List<MeasureDefinitionEntity> measures = definitionRepository.findAll();
        if (department != null && !department.isBlank()) {
            measures = measures.stream()
                    .filter(m -> department.equals(m.getDepartment()))
                    .collect(Collectors.toList());
        }

        List<MeasureThresholdEntity> allThresholds = thresholdRepository.findByActiveTrue();

        // Get latest scores
        Map<Long, Double> latestScores = getLatestScoresMap();
        Map<Long, Double> targetMap = new HashMap<>();
        for (MeasureThresholdEntity t : allThresholds) {
            if ("target".equals(t.getThresholdType())) {
                targetMap.put(t.getMeasureDefinitionId(), t.getThresholdValue());
            }
        }

        List<QualityReport.MeasureScoreSummary> scoreSummaries = new ArrayList<>();
        int above = 0, below = 0;
        double totalScore = 0;
        int scored = 0;

        for (MeasureDefinitionEntity m : measures) {
            Double score = latestScores.get(m.getId());
            Double target = targetMap.get(m.getId());
            String status = "no_data";
            if (score != null) {
                scored++;
                totalScore += score;
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
                    .build());
        }

        return QualityReport.builder()
                .reportType(reportType)
                .periodLabel(LocalDate.now().getMonth().name() + " " + LocalDate.now().getYear())
                .department(department)
                .totalMeasures(measures.size())
                .measuresAboveTarget(above)
                .measuresBelowTarget(below)
                .averageScore(scored > 0 ? totalScore / scored : 0)
                .measureScores(scoreSummaries)
                .departmentAverages(computeDepartmentScores())
                .build();
    }

    private List<ThresholdAlert> computeAlerts(String department) {
        List<MeasureThresholdEntity> thresholds = department != null
                ? thresholdRepository.findByDepartmentAndActiveTrue(department)
                : thresholdRepository.findByActiveTrue();

        Map<Long, Double> latestScores = getLatestScoresMap();
        Map<Long, String> measureNames = new HashMap<>();
        definitionRepository.findAll().forEach(m -> measureNames.put(m.getId(), m.getName()));

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

    private Map<Long, Double> getLatestScoresMap() {
        Map<Long, MeasureReportEntity> latestByMeasure = new HashMap<>();
        for (MeasureReportEntity r : reportRepository.findAll()) {
            Long mId = r.getMeasureDefinitionId();
            if (mId != null) {
                MeasureReportEntity existing = latestByMeasure.get(mId);
                if (existing == null || r.getCreatedAt().isAfter(existing.getCreatedAt())) {
                    latestByMeasure.put(mId, r);
                }
            }
        }
        Map<Long, Double> scores = new HashMap<>();
        for (var entry : latestByMeasure.entrySet()) {
            if (entry.getValue().getMeasureScore() != null) {
                scores.put(entry.getKey(), entry.getValue().getMeasureScore());
            }
        }
        return scores;
    }

    private Map<String, Double> computeDepartmentScores() {
        Map<String, List<Double>> scoresByDept = new HashMap<>();
        Map<Long, String> measureDepts = new HashMap<>();
        definitionRepository.findAll().forEach(m -> {
            if (m.getDepartment() != null) {
                measureDepts.put(m.getId(), m.getDepartment());
            }
        });

        Map<Long, Double> latestScores = getLatestScoresMap();
        for (var entry : latestScores.entrySet()) {
            String dept = measureDepts.get(entry.getKey());
            if (dept != null) {
                scoresByDept.computeIfAbsent(dept, k -> new ArrayList<>()).add(entry.getValue());
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
