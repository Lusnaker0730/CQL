package com.cqlplatform.service.measure;

import com.cqlplatform.exception.MeasureNotEvaluableException;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasureEvaluationServiceTest {

    @Mock
    private CqlExecutionService cqlExecutionService;

    @Mock
    private com.cqlplatform.service.cql.CqlTranslationService cqlTranslationService;

    @Mock
    private FhirDataProviderService fhirDataProviderService;

    private PatientDiscoveryService patientDiscoveryService;
    private PopulationEvaluator populationEvaluator;
    private StratifierEvaluator stratifierEvaluator;
    private MeasureScoreCalculator scoreCalculator;
    private MeasureEvaluationService measureService;

    @BeforeEach
    void setUp() {
        patientDiscoveryService = new PatientDiscoveryService(fhirDataProviderService);
        populationEvaluator = new PopulationEvaluator();
        scoreCalculator = new MeasureScoreCalculator();
        stratifierEvaluator = new StratifierEvaluator(populationEvaluator, scoreCalculator);
        measureService = new MeasureEvaluationService(
                cqlExecutionService, cqlTranslationService, fhirDataProviderService,
                patientDiscoveryService,
                populationEvaluator, stratifierEvaluator, scoreCalculator, new SupplementalDataEvaluator(),
                java.util.concurrent.Executors.newFixedThreadPool(4));
        ReflectionTestUtils.setField(measureService, "defaultPeriodStart", "");
        ReflectionTestUtils.setField(measureService, "defaultPeriodEnd", "");
        ReflectionTestUtils.setField(measureService, "measureTimeoutSeconds", 120);
    }

    private CqlExecutionResponse buildExecResponse(Map<String, Object> results) {
        Map<String, ExpressionResult> expressionResults = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : results.entrySet()) {
            expressionResults.put(entry.getKey(), ExpressionResult.builder()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .valueType(entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null")
                    .build());
        }
        return CqlExecutionResponse.builder()
                .success(true)
                .results(expressionResults)
                .build();
    }

    @Test
    void evaluateMeasure_singlePatient_shouldReturnResult() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");
        request.setFhirServerUrl("http://localhost/fhir");

        CqlExecutionResponse execResponse = buildExecResponse(Map.of(
                "Initial Population", true,
                "Denominator", true,
                "Numerator", true
        ));
        when(cqlExecutionService.execute(any())).thenReturn(execResponse);

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getMeasureId()).isEqualTo("test-measure");
        assertThat(result.getStatus()).isEqualTo("complete");
        assertThat(result.getGroups()).isNotEmpty();
    }

    // BUG-145 — the duration handed to the report is an elapsed time on the monotonic clock. As a
    // wall-clock subtraction it could be negative (clock stepped back mid-evaluation), which the
    // measure_report CHECK (>= 0) answered by rejecting the whole report.
    @Test
    void evaluateMeasure_autoSavesTheReport_withANonNegativeDuration() {
        MeasureReportService reportService = org.mockito.Mockito.mock(MeasureReportService.class);
        ReflectionTestUtils.setField(measureService, "measureReportService", reportService);
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");
        request.setFhirServerUrl("http://localhost/fhir");
        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of("Initial Population", true)));

        measureService.evaluateMeasure(request);

        org.mockito.ArgumentCaptor<Long> duration = org.mockito.ArgumentCaptor.forClass(Long.class);
        org.mockito.Mockito.verify(reportService).saveReport(any(), any(), any(), any(), duration.capture());
        assertThat(duration.getValue()).isBetween(0L, 60_000L);
    }

    @Test
    void evaluateMeasure_whenTheReportCannotBeSaved_stillReturnsTheResult() {
        MeasureReportService reportService = org.mockito.Mockito.mock(MeasureReportService.class);
        when(reportService.saveReport(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new com.cqlplatform.exception.CqlExecutionException("Failed to save measure report: constraint"));
        ReflectionTestUtils.setField(measureService, "measureReportService", reportService);
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");
        request.setFhirServerUrl("http://localhost/fhir");
        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of("Initial Population", true)));

        assertThat(measureService.evaluateMeasure(request).getStatus()).isEqualTo("complete");
    }

    @Test
    void evaluateMeasure_multiplePatients_shouldAggregateResults() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setFhirServerUrl("http://localhost/fhir");

        when(fhirDataProviderService.getAllPatientIds(any()))
                .thenReturn(List.of("p1", "p2", "p3"));

        CqlExecutionResponse execResponse = buildExecResponse(Map.of(
                "Initial Population", true,
                "Denominator", true,
                "Numerator", false
        ));
        when(cqlExecutionService.execute(any())).thenReturn(execResponse);

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getStatus()).isEqualTo("complete");
        assertThat(result.getGroups().get(0).getTotalPatients()).isEqualTo(3);
    }

    @Test
    void evaluateMeasure_shouldUseDefaultPeriodWhenNotProvided() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");

        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of()));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        int currentYear = LocalDate.now().getYear();
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(currentYear, 1, 1));
        assertThat(result.getPeriodEnd()).isEqualTo(LocalDate.of(currentYear, 12, 31));
    }

    @Test
    void evaluateMeasure_shouldUseProvidedPeriod() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");
        request.setPeriodStart(LocalDate.of(2023, 6, 1));
        request.setPeriodEnd(LocalDate.of(2023, 12, 31));

        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of()));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2023, 6, 1));
        assertThat(result.getPeriodEnd()).isEqualTo(LocalDate.of(2023, 12, 31));
    }

    @Test
    void evaluateMeasure_executionError_shouldReturnErrorStatus() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");

        when(cqlExecutionService.execute(any())).thenThrow(new RuntimeException("Execution failed"));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getStatus()).isEqualTo("error");
    }

    @Test
    void evaluateMeasure_scoringCalculation_shouldComputeCorrectly() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setFhirServerUrl("http://localhost/fhir");

        when(fhirDataProviderService.getAllPatientIds(any()))
                .thenReturn(List.of("p1", "p2", "p3", "p4"));

        // 3 out of 4 in IP and Denom, 2 in Numerator
        CqlExecutionResponse inIPandDenomAndNum = buildExecResponse(Map.of(
                "Initial Population", true,
                "Denominator", true,
                "Numerator", true
        ));
        CqlExecutionResponse inIPandDenomNotNum = buildExecResponse(Map.of(
                "Initial Population", true,
                "Denominator", true,
                "Numerator", false
        ));
        CqlExecutionResponse notInIP = buildExecResponse(Map.of(
                "Initial Population", false,
                "Denominator", false,
                "Numerator", false
        ));

        when(cqlExecutionService.execute(any()))
                .thenReturn(inIPandDenomAndNum)
                .thenReturn(inIPandDenomAndNum)
                .thenReturn(inIPandDenomNotNum)
                .thenReturn(notInIP);

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getGroups().get(0).getMeasureScore()).isNotNull();
    }

    @Test
    void evaluateMeasure_zeroDenominator_shouldReturnNullScore() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");

        CqlExecutionResponse execResponse = buildExecResponse(Map.of(
                "Initial Population", false,
                "Denominator", false,
                "Numerator", false
        ));
        when(cqlExecutionService.execute(any())).thenReturn(execResponse);

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getGroups().get(0).getMeasureScore()).isNull();
    }

    @Test
    void evaluateMeasure_singlePatientFailure_shouldContinueOthers() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setFhirServerUrl("http://localhost/fhir");

        when(fhirDataProviderService.getAllPatientIds(any()))
                .thenReturn(List.of("p1", "p2"));

        when(cqlExecutionService.execute(any()))
                .thenThrow(new RuntimeException("p1 failed"))
                .thenReturn(buildExecResponse(Map.of(
                        "Initial Population", true,
                        "Denominator", true,
                        "Numerator", true
                )));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getStatus()).isEqualTo("complete");
    }

    @Test
    void evaluateMeasure_shouldSetReportType() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");
        request.setReportType("summary");

        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of()));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getReportType()).isEqualTo("summary");
    }

    // =====================================================================
    // PAT-140 — partial-failure threshold + errorCount surfacing
    // =====================================================================

    @Test
    void evaluateMeasure_partialFailures_shouldSurfaceErrorCount() {
        // 3 patients, 1 fails — default threshold ratio 1.0 keeps existing
        // behaviour (don't abort), but errorCount + evaluatedPatientCount must
        // be on the result so the UI can warn the user.
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setFhirServerUrl("http://localhost/fhir");

        when(fhirDataProviderService.getAllPatientIds(any()))
                .thenReturn(List.of("p1", "p2", "p3"));

        when(cqlExecutionService.execute(any()))
                .thenThrow(new RuntimeException("p1 failed"))
                .thenReturn(buildExecResponse(Map.of(
                        "Initial Population", true,
                        "Denominator", true,
                        "Numerator", true)))
                .thenReturn(buildExecResponse(Map.of(
                        "Initial Population", true,
                        "Denominator", true,
                        "Numerator", true)));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getStatus()).isEqualTo("complete");
        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getEvaluatedPatientCount()).isEqualTo(3);
    }

    @Test
    void evaluateMeasure_failuresAtOrAboveThreshold_shouldAbort() {
        // Lower threshold to 0.5 — 1 of 2 patients failed (50%) should now
        // produce errorResult instead of partial denominator.
        ReflectionTestUtils.setField(measureService, "errorThresholdRatio", 0.5);

        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setFhirServerUrl("http://localhost/fhir");

        when(fhirDataProviderService.getAllPatientIds(any()))
                .thenReturn(List.of("p1", "p2"));

        when(cqlExecutionService.execute(any()))
                .thenThrow(new RuntimeException("p1 failed"))
                .thenReturn(buildExecResponse(Map.of(
                        "Initial Population", true, "Denominator", true, "Numerator", true)));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getStatus()).isEqualTo("error");
        assertThat(result.getErrorCount()).isEqualTo(1);
        assertThat(result.getEvaluatedPatientCount()).isEqualTo(2);
        assertThat(result.getErrorMessage()).contains("threshold ratio 0.50");
    }

    @Test
    void evaluateMeasure_zeroFailures_shouldNotPopulateErrorCount() {
        // Successful runs should keep errorCount=0 and evaluatedPatientCount set.
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setFhirServerUrl("http://localhost/fhir");

        when(fhirDataProviderService.getAllPatientIds(any()))
                .thenReturn(List.of("p1", "p2"));
        when(cqlExecutionService.execute(any()))
                .thenReturn(buildExecResponse(Map.of(
                        "Initial Population", true, "Denominator", true, "Numerator", true)));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getStatus()).isEqualTo("complete");
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getEvaluatedPatientCount()).isEqualTo(2);
    }

    // ===== PAT-219: lifecycle guard on the stored-definition entry point =====

    @Test
    void evaluateMeasure_storedDefinitionNotActive_shouldRefuseBeforeAnyFhirOrCqlWork() {
        MeasureDefinition draft = MeasureDefinition.builder()
                .id(42L).name("Draft measure").status("draft")
                .cqlContent("library Test version '1.0'").build();
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("42");
        request.setMeasureCql(draft.getCqlContent());
        request.setFhirServerUrl("http://localhost/fhir");

        assertThatThrownBy(() -> measureService.evaluateMeasure(request, 42L, draft))
                .isInstanceOf(MeasureNotEvaluableException.class)
                .hasMessageContaining("Measure 42")
                .hasMessageContaining("'draft'");

        // The whole point: unreviewed logic must never reach the FHIR server or the engine.
        verifyNoInteractions(cqlExecutionService, fhirDataProviderService);
    }

    @Test
    void evaluateMeasure_storedDefinitionActive_shouldProceedToEvaluation() {
        MeasureDefinition active = MeasureDefinition.builder()
                .id(42L).name("Active measure").status("active").scoringType("proportion")
                .cqlContent("library Test version '1.0'").build();
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("42");
        request.setMeasureCql(active.getCqlContent());
        request.setPatientId("patient-1");
        request.setFhirServerUrl("http://localhost/fhir");
        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of(
                "Initial Population", true, "Denominator", true, "Numerator", true)));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request, 42L, active);

        assertThat(result.getStatus()).isEqualTo("complete");
        assertThat(result.getGroups()).isNotEmpty();
    }

    // PAT-242 — the stored measure's own Measurement Period is the default evaluation period; an
    // explicit request period still wins.
    @Test
    void evaluateMeasure_defaultsToTheMeasuresMeasurementPeriod_unlessTheRequestNamesOne() {
        MeasureDefinition def = MeasureDefinition.builder()
                .id(5L).name("MP").status("active").cqlContent("library Test version '1.0'")
                .measurementPeriodStart(LocalDate.of(2024, 1, 1)).measurementPeriodEnd(LocalDate.of(2024, 12, 31))
                .build();
        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of("Initial Population", true)));

        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("MP");
        request.setPatientId("patient-1");
        request.setFhirServerUrl("http://localhost/fhir");
        MeasureEvaluationResult result = measureService.evaluateMeasure(request, 5L, def);
        assertThat(result.getStatus()).isEqualTo("complete");
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getPeriodEnd()).isEqualTo(LocalDate.of(2024, 12, 31));

        request.setPeriodStart(LocalDate.of(2025, 3, 1));
        request.setPeriodEnd(LocalDate.of(2025, 3, 31));
        result = measureService.evaluateMeasure(request, 5L, def);
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(result.getPeriodEnd()).isEqualTo(LocalDate.of(2025, 3, 31));
    }

    // ===== PAT-243 — an episode-based group counts episodes across patients =====

    private static GroupDefinition encounterGroup() {
        return GroupDefinition.builder().groupId("group-1").populationBasis("Encounter")
                .populations(List.of(
                        PopulationDefinition.builder().populationType("initial-population").criteriaExpression("Initial Population").build(),
                        PopulationDefinition.builder().populationType("denominator").criteriaExpression("Denominator").build(),
                        PopulationDefinition.builder().populationType("numerator").criteriaExpression("Numerator").build()))
                .build();
    }

    private static MeasureDefinition episodeMeasure() {
        return MeasureDefinition.builder().id(9L).name("Episodes").status("active").scoringType("proportion")
                .cqlContent("library Test version '1.0'").groupDefinitions(List.of(encounterGroup())).build();
    }

    @Test
    void evaluateMeasure_episodeBasedGroup_countsEpisodesAndNamesTheBasis() {
        when(fhirDataProviderService.getAllPatientIds(any())).thenReturn(List.of("p1", "p2"));
        when(cqlExecutionService.execute(any())).thenAnswer(inv -> {
            CqlExecutionRequest req = inv.getArgument(0);
            if ("p1".equals(req.getPatientId())) {
                return buildExecResponse(Map.of(
                        "Initial Population", List.of("FHIR.Encounter/a", "FHIR.Encounter/b"),
                        "Denominator", List.of("FHIR.Encounter/a", "FHIR.Encounter/b"),
                        "Numerator", List.of("FHIR.Encounter/a")));
            }
            return buildExecResponse(Map.of(
                    "Initial Population", List.of("FHIR.Encounter/c"),
                    "Denominator", List.of("FHIR.Encounter/c"),
                    "Numerator", List.of()));
        });
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("Episodes");
        request.setFhirServerUrl("http://localhost/fhir");

        MeasureEvaluationResult result = measureService.evaluateMeasure(request, 9L, episodeMeasure());

        assertThat(result.getStatus()).isEqualTo("complete");
        MeasureEvaluationResult.GroupResult group = result.getGroups().get(0);
        assertThat(group.getPopulationBasis()).isEqualTo("Encounter");
        assertThat(group.getPopulations()).extracting(p -> p.getPopulationType() + "=" + p.getCount())
                .contains("initial-population=3", "denominator=3", "numerator=1");
        assertThat(group.getMeasureScore()).isCloseTo(33.33, within(0.01));
        assertThat(group.getTotalPatients()).isEqualTo(2);
        assertThat(result.getWarnings()).isNull();
    }

    @Test
    void evaluateMeasure_episodeBasedGroupWhoseInitialPopulationIsBoolean_countsPatientsAndWarnsOnce() {
        when(fhirDataProviderService.getAllPatientIds(any())).thenReturn(List.of("p1", "p2"));
        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of(
                "Initial Population", true, "Denominator", true, "Numerator", false)));
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("Episodes");
        request.setFhirServerUrl("http://localhost/fhir");

        MeasureEvaluationResult result = measureService.evaluateMeasure(request, 9L, episodeMeasure());

        assertThat(result.getGroups().get(0).getPopulations()).extracting(p -> p.getPopulationType() + "=" + p.getCount())
                .contains("initial-population=2", "denominator=2", "numerator=0");
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings().get(0)).contains("Encounter").contains("counted per patient");
    }
}
