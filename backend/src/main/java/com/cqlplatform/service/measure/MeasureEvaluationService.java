package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.model.measure.MeasureEvaluationResult.*;
import com.cqlplatform.service.cql.CqlExecutionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opencds.cqf.cql.engine.runtime.Date;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Orchestrates measure evaluation by delegating to focused services.
 * Flow: discover patients → execute CQL → evaluate populations → stratify → score → save.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureEvaluationService {

    private final CqlExecutionService cqlExecutionService;
    private final PatientDiscoveryService patientDiscoveryService;
    private final PopulationEvaluator populationEvaluator;
    private final StratifierEvaluator stratifierEvaluator;
    private final MeasureScoreCalculator scoreCalculator;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MeasureReportService measureReportService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Timer measureEvaluationTimer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter measureEvaluationCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter measureEvaluationErrorCounter;

    @Value("${measure.reporting.default-period-start:2024-01-01}")
    private String defaultPeriodStart;

    @Value("${measure.reporting.default-period-end:2024-12-31}")
    private String defaultPeriodEnd;

    @Value("${cql.execution.measure-timeout-seconds:120}")
    private int measureTimeoutSeconds;

    public MeasureEvaluationResult evaluateMeasure(MeasureEvaluationRequest request) {
        return evaluateMeasure(request, null, null);
    }

    public MeasureEvaluationResult evaluateMeasure(MeasureEvaluationRequest request,
                                                    Long measureDefinitionId,
                                                    MeasureDefinition measureDefinition) {
        log.info("Evaluating measure: {} for patient: {}", request.getMeasureId(), request.getPatientId());
        if (measureEvaluationCounter != null) measureEvaluationCounter.increment();
        Timer.Sample sample = measureEvaluationTimer != null ? Timer.start() : null;
        long startTime = System.currentTimeMillis();

        MeasureEvaluationContext context = buildContext(request, measureDefinitionId, measureDefinition);

        try {
            // 1. Discover patients
            List<String> patients = patientDiscoveryService.discoverPatients(context);
            if (patients.isEmpty()) {
                stopTimer(sample);
                incrementErrorCounter();
                return errorResult(context, patientDiscoveryService.buildNoPatientsMessage(context));
            }

            // 2. Execute CQL per patient and aggregate
            AggregationState state = executeAndAggregate(context, patients);

            // 3. Check if all patients failed
            if (state.errorCount == patients.size()) {
                stopTimer(sample);
                incrementErrorCounter();
                return errorResult(context,
                        "All " + state.errorCount + " patient evaluations failed. Check server logs for details.");
            }

            // 4. Build final result
            MeasureEvaluationResult result = buildResult(context, state, patients.size());
            stopTimer(sample);

            // 5. Auto-save report
            long durationMs = System.currentTimeMillis() - startTime;
            autoSaveReport(result, measureDefinitionId, context.getFhirServerUrl(), durationMs);

            return result;

        } catch (Exception e) {
            incrementErrorCounter();
            stopTimer(sample);
            log.error("Measure evaluation failed", e);
            return errorResult(context, e.getMessage());
        }
    }

    private MeasureEvaluationContext buildContext(MeasureEvaluationRequest request,
                                                  Long measureDefinitionId,
                                                  MeasureDefinition measureDefinition) {
        LocalDate periodStart = request.getPeriodStart() != null
                ? request.getPeriodStart() : LocalDate.parse(defaultPeriodStart);
        LocalDate periodEnd = request.getPeriodEnd() != null
                ? request.getPeriodEnd() : LocalDate.parse(defaultPeriodEnd);

        return MeasureEvaluationContext.builder()
                .request(request)
                .measureDefinition(measureDefinition)
                .measureDefinitionId(measureDefinitionId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .timeoutSeconds(measureTimeoutSeconds)
                .build();
    }

    private AggregationState executeAndAggregate(MeasureEvaluationContext context, List<String> patients) {
        Map<String, Integer> populationCounts = populationEvaluator.initializePopulationCounts();
        Set<String> standardNames = new HashSet<>(populationCounts.keySet());
        Map<String, Object> customExpressions = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Integer>>> stratificationData = new HashMap<>();
        List<StratifierDefinition> stratifiers = stratifierEvaluator.getStratifiers(context.getMeasureDefinition());

        int errorCount = 0;
        long deadline = System.currentTimeMillis() + (context.getTimeoutSeconds() * 1000L);

        for (String patientId : patients) {
            if (System.currentTimeMillis() > deadline) {
                log.warn("Measure evaluation timed out after {}s", context.getTimeoutSeconds());
                break;
            }

            try {
                CqlExecutionResponse execResponse = executeForPatient(context, patientId);
                Map<String, CqlExecutionResponse.ExpressionResult> results = execResponse.getResults();

                populationEvaluator.aggregatePatientResults(populationCounts, results);
                populationEvaluator.aggregateCustomExpressions(customExpressions, results, standardNames);

                if (!stratifiers.isEmpty()) {
                    stratifierEvaluator.evaluatePatientStratifiers(stratifiers, results, stratificationData);
                }
            } catch (Exception e) {
                errorCount++;
                log.error("Failed evaluation for patient {}", patientId, e);
            }
        }

        return new AggregationState(populationCounts, customExpressions, stratificationData, errorCount);
    }

    private CqlExecutionResponse executeForPatient(MeasureEvaluationContext context, String patientId) {
        CqlExecutionRequest execRequest = new CqlExecutionRequest();
        execRequest.setCql(context.getMeasureCql());
        execRequest.setPatientId(patientId);
        execRequest.setFhirServerUrl(context.getFhirServerUrl());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("Measurement Period",
                new Interval(
                        new Date(context.getPeriodStart().getYear(),
                                context.getPeriodStart().getMonthValue(),
                                context.getPeriodStart().getDayOfMonth()),
                        true,
                        new Date(context.getPeriodEnd().getYear(),
                                context.getPeriodEnd().getMonthValue(),
                                context.getPeriodEnd().getDayOfMonth()),
                        true));
        execRequest.setParameters(parameters);

        return cqlExecutionService.execute(execRequest);
    }

    private MeasureEvaluationResult buildResult(MeasureEvaluationContext context,
                                                 AggregationState state,
                                                 int totalPatients) {
        Map<String, Integer> counts = state.populationCounts;

        List<PopulationResult> populations = new ArrayList<>();
        populations.add(populationResult("initial-population", counts.get("Initial Population")));
        populations.add(populationResult("denominator", counts.get("Denominator")));

        if (counts.get("Denominator Exclusions") > 0)
            populations.add(populationResult("denominator-exclusion", counts.get("Denominator Exclusions")));
        if (counts.get("Denominator Exceptions") > 0)
            populations.add(populationResult("denominator-exception", counts.get("Denominator Exceptions")));

        populations.add(populationResult("numerator", counts.get("Numerator")));

        if (counts.get("Numerator Exclusions") > 0)
            populations.add(populationResult("numerator-exclusion", counts.get("Numerator Exclusions")));

        Double measureScore = scoreCalculator.calculateProportionScore(
                counts.get("Denominator"), counts.get("Denominator Exclusions"), counts.get("Numerator"));

        List<StratifierResult> stratifierResults = stratifierEvaluator.buildStratifierResults(state.stratificationData);

        GroupResult groupResult = GroupResult.builder()
                .groupId("group-1")
                .description("Primary measure group (Total Patients: " + totalPatients + ")")
                .populations(populations)
                .measureScore(measureScore)
                .measureScoreUnit("percentage")
                .stratifiers(stratifierResults.isEmpty() ? null : stratifierResults)
                .build();

        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .measureName(context.getMeasureId())
                .status("complete")
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
                .reportType(context.getReportType())
                .groups(List.of(groupResult))
                .supplementalData(state.customExpressions.isEmpty() ? null : state.customExpressions)
                .build();
    }

    private static PopulationResult populationResult(String type, Integer count) {
        return PopulationResult.builder()
                .populationType(type)
                .populationId(type)
                .count(count)
                .build();
    }

    private MeasureEvaluationResult errorResult(MeasureEvaluationContext context, String message) {
        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .status("error")
                .errorMessage(message)
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
                .build();
    }

    private void autoSaveReport(MeasureEvaluationResult result, Long measureDefinitionId,
                                String fhirServerUrl, long durationMs) {
        if (measureReportService == null) return;
        try {
            measureReportService.saveReport(result, measureDefinitionId, fhirServerUrl, null, durationMs);
        } catch (Exception e) {
            log.warn("Failed to auto-save measure report, evaluation result is still valid", e);
        }
    }

    private void stopTimer(Timer.Sample sample) {
        if (sample != null && measureEvaluationTimer != null) sample.stop(measureEvaluationTimer);
    }

    private void incrementErrorCounter() {
        if (measureEvaluationErrorCounter != null) measureEvaluationErrorCounter.increment();
    }

    /** Internal state holder for the aggregation loop. */
    private record AggregationState(
            Map<String, Integer> populationCounts,
            Map<String, Object> customExpressions,
            Map<String, Map<String, Map<String, Integer>>> stratificationData,
            int errorCount
    ) {}
}
