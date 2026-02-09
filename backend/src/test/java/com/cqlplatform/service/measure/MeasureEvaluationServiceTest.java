package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
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
                cqlExecutionService, patientDiscoveryService,
                populationEvaluator, stratifierEvaluator, scoreCalculator);
        ReflectionTestUtils.setField(measureService, "defaultPeriodStart", "2024-01-01");
        ReflectionTestUtils.setField(measureService, "defaultPeriodEnd", "2024-12-31");
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
        assertThat(result.getGroups().get(0).getDescription()).contains("3");
    }

    @Test
    void evaluateMeasure_shouldUseDefaultPeriodWhenNotProvided() {
        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId("test-measure");
        request.setMeasureCql("library Test version '1.0'");
        request.setPatientId("patient-1");

        when(cqlExecutionService.execute(any())).thenReturn(buildExecResponse(Map.of()));

        MeasureEvaluationResult result = measureService.evaluateMeasure(request);

        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getPeriodEnd()).isEqualTo(LocalDate.of(2024, 12, 31));
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
}
