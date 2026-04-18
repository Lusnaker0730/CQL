package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportGroupEntity;
import com.cqlplatform.entity.MeasureReportPopulationEntity;
import com.cqlplatform.entity.MeasureReportStratifierEntity;
import com.cqlplatform.entity.MeasureReportStratifierPopulationEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.GroupResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.ObservationStatistics;
import com.cqlplatform.model.measure.MeasureEvaluationResult.PopulationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.StratifierResult;
import com.cqlplatform.repository.MeasureReportGroupRepository;
import com.cqlplatform.repository.MeasureReportPopulationRepository;
import com.cqlplatform.repository.MeasureReportStratifierPopulationRepository;
import com.cqlplatform.repository.MeasureReportStratifierRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-path helper for Phase 2 of ADR-001. Reconstructs a {@link MeasureEvaluationResult} from the
 * normalized {@code measure_report_*} child tables, matching the shape of what
 * {@link com.cqlplatform.entity.MeasureReportEntity#getEvaluationResult()} returns from
 * {@code result_json} deserialization.
 *
 * <p>Consumers use this as a drop-in replacement for {@code report.getEvaluationResult()} during
 * Phase 2 migration — they then iterate groups / populations / stratifiers exactly as before,
 * no structural changes needed. Returns {@link Optional#empty()} when the report has no
 * normalized rows yet (pre-Phase-1 data awaiting backfill); callers fall back to the legacy
 * {@code result_json} path.
 *
 * <p>Phase 3 of ADR-001 makes this the only read path and drops {@code result_json}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NormalizedMeasureReportReader {

    private final MeasureReportGroupRepository groupRepository;
    private final MeasureReportPopulationRepository populationRepository;
    private final MeasureReportStratifierRepository stratifierRepository;
    private final MeasureReportStratifierPopulationRepository stratifierPopulationRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    /**
     * Rebuild the result's group/population/stratifier structure from normalized rows.
     *
     * <p>Returns {@link Optional#empty()} when no group rows exist for the report — indicating
     * a pre-Phase-1 record that the backfill hasn't reached yet. Callers fall back to
     * {@code result_json}.
     *
     * <p>The returned object is a fresh {@link MeasureEvaluationResult} with only
     * groups/stratifiers populated. Top-level fields (measureId, measureName, periodStart/End,
     * errorMessage, supplementalData) are left null because consumers already read them from
     * {@link com.cqlplatform.entity.MeasureReportEntity}'s structured columns.
     */
    @Transactional(readOnly = true)
    public Optional<MeasureEvaluationResult> reconstruct(Long reportId) {
        if (reportId == null) return Optional.empty();

        List<MeasureReportGroupEntity> groupRows = groupRepository
                .findByMeasureReportIdOrderByOrdinalAsc(reportId);
        if (groupRows.isEmpty()) {
            return Optional.empty();
        }

        List<GroupResult> groups = new ArrayList<>(groupRows.size());
        for (MeasureReportGroupEntity g : groupRows) {
            groups.add(buildGroup(g));
        }
        return Optional.of(MeasureEvaluationResult.builder().groups(groups).build());
    }

    private GroupResult buildGroup(MeasureReportGroupEntity g) {
        List<MeasureReportPopulationEntity> popRows = populationRepository
                .findByMeasureReportGroupIdOrderByOrdinalAsc(g.getId());
        List<PopulationResult> pops = new ArrayList<>(popRows.size());
        for (MeasureReportPopulationEntity p : popRows) {
            pops.add(PopulationResult.builder()
                    .populationType(p.getPopulationType())
                    .populationId(p.getPopulationId())
                    .count(p.getCount())
                    .subjectIds(decodeSubjectIds(p.getSubjectIdsJson()))
                    .build());
        }

        List<MeasureReportStratifierEntity> stratRows = stratifierRepository
                .findByMeasureReportGroupIdOrderByOrdinalAsc(g.getId());
        List<StratifierResult> strats = new ArrayList<>(stratRows.size());
        for (MeasureReportStratifierEntity s : stratRows) {
            strats.add(buildStratifier(s));
        }

        ObservationStatistics obs = buildObservationStatistics(g);

        return GroupResult.builder()
                .groupId(g.getGroupId())
                .description(g.getDescription())
                .measureScore(g.getMeasureScore())
                .measureScoreUnit(g.getMeasureScoreUnit())
                .totalPatients(g.getTotalPatients())
                .populations(pops)
                .stratifiers(strats)
                .observationStatistics(obs)
                .build();
    }

    private StratifierResult buildStratifier(MeasureReportStratifierEntity s) {
        List<MeasureReportStratifierPopulationEntity> stratPopRows = stratifierPopulationRepository
                .findByMeasureReportStratifierIdOrderByOrdinalAsc(s.getId());
        List<PopulationResult> pops = new ArrayList<>(stratPopRows.size());
        for (MeasureReportStratifierPopulationEntity sp : stratPopRows) {
            pops.add(PopulationResult.builder()
                    .populationType(sp.getPopulationType())
                    .populationId(sp.getPopulationId())
                    .count(sp.getCount())
                    .build());
        }
        return StratifierResult.builder()
                .strataId(s.getStrataId())
                .strataValue(s.getStrataValue())
                .measureScore(s.getMeasureScore())
                .populations(pops)
                .build();
    }

    /**
     * Returns null (matching legacy {@code result_json} semantics) when all ObservationStatistics
     * fields are null. Otherwise builds a populated instance.
     */
    private static ObservationStatistics buildObservationStatistics(MeasureReportGroupEntity g) {
        if (g.getObsAggregateMethod() == null
                && g.getObsAggregateValue() == null
                && g.getObsCount() == null
                && g.getObsMinimum() == null
                && g.getObsMaximum() == null
                && g.getObsAverage() == null
                && g.getObsMedian() == null
                && g.getObsUnit() == null) {
            return null;
        }
        return ObservationStatistics.builder()
                .aggregateMethod(g.getObsAggregateMethod())
                .aggregateValue(g.getObsAggregateValue())
                .observationCount(g.getObsCount())
                .minimum(g.getObsMinimum())
                .maximum(g.getObsMaximum())
                .average(g.getObsAverage())
                .median(g.getObsMedian())
                .unit(g.getObsUnit())
                .build();
    }

    private static List<String> decodeSubjectIds(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, STRING_LIST);
        } catch (Exception e) {
            log.warn("Failed to deserialize subject_ids_json ({} chars): {}",
                    json.length(), e.getMessage());
            return null;
        }
    }
}
