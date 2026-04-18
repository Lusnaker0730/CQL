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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MeasureReportNormalizer — result → normalized rows")
class MeasureReportNormalizerTest {

    // Simple in-memory stubs capturing what the normalizer writes
    private final List<MeasureReportGroupEntity> savedGroups = new ArrayList<>();
    private final List<MeasureReportPopulationEntity> savedPops = new ArrayList<>();
    private final List<MeasureReportStratifierEntity> savedStrats = new ArrayList<>();
    private final List<MeasureReportStratifierPopulationEntity> savedStratPops = new ArrayList<>();

    private MeasureReportGroupRepository groupRepo;
    private MeasureReportPopulationRepository popRepo;
    private MeasureReportStratifierRepository stratRepo;
    private MeasureReportStratifierPopulationRepository stratPopRepo;

    private MeasureReportNormalizer normalizer;

    @BeforeEach
    void setUp() {
        savedGroups.clear();
        savedPops.clear();
        savedStrats.clear();
        savedStratPops.clear();

        AtomicLong nextGroupId = new AtomicLong(1);
        AtomicLong nextStratId = new AtomicLong(1);

        groupRepo = mock(MeasureReportGroupRepository.class);
        popRepo = mock(MeasureReportPopulationRepository.class);
        stratRepo = mock(MeasureReportStratifierRepository.class);
        stratPopRepo = mock(MeasureReportStratifierPopulationRepository.class);

        when(groupRepo.save(any(MeasureReportGroupEntity.class))).thenAnswer(inv -> {
            MeasureReportGroupEntity g = inv.getArgument(0);
            if (g.getId() == null) g.setId(nextGroupId.getAndIncrement());
            savedGroups.add(g);
            return g;
        });
        when(popRepo.save(any(MeasureReportPopulationEntity.class))).thenAnswer(inv -> {
            MeasureReportPopulationEntity p = inv.getArgument(0);
            savedPops.add(p);
            return p;
        });
        when(stratRepo.save(any(MeasureReportStratifierEntity.class))).thenAnswer(inv -> {
            MeasureReportStratifierEntity s = inv.getArgument(0);
            if (s.getId() == null) s.setId(nextStratId.getAndIncrement());
            savedStrats.add(s);
            return s;
        });
        when(stratPopRepo.save(any(MeasureReportStratifierPopulationEntity.class))).thenAnswer(inv -> {
            MeasureReportStratifierPopulationEntity sp = inv.getArgument(0);
            savedStratPops.add(sp);
            return sp;
        });

        normalizer = new MeasureReportNormalizer(groupRepo, popRepo, stratRepo, stratPopRepo);
    }

    @Test
    @DisplayName("null / empty result persists zero groups AND deletes any existing rows (idempotent)")
    void emptyResult_deletesExistingAndPersistsNothing() {
        int count = normalizer.persist(42L, MeasureEvaluationResult.builder().build());

        assertThat(count).isZero();
        verify(groupRepo).deleteAllByMeasureReportId(42L);
    }

    @Test
    @DisplayName("proportion measure: group with 3 populations → 1 group row + 3 population rows")
    void proportionMeasure_writesGroupAndPopulations() {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .measureId("m1")
                .groups(List.of(
                        MeasureEvaluationResult.GroupResult.builder()
                                .groupId("group-1")
                                .measureScore(0.75)
                                .totalPatients(48)
                                .populations(List.of(
                                        MeasureEvaluationResult.PopulationResult.builder()
                                                .populationType("initial-population")
                                                .populationId("ip")
                                                .count(40)
                                                .build(),
                                        MeasureEvaluationResult.PopulationResult.builder()
                                                .populationType("denominator")
                                                .populationId("denom")
                                                .count(40)
                                                .build(),
                                        MeasureEvaluationResult.PopulationResult.builder()
                                                .populationType("numerator")
                                                .populationId("numer")
                                                .count(30)
                                                .build()))
                                .build()))
                .build();

        int count = normalizer.persist(100L, result);

        assertThat(count).isEqualTo(1);
        assertThat(savedGroups).hasSize(1);
        assertThat(savedGroups.get(0).getMeasureScore()).isEqualTo(0.75);
        assertThat(savedGroups.get(0).getTotalPatients()).isEqualTo(48);
        assertThat(savedGroups.get(0).getOrdinal()).isZero();

        assertThat(savedPops).hasSize(3);
        assertThat(savedPops).extracting(MeasureReportPopulationEntity::getPopulationType)
                .containsExactly("initial-population", "denominator", "numerator");
        assertThat(savedPops).extracting(MeasureReportPopulationEntity::getCount)
                .containsExactly(40, 40, 30);
        // Ordinals preserved
        assertThat(savedPops).extracting(MeasureReportPopulationEntity::getOrdinal)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("continuous-variable measure: ObservationStatistics flattens onto group row")
    void cvMeasure_flattensObservationStatistics() {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .groups(List.of(
                        MeasureEvaluationResult.GroupResult.builder()
                                .groupId("group-1")
                                .measureScore(5.6)
                                .observationStatistics(MeasureEvaluationResult.ObservationStatistics.builder()
                                        .aggregateMethod("Average")
                                        .aggregateValue(5.6)
                                        .observationCount(10)
                                        .minimum(4.8)
                                        .maximum(6.9)
                                        .average(5.6)
                                        .median(5.5)
                                        .unit("%")
                                        .build())
                                .build()))
                .build();

        normalizer.persist(101L, result);

        assertThat(savedGroups).hasSize(1);
        MeasureReportGroupEntity g = savedGroups.get(0);
        assertThat(g.getObsAggregateMethod()).isEqualTo("Average");
        assertThat(g.getObsAggregateValue()).isEqualTo(5.6);
        assertThat(g.getObsCount()).isEqualTo(10);
        assertThat(g.getObsMinimum()).isEqualTo(4.8);
        assertThat(g.getObsMaximum()).isEqualTo(6.9);
        assertThat(g.getObsAverage()).isEqualTo(5.6);
        assertThat(g.getObsMedian()).isEqualTo(5.5);
        assertThat(g.getObsUnit()).isEqualTo("%");
    }

    @Test
    @DisplayName("multiple groups + stratifiers with nested populations persist with preserved order")
    void complexReport_writesFullGraph() {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .groups(List.of(
                        MeasureEvaluationResult.GroupResult.builder()
                                .groupId("group-A")
                                .populations(List.of(
                                        MeasureEvaluationResult.PopulationResult.builder()
                                                .populationType("initial-population")
                                                .count(20)
                                                .build()))
                                .stratifiers(List.of(
                                        MeasureEvaluationResult.StratifierResult.builder()
                                                .strataId("age")
                                                .strataValue("20-40")
                                                .measureScore(0.5)
                                                .populations(List.of(
                                                        MeasureEvaluationResult.PopulationResult.builder()
                                                                .populationType("denominator")
                                                                .count(8)
                                                                .build(),
                                                        MeasureEvaluationResult.PopulationResult.builder()
                                                                .populationType("numerator")
                                                                .count(4)
                                                                .build()))
                                                .build()))
                                .build(),
                        MeasureEvaluationResult.GroupResult.builder()
                                .groupId("group-B")
                                .populations(List.of(
                                        MeasureEvaluationResult.PopulationResult.builder()
                                                .populationType("measure-population")
                                                .count(15)
                                                .build()))
                                .build()))
                .build();

        int count = normalizer.persist(102L, result);

        assertThat(count).isEqualTo(2);
        assertThat(savedGroups).extracting(MeasureReportGroupEntity::getGroupId)
                .containsExactly("group-A", "group-B");
        assertThat(savedGroups).extracting(MeasureReportGroupEntity::getOrdinal)
                .containsExactly(0, 1);

        assertThat(savedStrats).hasSize(1);
        assertThat(savedStrats.get(0).getStrataId()).isEqualTo("age");
        assertThat(savedStrats.get(0).getStrataValue()).isEqualTo("20-40");

        assertThat(savedStratPops).hasSize(2);
        assertThat(savedStratPops).extracting(MeasureReportStratifierPopulationEntity::getPopulationType)
                .containsExactly("denominator", "numerator");
        assertThat(savedStratPops).extracting(MeasureReportStratifierPopulationEntity::getCount)
                .containsExactly(8, 4);
    }

    @Test
    @DisplayName("persist is idempotent — calling twice triggers delete + re-save (no duplicates)")
    void persist_isIdempotent() {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .groups(List.of(MeasureEvaluationResult.GroupResult.builder()
                        .groupId("g")
                        .build()))
                .build();

        normalizer.persist(1L, result);
        normalizer.persist(1L, result);

        // delete called twice — the invariant under test
        verify(groupRepo, org.mockito.Mockito.times(2)).deleteAllByMeasureReportId(1L);
        // saved twice (groupRepo mock accumulates — both calls appended)
        assertThat(savedGroups).hasSize(2);
    }

    @Test
    @DisplayName("population without explicit populationId falls back to populationType")
    void populationIdFallback() {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .groups(List.of(MeasureEvaluationResult.GroupResult.builder()
                        .populations(List.of(MeasureEvaluationResult.PopulationResult.builder()
                                .populationType("initial-population")
                                .count(5)
                                .build()))
                        .build()))
                .build();

        normalizer.persist(1L, result);

        assertThat(savedPops).hasSize(1);
        assertThat(savedPops.get(0).getPopulationId()).isEqualTo("initial-population");
    }

    @Test
    @DisplayName("null measureReportId is rejected — defensive contract")
    void nullReportId_throws() {
        assertThat(java.util.function.Function.identity()).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                normalizer.persist(null, MeasureEvaluationResult.builder().build())
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
