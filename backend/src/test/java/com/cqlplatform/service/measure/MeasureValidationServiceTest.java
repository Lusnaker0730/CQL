package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.CqlTranslationResponse.TranslationMetadata;
import com.cqlplatform.model.CqlTranslationResponse.ExpressionInfo;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.service.cql.CqlTranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasureValidationServiceTest {

    @Mock
    private MeasureDefinitionService definitionService;

    @Mock
    private CqlTranslationService translationService;

    @Mock
    private TestCaseService testCaseService;

    @InjectMocks
    private MeasureValidationService service;

    private MeasureDefinition createMinimalMeasure() {
        return MeasureDefinition.builder()
                .name("TestMeasure")
                .version("1.0.0")
                .scoringType("proportion")
                .title("Test Measure Title")
                .description("Test description")
                .cqlContent("library Test version '1.0'\ndefine \"IP\": true")
                .build();
    }

    private CqlTranslationResponse createSuccessfulTranslation() {
        ExpressionInfo ipExpr = ExpressionInfo.builder()
                .name("IP")
                .resultType("System.Boolean")
                .accessLevel("Public")
                .build();

        TranslationMetadata metadata = TranslationMetadata.builder()
                .libraryId("Test")
                .libraryVersion("1.0")
                .expressions(List.of(ipExpr))
                .build();

        return CqlTranslationResponse.builder()
                .success(true)
                .metadata(metadata)
                .build();
    }

    // ===== validateMetadata =====

    @Test
    void validateFull_missingName_shouldReportError() {
        MeasureDefinition measure = createMinimalMeasure();
        measure.setName(null);

        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));
        when(translationService.translate(any())).thenReturn(createSuccessfulTranslation());
        when(testCaseService.getTestCasesForMeasure(1L)).thenReturn(List.of());

        ValidationReport report = service.validateFull(1L);

        assertThat(report.getIssues().stream().filter(i -> "ERROR".equals(i.getSeverity())).toList()).anyMatch(e -> e.getMessage().contains("name is required"));
    }

    @Test
    void validateFull_missingVersion_shouldReportError() {
        MeasureDefinition measure = createMinimalMeasure();
        measure.setVersion(null);

        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));
        when(translationService.translate(any())).thenReturn(createSuccessfulTranslation());
        when(testCaseService.getTestCasesForMeasure(1L)).thenReturn(List.of());

        ValidationReport report = service.validateFull(1L);

        assertThat(report.getIssues().stream().filter(i -> "ERROR".equals(i.getSeverity())).toList()).anyMatch(e -> e.getMessage().contains("version is required"));
    }

    @Test
    void validateFull_missingTitle_shouldReportWarning() {
        MeasureDefinition measure = createMinimalMeasure();
        measure.setTitle(null);

        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));
        when(translationService.translate(any())).thenReturn(createSuccessfulTranslation());
        when(testCaseService.getTestCasesForMeasure(1L)).thenReturn(List.of());

        ValidationReport report = service.validateFull(1L);

        assertThat(report.getIssues().stream().filter(i -> "WARNING".equals(i.getSeverity())).toList()).anyMatch(w -> w.getMessage().contains("title is recommended"));
    }

    // ===== validatePopulations =====

    @Test
    void validateFull_proportionMissingRequiredPopulations_shouldReportErrors() {
        MeasureDefinition measure = createMinimalMeasure();
        measure.setScoringType("proportion");

        // Group with some populations but missing required ones
        PopulationDefinition ip = new PopulationDefinition();
        ip.setPopulationType("initial-population");
        ip.setCriteriaExpression("IP");

        GroupDefinition group = new GroupDefinition();
        group.setGroupId("group-1");
        group.setPopulations(List.of(ip));
        measure.setGroupDefinitions(List.of(group));

        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));
        when(translationService.translate(any())).thenReturn(createSuccessfulTranslation());
        when(testCaseService.getTestCasesForMeasure(1L)).thenReturn(List.of());

        ValidationReport report = service.validateFull(1L);

        var errors = report.getIssues().stream().filter(i -> "ERROR".equals(i.getSeverity())).toList();
        // Should have errors for missing denominator and numerator
        assertThat(errors).anyMatch(e -> e.getMessage().contains("denominator"));
        assertThat(errors).anyMatch(e -> e.getMessage().contains("numerator"));
    }

    @Test
    void validateFull_expressionNotInCql_shouldReportError() {
        MeasureDefinition measure = createMinimalMeasure();
        measure.setScoringType("cohort");

        PopulationDefinition pop = new PopulationDefinition();
        pop.setPopulationType("initial-population");
        pop.setCriteriaExpression("NonExistent");

        GroupDefinition group = new GroupDefinition();
        group.setGroupId("group-1");
        group.setPopulations(List.of(pop));
        measure.setGroupDefinitions(List.of(group));

        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));
        when(translationService.translate(any())).thenReturn(createSuccessfulTranslation());
        when(testCaseService.getTestCasesForMeasure(1L)).thenReturn(List.of());

        ValidationReport report = service.validateFull(1L);

        assertThat(report.getIssues().stream().filter(i -> "ERROR".equals(i.getSeverity())).toList()).anyMatch(e -> e.getMessage().contains("not found in CQL"));
    }

    // ===== validateTestCases =====

    @Test
    void validateFull_noTestCases_shouldReportWarning() {
        MeasureDefinition measure = createMinimalMeasure();
        measure.setScoringType("cohort");

        PopulationDefinition pop = new PopulationDefinition();
        pop.setPopulationType("initial-population");
        pop.setCriteriaExpression("IP");

        GroupDefinition group = new GroupDefinition();
        group.setGroupId("group-1");
        group.setPopulations(List.of(pop));
        measure.setGroupDefinitions(List.of(group));

        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));
        when(translationService.translate(any())).thenReturn(createSuccessfulTranslation());
        when(testCaseService.getTestCasesForMeasure(1L)).thenReturn(List.of());

        ValidationReport report = service.validateFull(1L);

        assertThat(report.getIssues().stream().filter(i -> "WARNING".equals(i.getSeverity())).toList()).anyMatch(w -> w.getMessage().contains("No test cases"));
    }

    @Test
    void validateFull_measureNotFound_shouldThrow() {
        when(definitionService.getById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateFull(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ===== validateQuick =====

    @Test
    void validateQuick_validMeasure_shouldReturnReport() {
        MeasureDefinition measure = createMinimalMeasure();
        measure.setScoringType("cohort");

        PopulationDefinition pop = new PopulationDefinition();
        pop.setPopulationType("initial-population");
        pop.setCriteriaExpression("IP");

        GroupDefinition group = new GroupDefinition();
        group.setGroupId("group-1");
        group.setPopulations(List.of(pop));
        measure.setGroupDefinitions(List.of(group));

        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));
        when(translationService.translate(any())).thenReturn(createSuccessfulTranslation());

        ValidationReport report = service.validateQuick(1L);

        assertThat(report).isNotNull();
        // Quick validate should not check test cases
        verify(testCaseService, never()).getTestCasesForMeasure(anyLong());
    }

    // ===== No CQL content =====

    @Test
    void validateFull_noCqlContent_shouldReportError() {
        MeasureDefinition measure = createMinimalMeasure();
        measure.setCqlContent(null);

        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));
        when(testCaseService.getTestCasesForMeasure(1L)).thenReturn(List.of());

        ValidationReport report = service.validateFull(1L);

        assertThat(report.getIssues().stream().filter(i -> "ERROR".equals(i.getSeverity())).toList()).anyMatch(e -> e.getMessage().contains("CQL content is required"));
    }
}
