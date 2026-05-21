package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportGroupEntity;
import com.cqlplatform.entity.MeasureReportPopulationEntity;
import com.cqlplatform.entity.MeasureReportStratifierEntity;
import com.cqlplatform.entity.MeasureReportStratifierPopulationEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.repository.MeasureReportGroupRepository;
import com.cqlplatform.repository.MeasureReportPopulationRepository;
import com.cqlplatform.repository.MeasureReportStratifierPopulationRepository;
import com.cqlplatform.repository.MeasureReportStratifierRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persist a {@link MeasureEvaluationResult} into the normalized child tables
 * (group / population / stratifier / stratifier-population).
 *
 * <p>Phase 1 of code-review issue #6 / ADR-001. Called from two places:
 * <ol>
 *   <li>{@link MeasureReportService#saveReport} — dual-write at save time. The
 *       {@code result_json} blob is still written alongside; consumers continue
 *       reading it in Phase 1.</li>
 *   <li>{@link MeasureReportBackfillService} — one-shot startup task that walks
 *       existing measure_report rows and populates the child tables from their
 *       {@code result_json}.</li>
 * </ol>
 *
 * <p>The service is idempotent against a specific {@code measureReportId}: it
 * deletes existing normalized rows for the report before re-inserting. This lets
 * either caller safely re-run without creating duplicates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureReportNormalizer {

    private final MeasureReportGroupRepository groupRepository;
    private final MeasureReportPopulationRepository populationRepository;
    private final MeasureReportStratifierRepository stratifierRepository;
    private final MeasureReportStratifierPopulationRepository stratifierPopulationRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Replace the normalized rows for {@code measureReportId} with the contents of {@code result}.
     * Safe to call repeatedly — existing rows are deleted first.
     *
     * @return number of group rows persisted
     */
    @Transactional
    public int persist(Long measureReportId, MeasureEvaluationResult result) {
        if (measureReportId == null) {
            throw new IllegalArgumentException("measureReportId is required");
        }
        // Idempotency: wipe any existing normalized rows for this report.
        // FK cascades take care of populations + stratifiers + stratifier populations.
        groupRepository.deleteAllByMeasureReportId(measureReportId);

        if (result == null || result.getGroups() == null || result.getGroups().isEmpty()) {
            return 0;
        }

        int groupCount = 0;
        int ordinal = 0;
        for (MeasureEvaluationResult.GroupResult group : result.getGroups()) {
            persistGroup(measureReportId, group, ordinal++);
            groupCount++;
        }
        return groupCount;
    }

    private void persistGroup(Long measureReportId, MeasureEvaluationResult.GroupResult group, int ordinal) {
        MeasureReportGroupEntity groupEntity = MeasureReportGroupEntity.builder()
                .measureReportId(measureReportId)
                .groupId(group.getGroupId() != null ? group.getGroupId() : "group-" + ordinal)
                .description(group.getDescription())
                .measureScore(group.getMeasureScore())
                .measureScoreUnit(group.getMeasureScoreUnit())
                .totalPatients(group.getTotalPatients())
                .ordinal(ordinal)
                .build();

        // Flatten ObservationStatistics onto the group row
        MeasureEvaluationResult.ObservationStatistics obs = group.getObservationStatistics();
        if (obs != null) {
            groupEntity.setObsAggregateMethod(obs.getAggregateMethod());
            groupEntity.setObsAggregateValue(obs.getAggregateValue());
            groupEntity.setObsCount(obs.getObservationCount());
            groupEntity.setObsMinimum(obs.getMinimum());
            groupEntity.setObsMaximum(obs.getMaximum());
            groupEntity.setObsAverage(obs.getAverage());
            groupEntity.setObsMedian(obs.getMedian());
            groupEntity.setObsUnit(obs.getUnit());
        }

        groupEntity = groupRepository.save(groupEntity);

        // Populations
        if (group.getPopulations() != null) {
            int popOrdinal = 0;
            for (MeasureEvaluationResult.PopulationResult pop : group.getPopulations()) {
                populationRepository.save(MeasureReportPopulationEntity.builder()
                        .measureReportGroupId(groupEntity.getId())
                        .populationType(pop.getPopulationType())
                        .populationId(pop.getPopulationId() != null
                                ? pop.getPopulationId() : pop.getPopulationType())
                        .count(pop.getCount() != null ? pop.getCount() : 0)
                        .subjectIdsJson(encodeSubjectIds(pop.getSubjectIds()))
                        .ordinal(popOrdinal++)
                        .build());
            }
        }

        // Stratifiers (with their populations)
        if (group.getStratifiers() != null) {
            int stratOrdinal = 0;
            for (MeasureEvaluationResult.StratifierResult strat : group.getStratifiers()) {
                MeasureReportStratifierEntity stratEntity = stratifierRepository.save(
                        MeasureReportStratifierEntity.builder()
                                .measureReportGroupId(groupEntity.getId())
                                .strataId(strat.getStrataId() != null
                                        ? strat.getStrataId() : "stratum-" + stratOrdinal)
                                .strataValue(strat.getStrataValue())
                                .measureScore(strat.getMeasureScore())
                                .ordinal(stratOrdinal++)
                                .build());
                if (strat.getPopulations() != null) {
                    int stratPopOrdinal = 0;
                    for (MeasureEvaluationResult.PopulationResult sp : strat.getPopulations()) {
                        stratifierPopulationRepository.save(
                                MeasureReportStratifierPopulationEntity.builder()
                                        .measureReportStratifierId(stratEntity.getId())
                                        .populationType(sp.getPopulationType())
                                        .populationId(sp.getPopulationId() != null
                                                ? sp.getPopulationId() : sp.getPopulationType())
                                        .count(sp.getCount() != null ? sp.getCount() : 0)
                                        .ordinal(stratPopOrdinal++)
                                        .build());
                    }
                }
            }
        }
    }

    /**
     * Serialize subjectIds (may be null) to a compact JSON array or null. Kept private —
     * we don't want downstream code reading the raw TEXT blob; they should ship a repository
     * method if they need per-subject access.
     */
    private static String encodeSubjectIds(List<String> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(subjectIds);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize subjectIds ({} entries): {}", subjectIds.size(), e.getMessage());
            return null;
        }
    }
}
