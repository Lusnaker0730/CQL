package com.cqlplatform.service.measure;

import com.cqlplatform.exception.MeasureNotEvaluableException;
import com.cqlplatform.model.measure.EvaluationStatusConstants;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.GroupResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.PopulationResult;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.cqlplatform.model.measure.PopulationTypeConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PAT-259 — composite scoring from component results: opportunity (Σ num / Σ effective den), all-or-nothing and
 * subject-level linear (both over the components' subject lists), weighted (component scores × cqfm weights),
 * the per-component breakdown, and the composite's own report.
 */
@ExtendWith(MockitoExtension.class)
class CompositeMeasureServiceTest {

    @Mock private MeasureDefinitionService definitionService;
    @Mock private MeasureEvaluationService evaluationService;
    @Mock private ObjectProvider<MeasureReportService> reportProvider;
    @Mock private MeasureReportService reportService;

    private CompositeMeasureService service;

    @BeforeEach
    void setUp() {
        service = new CompositeMeasureService(definitionService, evaluationService, new MeasureScoreCalculator(), reportProvider);
    }

    private static MeasureDefinition composite(String status, String method, Map<String, Double> weights) {
        return MeasureDefinition.builder()
                .id(1L).name("Composite").status(status)
                .scoringType(ScoringTypeConstants.COMPOSITE)
                .compositeScoring(method)
                .componentMeasureIds(List.of(2L, 3L))
                .componentWeights(weights)
                .build();
    }

    private static MeasureDefinition component(long id, String name) {
        return MeasureDefinition.builder().id(id).name(name).version("1.0.0").status("active")
                .scoringType(ScoringTypeConstants.PROPORTION).cqlContent("library " + name + " version '1.0'").build();
    }

    private static PopulationResult pop(String type, int count, Set<String> subjects) {
        return PopulationResult.builder().populationType(type).populationId(type).count(count)
                .subjectIds(subjects == null ? null : new ArrayList<>(subjects)).build();
    }

    /** A component result with counts only (summary report). */
    private static MeasureEvaluationResult counts(int ip, int den, int excl, int num, Double score) {
        return MeasureEvaluationResult.builder()
                .status(EvaluationStatusConstants.COMPLETE)
                .groups(List.of(GroupResult.builder().groupId("group-1").description("Rate")
                        .populations(List.of(pop(INITIAL_POPULATION, ip, null), pop(DENOMINATOR, den, null),
                                pop(DENOMINATOR_EXCLUSION, excl, null), pop(NUMERATOR, num, null)))
                        .measureScore(score).build()))
                .build();
    }

    /** A component result with subject lists (subject-list report). */
    private static MeasureEvaluationResult subjects(Set<String> ip, Set<String> den, Set<String> excl, Set<String> num) {
        return MeasureEvaluationResult.builder()
                .status(EvaluationStatusConstants.COMPLETE)
                .groups(List.of(GroupResult.builder().groupId("group-1")
                        .populations(List.of(pop(INITIAL_POPULATION, ip.size(), ip), pop(DENOMINATOR, den.size(), den),
                                pop(DENOMINATOR_EXCLUSION, excl.size(), excl), pop(NUMERATOR, num.size(), num)))
                        .build()))
                .build();
    }

    private void components(MeasureEvaluationResult r2, MeasureEvaluationResult r3) {
        MeasureDefinition c2 = component(2L, "HbA1c");
        MeasureDefinition c3 = component(3L, "BloodPressure");
        when(definitionService.getById(2L)).thenReturn(Optional.of(c2));
        when(definitionService.getById(3L)).thenReturn(Optional.of(c3));
        when(evaluationService.evaluateMeasure(any(), eq(2L), same(c2))).thenReturn(r2);
        when(evaluationService.evaluateMeasure(any(), eq(3L), same(c3))).thenReturn(r3);
    }

    private static GroupResult summary(MeasureEvaluationResult result) {
        GroupResult g = result.getGroups().get(0);
        assertThat(g.getGroupId()).isEqualTo("composite-summary");
        return g;
    }

    private static Integer count(GroupResult group, String type) {
        return group.getPopulations().stream().filter(p -> type.equals(p.getPopulationType()))
                .map(PopulationResult::getCount).findFirst().orElse(null);
    }

    // ===== PAT-219: the composite parent has its own lifecycle gate =====

    @Test
    void evaluateComposite_parentNotActive_shouldRefuseBeforeLoadingAnyComponent() {
        assertThatThrownBy(() -> service.evaluateComposite(composite("in-review", "opportunity", null), new MeasureEvaluationRequest()))
                .isInstanceOf(MeasureNotEvaluableException.class)
                .hasMessageContaining("Measure 1")
                .hasMessageContaining("'in-review'");
        verifyNoInteractions(definitionService, evaluationService);
    }

    // ===== opportunity =====

    @Test
    void opportunity_sumsNumeratorsOverEffectiveDenominators_andListsTheComponents() {
        components(counts(5, 3, 1, 1, 50.0), counts(6, 4, 0, 2, 50.0));
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setReportType("summary");
        request.setFhirServerUrl("http://fhir");
        request.setConnectionId(9L);

        MeasureEvaluationResult result = service.evaluateComposite(composite("active", "opportunity", null), request);

        // (1 + 2) / ((3 + 4) − 1) = 3 / 6
        assertThat(summary(result).getMeasureScore()).isCloseTo(50.0, within(0.001));
        assertThat(count(summary(result), INITIAL_POPULATION)).isEqualTo(11);
        assertThat(count(summary(result), DENOMINATOR)).isEqualTo(7);
        assertThat(count(summary(result), DENOMINATOR_EXCLUSION)).isEqualTo(1);
        assertThat(count(summary(result), NUMERATOR)).isEqualTo(3);
        assertThat(result.getComponentScores()).extracting(c -> c.getMeasureName() + ":" + c.getNumerator() + "/" + c.getDenominator())
                .containsExactly("HbA1c:1/3", "BloodPressure:2/4");
        assertThat(result.getComponentScores().get(0).getWeight()).isNull();
        // component groups follow the summary, prefixed with the component id
        assertThat(result.getGroups()).extracting(GroupResult::getGroupId)
                .containsExactly("composite-summary", "component-2:group-1", "component-3:group-1");
        assertThat(result.getGroups().get(1).getDescription()).startsWith("HbA1c v1.0.0");
        assertThat(result.getStatus()).isEqualTo(EvaluationStatusConstants.COMPLETE);

        // the components ran through the guarded 3-arg overload, with the caller's connection and report type
        ArgumentCaptor<MeasureEvaluationRequest> sent = ArgumentCaptor.forClass(MeasureEvaluationRequest.class);
        verify(evaluationService).evaluateMeasure(sent.capture(), eq(2L), any());
        assertThat(sent.getValue().getConnectionId()).isEqualTo(9L);
        assertThat(sent.getValue().getFhirServerUrl()).isEqualTo("http://fhir");
        assertThat(sent.getValue().getReportType()).isEqualTo("summary");
        assertThat(sent.getValue().getMeasureCql()).startsWith("library HbA1c");
    }

    // ===== all-or-nothing =====

    @Test
    void allOrNothing_asksTheComponentsForSubjectLists_andCountsOnlyPatientsMeetingEveryEligibleComponent() {
        // A: p3 is excluded; only p1 met.  B: p1 and p2 both met.
        components(subjects(Set.of("p1", "p2", "p3"), Set.of("p1", "p2", "p3"), Set.of("p3"), Set.of("p1")),
                   subjects(Set.of("p1", "p2"), Set.of("p1", "p2"), Set.of(), Set.of("p1", "p2")));
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setReportType("summary");

        MeasureEvaluationResult result = service.evaluateComposite(composite("active", "all-or-nothing", null), request);

        // denominator = {p1, p2} (p3 excluded everywhere); p1 met both, p2 failed A → 1 / 2
        assertThat(summary(result).getMeasureScore()).isCloseTo(50.0, within(0.001));
        assertThat(count(summary(result), INITIAL_POPULATION)).isEqualTo(3);
        assertThat(count(summary(result), DENOMINATOR)).isEqualTo(2);
        assertThat(count(summary(result), NUMERATOR)).isEqualTo(1);
        // the caller asked for a summary: no subject ids leak out, neither on the summary nor on the components
        assertThat(summary(result).getPopulations()).allSatisfy(p -> assertThat(p.getSubjectIds()).isNull());
        assertThat(result.getGroups().get(1).getPopulations()).allSatisfy(p -> assertThat(p.getSubjectIds()).isNull());

        ArgumentCaptor<MeasureEvaluationRequest> sent = ArgumentCaptor.forClass(MeasureEvaluationRequest.class);
        verify(evaluationService).evaluateMeasure(sent.capture(), eq(2L), any());
        assertThat(sent.getValue().getReportType()).isEqualTo("subject-list");
    }

    @Test
    void allOrNothing_subjectListRequested_keepsTheSubjectIds() {
        components(subjects(Set.of("p1", "p2"), Set.of("p1", "p2"), Set.of(), Set.of("p1")),
                   subjects(Set.of("p1", "p2"), Set.of("p1", "p2"), Set.of(), Set.of("p1", "p2")));
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setReportType("subject-list");

        MeasureEvaluationResult result = service.evaluateComposite(composite("active", "all-or-nothing", null), request);

        PopulationResult numerator = summary(result).getPopulations().stream()
                .filter(p -> NUMERATOR.equals(p.getPopulationType())).findFirst().orElseThrow();
        assertThat(numerator.getSubjectIds()).containsExactly("p1");
        assertThat(result.getGroups().get(1).getPopulations().get(0).getSubjectIds()).isNotNull();
    }

    @Test
    void allOrNothing_withoutSubjectLists_hasNoScore_andSaysWhy() {
        components(counts(2, 2, 0, 1, 50.0), counts(2, 2, 0, 2, 100.0));

        MeasureEvaluationResult result = service.evaluateComposite(composite("active", "all-or-nothing", null), new MeasureEvaluationRequest());

        assertThat(summary(result).getMeasureScore()).isNull();
        assertThat(result.getWarnings()).singleElement().asString()
                .contains("all-or-nothing").contains("HbA1c").contains("BloodPressure");
        assertThat(result.getStatus()).isEqualTo(EvaluationStatusConstants.COMPLETE);
    }

    // ===== linear (subject-level) =====

    @Test
    void linear_averagesEachPatientsShareOfMetComponents() {
        // p1: eligible for A and B, met both → 1.0;  p2: eligible for A and B, met B only → 0.5
        components(subjects(Set.of("p1", "p2"), Set.of("p1", "p2"), Set.of(), Set.of("p1")),
                   subjects(Set.of("p1", "p2"), Set.of("p1", "p2"), Set.of(), Set.of("p1", "p2")));

        MeasureEvaluationResult result = service.evaluateComposite(composite("active", "linear", null), new MeasureEvaluationRequest());

        assertThat(summary(result).getMeasureScore()).isCloseTo(75.0, within(0.001));
        assertThat(count(summary(result), DENOMINATOR)).isEqualTo(2);
    }

    // ===== weighted (component-level) =====

    @Test
    void weighted_weightsTheComponentScores_defaultingAMissingWeightToOne() {
        components(counts(10, 10, 0, 4, 40.0), counts(10, 10, 0, 8, 80.0));

        MeasureEvaluationResult result = service.evaluateComposite(
                composite("active", "weighted", Map.of("2", 0.25, "3", 0.75)), new MeasureEvaluationRequest());
        // 0.25 × 40 + 0.75 × 80 = 70
        assertThat(summary(result).getMeasureScore()).isCloseTo(70.0, within(0.001));
        assertThat(result.getComponentScores()).extracting(MeasureEvaluationResult.ComponentScore::getWeight)
                .containsExactly(0.25, 0.75);

        components(counts(10, 10, 0, 4, 40.0), counts(10, 10, 0, 8, 80.0));
        MeasureEvaluationResult equal = service.evaluateComposite(
                composite("active", "WEIGHTED", Map.of("2", 3.0)), new MeasureEvaluationRequest());
        // 3 × 40 + 1 × 80 = 200 / 4
        assertThat(summary(equal).getMeasureScore()).isCloseTo(50.0, within(0.001));
        assertThat(equal.getComponentScores().get(1).getWeight()).isEqualTo(1.0);
    }

    // ===== errors, unknown method, persistence =====

    @Test
    void unknownMethod_isRejected() {
        assertThatThrownBy(() -> service.evaluateComposite(composite("active", "average", null), new MeasureEvaluationRequest()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("average");
    }

    @Test
    void aFailedComponent_makesTheCompositeAnError_namingTheComponent() {
        components(counts(2, 2, 0, 1, 50.0), MeasureEvaluationResult.builder().status("error").errorMessage("boom").groups(List.of()).build());

        MeasureEvaluationResult result = service.evaluateComposite(composite("active", "opportunity", null), new MeasureEvaluationRequest());

        assertThat(result.getStatus()).isEqualTo("error");
        assertThat(result.getErrorMessage()).contains("BloodPressure").contains("boom");
        assertThat(result.getComponentScores().get(1).getStatus()).isEqualTo("error");
    }

    @Test
    void theCompositesOwnReportIsSaved_andASaveFailureDoesNotLoseTheResult() {
        when(reportProvider.getIfAvailable()).thenReturn(reportService);
        components(counts(2, 2, 0, 1, 50.0), counts(2, 2, 0, 2, 100.0));
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setFhirServerUrl("http://fhir");

        MeasureEvaluationResult result = service.evaluateComposite(composite("active", "opportunity", null), request);
        verify(reportService).saveReport(same(result), eq(1L), eq("http://fhir"), isNull(), anyLong());

        doThrow(new IllegalStateException("db down")).when(reportService).saveReport(any(), anyLong(), any(), any(), anyLong());
        components(counts(2, 2, 0, 1, 50.0), counts(2, 2, 0, 2, 100.0));
        MeasureEvaluationResult again = service.evaluateComposite(composite("active", "opportunity", null), request);
        assertThat(again.getStatus()).isEqualTo(EvaluationStatusConstants.COMPLETE);
    }
}
