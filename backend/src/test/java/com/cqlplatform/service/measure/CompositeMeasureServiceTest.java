package com.cqlplatform.service.measure;

import com.cqlplatform.exception.MeasureNotEvaluableException;
import com.cqlplatform.model.measure.EvaluationStatusConstants;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.GroupResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.PopulationResult;
import com.cqlplatform.model.measure.PopulationTypeConstants;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeMeasureServiceTest {

    @Mock
    private MeasureDefinitionService definitionService;

    @Mock
    private MeasureEvaluationService evaluationService;

    @InjectMocks
    private CompositeMeasureService service;

    private static MeasureDefinition composite(String status) {
        return MeasureDefinition.builder()
                .id(1L).name("Composite").status(status)
                .scoringType(ScoringTypeConstants.COMPOSITE)
                .compositeScoring(ScoringTypeConstants.COMPOSITE_OPPORTUNITY)
                .componentMeasureIds(List.of(2L, 3L))
                .build();
    }

    private static MeasureEvaluationResult componentResult(int numerator, int denominator) {
        return MeasureEvaluationResult.builder()
                .status(EvaluationStatusConstants.COMPLETE)
                .groups(List.of(GroupResult.builder()
                        .groupId("component-group")
                        .populations(List.of(
                                PopulationResult.builder()
                                        .populationType(PopulationTypeConstants.NUMERATOR).count(numerator).build(),
                                PopulationResult.builder()
                                        .populationType(PopulationTypeConstants.DENOMINATOR).count(denominator).build()))
                        .build()))
                .build();
    }

    // ===== PAT-219: the composite parent has its own lifecycle gate =====

    @Test
    void evaluateComposite_parentNotActive_shouldRefuseBeforeLoadingAnyComponent() {
        assertThatThrownBy(() -> service.evaluateComposite(composite("in-review"), new MeasureEvaluationRequest()))
                .isInstanceOf(MeasureNotEvaluableException.class)
                .hasMessageContaining("Measure 1")
                .hasMessageContaining("'in-review'");

        // Nothing downstream may run: no component lookup, no evaluation.
        verifyNoInteractions(definitionService, evaluationService);
    }

    @Test
    void evaluateComposite_parentActive_shouldEvaluateEachComponentThroughTheGuardedEntryPoint() {
        MeasureDefinition c2 = MeasureDefinition.builder().id(2L).name("C2").status("active")
                .cqlContent("library C2 version '1.0'").build();
        MeasureDefinition c3 = MeasureDefinition.builder().id(3L).name("C3").status("active")
                .cqlContent("library C3 version '1.0'").build();
        when(definitionService.getById(2L)).thenReturn(Optional.of(c2));
        when(definitionService.getById(3L)).thenReturn(Optional.of(c3));
        when(evaluationService.evaluateMeasure(any(), eq(2L), same(c2))).thenReturn(componentResult(1, 2));
        when(evaluationService.evaluateMeasure(any(), eq(3L), same(c3))).thenReturn(componentResult(1, 2));

        MeasureEvaluationResult result = service.evaluateComposite(composite("active"), new MeasureEvaluationRequest());

        assertThat(result.getStatus()).isEqualTo(EvaluationStatusConstants.COMPLETE);
        assertThat(result.getGroups().get(0).getGroupId()).isEqualTo("composite-summary");
        // Opportunity scoring: (1 + 1) / (2 + 2) * 100
        assertThat(result.getGroups().get(0).getMeasureScore()).isEqualTo(50.0);
        // Components are always evaluated via the 3-arg overload that carries the definition —
        // that is the overload MeasureStatusGuard sits in front of.
        verify(evaluationService).evaluateMeasure(any(), eq(2L), same(c2));
        verify(evaluationService).evaluateMeasure(any(), eq(3L), same(c3));
    }
}
