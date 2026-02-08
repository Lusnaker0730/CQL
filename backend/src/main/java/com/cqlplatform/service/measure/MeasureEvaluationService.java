package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.*;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.StratifierDefinition;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.fhir.FhirDataProviderService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureEvaluationService {

    private final CqlExecutionService cqlExecutionService;
    private final FhirDataProviderService fhirDataProviderService;

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
        log.info("Evaluating measure: {} for patient: {}",
                request.getMeasureId(), request.getPatientId());
        if (measureEvaluationCounter != null) measureEvaluationCounter.increment();
        Timer.Sample sample = measureEvaluationTimer != null ? Timer.start() : null;
        long startTime = System.currentTimeMillis();

        LocalDate periodStart = request.getPeriodStart() != null ? request.getPeriodStart()
                : LocalDate.parse(defaultPeriodStart);
        LocalDate periodEnd = request.getPeriodEnd() != null ? request.getPeriodEnd()
                : LocalDate.parse(defaultPeriodEnd);

        try {
            List<String> patientsToEvaluate;
            if (request.getPatientId() != null && !request.getPatientId().isBlank()) {
                patientsToEvaluate = List.of(request.getPatientId());
            } else {
                patientsToEvaluate = fhirDataProviderService
                        .getAllPatientIds(request.getFhirServerUrl());
            }

            log.info("Evaluating for {} patients", patientsToEvaluate.size());

            Map<String, Integer> populationCounts = new HashMap<>();
            populationCounts.put("Initial Population", 0);
            populationCounts.put("Denominator", 0);
            populationCounts.put("Denominator Exclusions", 0);
            populationCounts.put("Denominator Exceptions", 0);
            populationCounts.put("Numerator", 0);
            populationCounts.put("Numerator Exclusions", 0);

            Set<String> standardPopulations = new HashSet<>(populationCounts.keySet());
            Map<String, Object> customExpressions = new LinkedHashMap<>();

            // Stratification tracking: stratifierId -> strataValue -> populationType -> count
            Map<String, Map<String, Map<String, Integer>>> stratificationData = new HashMap<>();
            List<StratifierDefinition> stratifiers = getStratifiers(measureDefinition);

            int errorCount = 0;
            long deadline = System.currentTimeMillis() + (measureTimeoutSeconds * 1000L);
            for (String patientId : patientsToEvaluate) {
                if (System.currentTimeMillis() > deadline) {
                    log.warn("Measure evaluation timed out after {}s", measureTimeoutSeconds);
                    break;
                }
                CqlExecutionRequest execRequest = new CqlExecutionRequest();
                execRequest.setCql(request.getMeasureCql());
                execRequest.setPatientId(patientId);
                execRequest.setFhirServerUrl(request.getFhirServerUrl());

                Map<String, Object> parameters = new HashMap<>();
                parameters.put("Measurement Period",
                        new Interval(
                                new Date(periodStart.getYear(),
                                        periodStart.getMonthValue(),
                                        periodStart.getDayOfMonth()),
                                true,
                                new Date(periodEnd.getYear(),
                                        periodEnd.getMonthValue(),
                                        periodEnd.getDayOfMonth()),
                                true));
                execRequest.setParameters(parameters);

                try {
                    CqlExecutionResponse execResponse = cqlExecutionService.execute(execRequest);
                    aggregateResults(populationCounts, execResponse.getResults());
                    aggregateCustomExpressions(customExpressions, execResponse.getResults(), standardPopulations);

                    // Evaluate stratifiers for this patient
                    if (!stratifiers.isEmpty()) {
                        evaluateStratifiers(stratifiers, execResponse.getResults(),
                                populationCounts, stratificationData);
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed evaluation for patient {}", patientId, e);
                }
            }

            if (errorCount == patientsToEvaluate.size()) {
                if (measureEvaluationErrorCounter != null) measureEvaluationErrorCounter.increment();
                if (sample != null && measureEvaluationTimer != null) sample.stop(measureEvaluationTimer);
                return MeasureEvaluationResult.builder()
                        .measureId(request.getMeasureId())
                        .status("error")
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .build();
            }

            MeasureEvaluationResult result = buildAggregatedResult(request, populationCounts, periodStart, periodEnd,
                    patientsToEvaluate.size(), stratificationData, customExpressions);
            if (sample != null && measureEvaluationTimer != null) sample.stop(measureEvaluationTimer);

            // Auto-save report
            long durationMs = System.currentTimeMillis() - startTime;
            autoSaveReport(result, measureDefinitionId, request.getFhirServerUrl(), durationMs);

            return result;

        } catch (Exception e) {
            if (measureEvaluationErrorCounter != null) measureEvaluationErrorCounter.increment();
            if (sample != null && measureEvaluationTimer != null) sample.stop(measureEvaluationTimer);
            log.error("Measure evaluation failed", e);
            return MeasureEvaluationResult.builder()
                    .measureId(request.getMeasureId())
                    .status("error")
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .build();
        }
    }

    private List<StratifierDefinition> getStratifiers(MeasureDefinition definition) {
        if (definition == null || definition.getGroupDefinitions() == null) {
            return Collections.emptyList();
        }
        List<StratifierDefinition> all = new ArrayList<>();
        for (GroupDefinition group : definition.getGroupDefinitions()) {
            if (group.getStratifiers() != null) {
                all.addAll(group.getStratifiers());
            }
        }
        return all;
    }

    private void evaluateStratifiers(List<StratifierDefinition> stratifiers,
                                     Map<String, CqlExecutionResponse.ExpressionResult> results,
                                     Map<String, Integer> populationCounts,
                                     Map<String, Map<String, Map<String, Integer>>> stratificationData) {
        for (StratifierDefinition stratifier : stratifiers) {
            String stratId = stratifier.getStratifierId();
            String expression = stratifier.getCriteriaExpression();

            CqlExecutionResponse.ExpressionResult stratResult = results.get(expression);
            if (stratResult == null) continue;

            String strataValue = String.valueOf(stratResult.getValue());
            if (strataValue == null || "null".equals(strataValue)) continue;

            Map<String, Map<String, Integer>> strataMap = stratificationData
                    .computeIfAbsent(stratId, k -> new HashMap<>());
            Map<String, Integer> popCounts = strataMap
                    .computeIfAbsent(strataValue, k -> new HashMap<>());

            // Increment population counts for this stratum based on current patient
            for (Map.Entry<String, Integer> entry : populationCounts.entrySet()) {
                // We track per-patient: if the population count changed (patient contributed),
                // increment this stratum's count
                popCounts.merge(entry.getKey(), 0, (a, b) -> a + b);
            }

            // Check each population and add 1 if the patient is in it
            for (String popName : List.of("Initial Population", "Denominator", "Numerator",
                    "Denominator Exclusions", "Denominator Exceptions", "Numerator Exclusions")) {
                Integer count = extractPopulationCount(results, popName);
                if (count != null && count > 0) {
                    popCounts.merge(popName, 1, (a, b) -> a + b);
                }
            }
        }
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

    private void aggregateResults(Map<String, Integer> counts,
            Map<String, CqlExecutionResponse.ExpressionResult> results) {
        for (String key : counts.keySet()) {
            Integer count = extractPopulationCount(results, key);
            if (count != null && count > 0) {
                counts.put(key, counts.get(key) + count);
            }
        }
    }

    private void aggregateCustomExpressions(Map<String, Object> customExpressions,
            Map<String, CqlExecutionResponse.ExpressionResult> results,
            Set<String> standardPopulations) {
        if (results == null) return;
        for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : results.entrySet()) {
            String key = entry.getKey();
            if (standardPopulations.contains(key)) continue;
            Object value = entry.getValue().getValue();
            if (value instanceof Number) {
                int intVal = ((Number) value).intValue();
                int existing = customExpressions.containsKey(key)
                        ? ((Number) customExpressions.get(key)).intValue() : 0;
                customExpressions.put(key, existing + intVal);
            } else if (value instanceof Boolean) {
                int increment = (Boolean) value ? 1 : 0;
                int existing = customExpressions.containsKey(key)
                        ? ((Number) customExpressions.get(key)).intValue() : 0;
                customExpressions.put(key, existing + increment);
            } else if (value instanceof Collection<?> collection) {
                int existing = customExpressions.containsKey(key)
                        ? ((Number) customExpressions.get(key)).intValue() : 0;
                customExpressions.put(key, existing + collection.size());
            }
        }
    }

    private MeasureEvaluationResult buildAggregatedResult(
            MeasureEvaluationRequest request,
            Map<String, Integer> counts,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalPatients,
            Map<String, Map<String, Map<String, Integer>>> stratificationData,
            Map<String, Object> customExpressions) {

        Integer initialPopulation = counts.get("Initial Population");
        Integer denominator = counts.get("Denominator");
        Integer denominatorExclusions = counts.get("Denominator Exclusions");
        Integer denominatorExceptions = counts.get("Denominator Exceptions");
        Integer numerator = counts.get("Numerator");
        Integer numeratorExclusions = counts.get("Numerator Exclusions");

        List<PopulationResult> populations = new ArrayList<>();

        populations.add(PopulationResult.builder()
                .populationType("initial-population")
                .populationId("initial-population")
                .count(initialPopulation)
                .build());

        populations.add(PopulationResult.builder()
                .populationType("denominator")
                .populationId("denominator")
                .count(denominator)
                .build());

        if (denominatorExclusions > 0) {
            populations.add(PopulationResult.builder()
                    .populationType("denominator-exclusion")
                    .populationId("denominator-exclusion")
                    .count(denominatorExclusions)
                    .build());
        }

        if (denominatorExceptions > 0) {
            populations.add(PopulationResult.builder()
                    .populationType("denominator-exception")
                    .populationId("denominator-exception")
                    .count(denominatorExceptions)
                    .build());
        }

        populations.add(PopulationResult.builder()
                .populationType("numerator")
                .populationId("numerator")
                .count(numerator)
                .build());

        if (numeratorExclusions > 0) {
            populations.add(PopulationResult.builder()
                    .populationType("numerator-exclusion")
                    .populationId("numerator-exclusion")
                    .count(numeratorExclusions)
                    .build());
        }

        Double measureScore = calculateMeasureScore(denominator, denominatorExclusions, numerator);

        // Build stratifier results
        List<StratifierResult> stratifierResults = buildStratifierResults(stratificationData);

        GroupResult groupResult = GroupResult.builder()
                .groupId("group-1")
                .description("Primary measure group (Total Patients: " + totalPatients + ")")
                .populations(populations)
                .measureScore(measureScore)
                .measureScoreUnit("percentage")
                .stratifiers(stratifierResults.isEmpty() ? null : stratifierResults)
                .build();

        return MeasureEvaluationResult.builder()
                .measureId(request.getMeasureId())
                .measureName(request.getMeasureId())
                .status("complete")
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .reportType(request.getReportType())
                .groups(List.of(groupResult))
                .supplementalData(customExpressions.isEmpty() ? null : customExpressions)
                .build();
    }

    private List<StratifierResult> buildStratifierResults(
            Map<String, Map<String, Map<String, Integer>>> stratificationData) {
        List<StratifierResult> results = new ArrayList<>();

        for (Map.Entry<String, Map<String, Map<String, Integer>>> stratEntry : stratificationData.entrySet()) {
            String stratId = stratEntry.getKey();
            for (Map.Entry<String, Map<String, Integer>> strataEntry : stratEntry.getValue().entrySet()) {
                String strataValue = strataEntry.getKey();
                Map<String, Integer> popCounts = strataEntry.getValue();

                List<PopulationResult> stratPops = new ArrayList<>();
                for (Map.Entry<String, Integer> popEntry : popCounts.entrySet()) {
                    String popType = popEntry.getKey().toLowerCase().replace(" ", "-")
                            .replace("exclusions", "exclusion")
                            .replace("exceptions", "exception");
                    stratPops.add(PopulationResult.builder()
                            .populationType(popType)
                            .populationId(popType)
                            .count(popEntry.getValue())
                            .build());
                }

                Integer denom = popCounts.getOrDefault("Denominator", 0);
                Integer denomExcl = popCounts.getOrDefault("Denominator Exclusions", 0);
                Integer numer = popCounts.getOrDefault("Numerator", 0);
                Double stratScore = calculateMeasureScore(denom, denomExcl, numer);

                results.add(StratifierResult.builder()
                        .strataId(stratId)
                        .strataValue(strataValue)
                        .populations(stratPops)
                        .measureScore(stratScore)
                        .build());
            }
        }

        return results;
    }

    private Integer extractPopulationCount(
            Map<String, CqlExecutionResponse.ExpressionResult> results,
            String populationName) {

        CqlExecutionResponse.ExpressionResult result = results.get(populationName);
        if (result == null) {
            return null;
        }

        Object value = result.getValue();
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof Iterable<?> iterable) {
            int count = 0;
            var iterator = iterable.iterator();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
            return count;
        }

        return null;
    }

    private Double calculateMeasureScore(Integer denominator, Integer exclusions, Integer numerator) {
        if (denominator == null || denominator == 0) {
            return null;
        }

        int effectiveDenominator = denominator - (exclusions != null ? exclusions : 0);
        if (effectiveDenominator <= 0) {
            return null;
        }

        int effectiveNumerator = numerator != null ? numerator : 0;
        return (double) effectiveNumerator / effectiveDenominator * 100;
    }
}
