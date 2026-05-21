package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.entity.MeasureReportPopulationEntity;
import com.cqlplatform.model.measure.MeasureComparisonResult;
import com.cqlplatform.model.measure.MeasureComparisonResult.PeriodSummary;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.GroupResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.PopulationResult;
import com.cqlplatform.model.measure.MeasureTrendResult;
import com.cqlplatform.model.measure.MeasureTrendResult.TrendDataPoint;
import com.cqlplatform.repository.MeasureReportPopulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureComparisonService {

    private final MeasureReportService reportService;
    private final MeasureReportPopulationRepository populationRepository;

    public MeasureComparisonResult comparePeriods(Long measureDefinitionId, String measureName,
                                                   LocalDate p1Start, LocalDate p1End,
                                                   LocalDate p2Start, LocalDate p2End) {
        List<MeasureReportEntity> p1Reports = reportService.getReportsForPeriodById(measureDefinitionId, p1Start, p1End);
        List<MeasureReportEntity> p2Reports = reportService.getReportsForPeriodById(measureDefinitionId, p2Start, p2End);

        PeriodSummary period1 = buildPeriodSummary(p1Start, p1End, p1Reports);
        PeriodSummary period2 = buildPeriodSummary(p2Start, p2End, p2Reports);

        Double scoreDelta = null;
        Double scorePercentChange = null;
        String trend = "stable";

        if (period1.getMeasureScore() != null && period2.getMeasureScore() != null) {
            scoreDelta = period2.getMeasureScore() - period1.getMeasureScore();
            if (period1.getMeasureScore() != 0) {
                scorePercentChange = (scoreDelta / period1.getMeasureScore()) * 100;
            }
            if (scoreDelta > 1.0) {
                trend = "improving";
            } else if (scoreDelta < -1.0) {
                trend = "declining";
            }
        }

        Map<String, Integer> populationDeltas = new HashMap<>();
        if (period1.getPopulationCounts() != null && period2.getPopulationCounts() != null) {
            Set<String> allPops = new HashSet<>();
            allPops.addAll(period1.getPopulationCounts().keySet());
            allPops.addAll(period2.getPopulationCounts().keySet());
            for (String pop : allPops) {
                int count1 = period1.getPopulationCounts().getOrDefault(pop, 0);
                int count2 = period2.getPopulationCounts().getOrDefault(pop, 0);
                populationDeltas.put(pop, count2 - count1);
            }
        }

        return MeasureComparisonResult.builder()
                .measureName(measureName)
                .period1(period1)
                .period2(period2)
                .scoreDelta(scoreDelta)
                .scorePercentChange(scorePercentChange)
                .populationDeltas(populationDeltas)
                .trend(trend)
                .build();
    }

    public MeasureTrendResult getTrend(Long measureDefinitionId, String measureName, int periodCount) {
        List<MeasureReportEntity> reports = reportService.getReportsByMeasureIdOrderByPeriod(measureDefinitionId);

        // Take the last N reports
        int start = Math.max(0, reports.size() - periodCount);
        List<MeasureReportEntity> recentReports = reports.subList(start, reports.size());

        List<TrendDataPoint> dataPoints = recentReports.stream()
                .map(report -> {
                    Map<String, Integer> popCounts = extractPopulationCounts(report);
                    return TrendDataPoint.builder()
                            .periodStart(report.getPeriodStart())
                            .periodEnd(report.getPeriodEnd())
                            .score(report.getMeasureScore())
                            .populationCounts(popCounts)
                            .build();
                })
                .collect(Collectors.toList());

        return MeasureTrendResult.builder()
                .measureName(measureName)
                .dataPoints(dataPoints)
                .build();
    }

    private PeriodSummary buildPeriodSummary(LocalDate periodStart, LocalDate periodEnd,
                                              List<MeasureReportEntity> reports) {
        if (reports.isEmpty()) {
            return PeriodSummary.builder()
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .build();
        }

        // Use the best report: prefer the one with the highest score among those with non-null scores
        MeasureReportEntity report = reports.size() > 1
                ? reports.stream()
                    .filter(r -> r.getMeasureScore() != null)
                    .max(java.util.Comparator.comparingDouble(MeasureReportEntity::getMeasureScore))
                    .orElse(reports.get(0))
                : reports.get(0);
        Map<String, Integer> popCounts = extractPopulationCounts(report);

        return PeriodSummary.builder()
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .measureScore(report.getMeasureScore())
                .totalPatients(report.getTotalPatients())
                .populationCounts(popCounts)
                .build();
    }

    /**
     * Population-type → latest count across all groups.
     *
     * <p>Phase 2 of ADR-001: prefer the normalized {@code measure_report_population} table
     * (one query, indexed, SQL-friendly). Fall back to {@code result_json} parsing when the
     * report has no normalized rows — this covers reports written before Phase 1 deployment
     * that haven't been picked up by the startup backfill yet. After Phase 3 the fallback
     * branch becomes unreachable and will be removed.
     */
    private Map<String, Integer> extractPopulationCounts(MeasureReportEntity report) {
        if (report.getId() != null) {
            List<MeasureReportPopulationEntity> rows = populationRepository.findAllForReport(report.getId());
            if (!rows.isEmpty()) {
                // Iteration order (group ordinal, then population ordinal) matches the legacy
                // result_json traversal below — later populations override earlier same-typed ones.
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (MeasureReportPopulationEntity p : rows) {
                    counts.put(p.getPopulationType(), p.getCount() != null ? p.getCount() : 0);
                }
                return counts;
            }
        }
        // Transitional fallback — historical reports not yet backfilled.
        return extractPopulationCountsFromJson(report);
    }

    /** Legacy result_json path. Removed after Phase 3 of ADR-001. */
    private Map<String, Integer> extractPopulationCountsFromJson(MeasureReportEntity report) {
        Map<String, Integer> counts = new HashMap<>();
        MeasureEvaluationResult result = report.getEvaluationResult();
        if (result != null && result.getGroups() != null) {
            for (GroupResult group : result.getGroups()) {
                if (group.getPopulations() != null) {
                    for (PopulationResult pop : group.getPopulations()) {
                        counts.put(pop.getPopulationType(), pop.getCount() != null ? pop.getCount() : 0);
                    }
                }
            }
        }
        return counts;
    }
}
