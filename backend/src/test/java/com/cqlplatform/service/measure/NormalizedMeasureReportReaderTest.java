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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NormalizedMeasureReportReader — rebuild MeasureEvaluationResult from normalized tables")
class NormalizedMeasureReportReaderTest {

    private MeasureReportGroupRepository groupRepo;
    private MeasureReportPopulationRepository popRepo;
    private MeasureReportStratifierRepository stratRepo;
    private MeasureReportStratifierPopulationRepository stratPopRepo;
    private NormalizedMeasureReportReader reader;

    @BeforeEach
    void setUp() {
        groupRepo = mock(MeasureReportGroupRepository.class);
        popRepo = mock(MeasureReportPopulationRepository.class);
        stratRepo = mock(MeasureReportStratifierRepository.class);
        stratPopRepo = mock(MeasureReportStratifierPopulationRepository.class);
        reader = new NormalizedMeasureReportReader(groupRepo, popRepo, stratRepo, stratPopRepo);
    }

    @Test
    @DisplayName("empty normalized rows → Optional.empty (triggers fallback at caller)")
    void noGroups_returnsEmpty() {
        when(groupRepo.findByMeasureReportIdOrderByOrdinalAsc(1L)).thenReturn(List.of());
        assertThat(reader.reconstruct(1L)).isEmpty();
    }

    @Test
    @DisplayName("null reportId → Optional.empty (defensive, no repo call)")
    void nullReportId_returnsEmpty() {
        assertThat(reader.reconstruct(null)).isEmpty();
    }

    @Test
    @DisplayName("single group with populations rebuilds identically to legacy JSON shape")
    void proportionMeasure_rebuilds() {
        MeasureReportGroupEntity g = MeasureReportGroupEntity.builder()
                .id(10L).measureReportId(1L).groupId("g1")
                .description("Primary group").measureScore(0.75)
                .measureScoreUnit("%").totalPatients(48).ordinal(0).build();
        when(groupRepo.findByMeasureReportIdOrderByOrdinalAsc(1L)).thenReturn(List.of(g));
        when(popRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(10L)).thenReturn(List.of(
                MeasureReportPopulationEntity.builder()
                        .populationType("initial-population").populationId("ip").count(40).ordinal(0).build(),
                MeasureReportPopulationEntity.builder()
                        .populationType("denominator").populationId("denom").count(40).ordinal(1).build(),
                MeasureReportPopulationEntity.builder()
                        .populationType("numerator").populationId("numer").count(30).ordinal(2).build()
        ));
        when(stratRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(10L)).thenReturn(List.of());

        Optional<MeasureEvaluationResult> out = reader.reconstruct(1L);

        assertThat(out).isPresent();
        MeasureEvaluationResult result = out.get();
        assertThat(result.getGroups()).hasSize(1);
        MeasureEvaluationResult.GroupResult rg = result.getGroups().get(0);
        assertThat(rg.getGroupId()).isEqualTo("g1");
        assertThat(rg.getDescription()).isEqualTo("Primary group");
        assertThat(rg.getMeasureScore()).isEqualTo(0.75);
        assertThat(rg.getTotalPatients()).isEqualTo(48);
        assertThat(rg.getPopulations()).hasSize(3);
        assertThat(rg.getPopulations()).extracting(MeasureEvaluationResult.PopulationResult::getPopulationType)
                .containsExactly("initial-population", "denominator", "numerator");
        assertThat(rg.getPopulations()).extracting(MeasureEvaluationResult.PopulationResult::getCount)
                .containsExactly(40, 40, 30);
        // No stratifier data → empty list, not null
        assertThat(rg.getStratifiers()).isEmpty();
        // No observation statistics → null (matches legacy JSON shape when all obs fields absent)
        assertThat(rg.getObservationStatistics()).isNull();
    }

    @Test
    @DisplayName("CV measure with ObservationStatistics fields populates the nested object")
    void cvMeasure_rebuildsObservationStatistics() {
        MeasureReportGroupEntity g = MeasureReportGroupEntity.builder()
                .id(20L).measureReportId(2L).groupId("cv").ordinal(0)
                .measureScore(5.6)
                .obsAggregateMethod("Average").obsAggregateValue(5.6).obsCount(10)
                .obsMinimum(4.8).obsMaximum(6.9).obsAverage(5.6).obsMedian(5.5)
                .obsUnit("%").build();
        when(groupRepo.findByMeasureReportIdOrderByOrdinalAsc(2L)).thenReturn(List.of(g));
        when(popRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(20L)).thenReturn(List.of());
        when(stratRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(20L)).thenReturn(List.of());

        MeasureEvaluationResult.ObservationStatistics obs = reader.reconstruct(2L)
                .orElseThrow().getGroups().get(0).getObservationStatistics();

        assertThat(obs).isNotNull();
        assertThat(obs.getAggregateMethod()).isEqualTo("Average");
        assertThat(obs.getAggregateValue()).isEqualTo(5.6);
        assertThat(obs.getObservationCount()).isEqualTo(10);
        assertThat(obs.getUnit()).isEqualTo("%");
    }

    @Test
    @DisplayName("stratifier + its populations rebuild into nested StratifierResult")
    void stratifiedMeasure_rebuildsStratifiers() {
        MeasureReportGroupEntity g = MeasureReportGroupEntity.builder()
                .id(30L).measureReportId(3L).groupId("strat").ordinal(0).build();
        when(groupRepo.findByMeasureReportIdOrderByOrdinalAsc(3L)).thenReturn(List.of(g));
        when(popRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(30L)).thenReturn(List.of());

        MeasureReportStratifierEntity strat = MeasureReportStratifierEntity.builder()
                .id(300L).measureReportGroupId(30L)
                .strataId("age").strataValue("20-40").measureScore(0.6).ordinal(0).build();
        when(stratRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(30L)).thenReturn(List.of(strat));
        when(stratPopRepo.findByMeasureReportStratifierIdOrderByOrdinalAsc(300L)).thenReturn(List.of(
                MeasureReportStratifierPopulationEntity.builder()
                        .populationType("denominator").populationId("denom").count(8).ordinal(0).build(),
                MeasureReportStratifierPopulationEntity.builder()
                        .populationType("numerator").populationId("numer").count(4).ordinal(1).build()
        ));

        List<MeasureEvaluationResult.StratifierResult> strats = reader.reconstruct(3L)
                .orElseThrow().getGroups().get(0).getStratifiers();

        assertThat(strats).hasSize(1);
        MeasureEvaluationResult.StratifierResult s = strats.get(0);
        assertThat(s.getStrataId()).isEqualTo("age");
        assertThat(s.getStrataValue()).isEqualTo("20-40");
        assertThat(s.getMeasureScore()).isEqualTo(0.6);
        assertThat(s.getPopulations()).extracting(MeasureEvaluationResult.PopulationResult::getPopulationType)
                .containsExactly("denominator", "numerator");
        assertThat(s.getPopulations()).extracting(MeasureEvaluationResult.PopulationResult::getCount)
                .containsExactly(8, 4);
    }

    @Test
    @DisplayName("subject_ids_json decodes back to List<String>")
    void subjectIds_decoded() {
        MeasureReportGroupEntity g = MeasureReportGroupEntity.builder()
                .id(40L).measureReportId(4L).groupId("g").ordinal(0).build();
        when(groupRepo.findByMeasureReportIdOrderByOrdinalAsc(4L)).thenReturn(List.of(g));
        when(popRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(40L)).thenReturn(List.of(
                MeasureReportPopulationEntity.builder()
                        .populationType("ip").populationId("ip").count(2)
                        .subjectIdsJson("[\"Patient/1\",\"Patient/2\"]")
                        .ordinal(0).build()
        ));
        when(stratRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(40L)).thenReturn(List.of());

        var pops = reader.reconstruct(4L).orElseThrow().getGroups().get(0).getPopulations();

        assertThat(pops.get(0).getSubjectIds()).containsExactly("Patient/1", "Patient/2");
    }

    @Test
    @DisplayName("malformed subject_ids_json → subjectIds = null (failure isolated, group still rebuilds)")
    void malformedSubjectIds_doesNotBreak() {
        MeasureReportGroupEntity g = MeasureReportGroupEntity.builder()
                .id(50L).measureReportId(5L).groupId("g").ordinal(0).build();
        when(groupRepo.findByMeasureReportIdOrderByOrdinalAsc(5L)).thenReturn(List.of(g));
        when(popRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(50L)).thenReturn(List.of(
                MeasureReportPopulationEntity.builder()
                        .populationType("ip").populationId("ip").count(1)
                        .subjectIdsJson("{not valid json")
                        .ordinal(0).build()
        ));
        when(stratRepo.findByMeasureReportGroupIdOrderByOrdinalAsc(50L)).thenReturn(List.of());

        var pop = reader.reconstruct(5L).orElseThrow().getGroups().get(0).getPopulations().get(0);

        assertThat(pop.getCount()).isEqualTo(1);
        assertThat(pop.getSubjectIds()).isNull();  // broken JSON isolated
    }
}
