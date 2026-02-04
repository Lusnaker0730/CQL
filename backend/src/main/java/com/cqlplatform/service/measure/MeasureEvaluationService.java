package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.*;
import com.cqlplatform.service.cql.CqlExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureEvaluationService {

    private final CqlExecutionService cqlExecutionService;

    @Value("${measure.reporting.default-period-start:2024-01-01}")
    private String defaultPeriodStart;

    @Value("${measure.reporting.default-period-end:2024-12-31}")
    private String defaultPeriodEnd;

    public MeasureEvaluationResult evaluateMeasure(MeasureEvaluationRequest request) {
        log.info("Evaluating measure: {} for patient: {}",
                request.getMeasureId(), request.getPatientId());

        LocalDate periodStart = request.getPeriodStart() != null ?
                request.getPeriodStart() : LocalDate.parse(defaultPeriodStart);
        LocalDate periodEnd = request.getPeriodEnd() != null ?
                request.getPeriodEnd() : LocalDate.parse(defaultPeriodEnd);

        try {
            CqlExecutionRequest execRequest = new CqlExecutionRequest();
            execRequest.setCql(request.getMeasureCql());
            execRequest.setPatientId(request.getPatientId());
            execRequest.setFhirServerUrl(request.getFhirServerUrl());

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("Measurement Period",
                    new org.opencds.cqf.cql.engine.runtime.Interval(
                            new org.opencds.cqf.cql.engine.runtime.Date(
                                    periodStart.getYear(), periodStart.getMonthValue(), periodStart.getDayOfMonth()),
                            true,
                            new org.opencds.cqf.cql.engine.runtime.Date(
                                    periodEnd.getYear(), periodEnd.getMonthValue(), periodEnd.getDayOfMonth()),
                            true
                    ));
            execRequest.setParameters(parameters);

            CqlExecutionResponse execResponse = cqlExecutionService.execute(execRequest);

            return buildMeasureResult(request, execResponse, periodStart, periodEnd);

        } catch (Exception e) {
            log.error("Measure evaluation failed", e);
            return MeasureEvaluationResult.builder()
                    .measureId(request.getMeasureId())
                    .status("error")
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .build();
        }
    }

    private MeasureEvaluationResult buildMeasureResult(
            MeasureEvaluationRequest request,
            CqlExecutionResponse execResponse,
            LocalDate periodStart,
            LocalDate periodEnd) {

        Map<String, CqlExecutionResponse.ExpressionResult> results = execResponse.getResults();

        Integer initialPopulation = extractPopulationCount(results, "Initial Population");
        Integer denominator = extractPopulationCount(results, "Denominator");
        Integer denominatorExclusions = extractPopulationCount(results, "Denominator Exclusions");
        Integer denominatorExceptions = extractPopulationCount(results, "Denominator Exceptions");
        Integer numerator = extractPopulationCount(results, "Numerator");
        Integer numeratorExclusions = extractPopulationCount(results, "Numerator Exclusions");

        List<PopulationResult> populations = new ArrayList<>();

        populations.add(PopulationResult.builder()
                .populationType("initial-population")
                .populationId("initial-population")
                .count(initialPopulation)
                .subjectIds(initialPopulation != null && initialPopulation > 0 ?
                        List.of(request.getPatientId()) : List.of())
                .build());

        populations.add(PopulationResult.builder()
                .populationType("denominator")
                .populationId("denominator")
                .count(denominator)
                .subjectIds(denominator != null && denominator > 0 ?
                        List.of(request.getPatientId()) : List.of())
                .build());

        if (denominatorExclusions != null) {
            populations.add(PopulationResult.builder()
                    .populationType("denominator-exclusion")
                    .populationId("denominator-exclusion")
                    .count(denominatorExclusions)
                    .build());
        }

        if (denominatorExceptions != null) {
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
                .subjectIds(numerator != null && numerator > 0 ?
                        List.of(request.getPatientId()) : List.of())
                .build());

        if (numeratorExclusions != null) {
            populations.add(PopulationResult.builder()
                    .populationType("numerator-exclusion")
                    .populationId("numerator-exclusion")
                    .count(numeratorExclusions)
                    .build());
        }

        Double measureScore = calculateMeasureScore(denominator, denominatorExclusions, numerator);

        GroupResult groupResult = GroupResult.builder()
                .groupId("group-1")
                .description("Primary measure group")
                .populations(populations)
                .measureScore(measureScore)
                .measureScoreUnit("percentage")
                .build();

        Map<String, Object> supplementalData = new HashMap<>();
        for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : results.entrySet()) {
            String name = entry.getKey();
            if (!isPopulationExpression(name)) {
                supplementalData.put(name, entry.getValue().getDisplayValue());
            }
        }

        return MeasureEvaluationResult.builder()
                .measureId(request.getMeasureId())
                .measureName(request.getMeasureId())
                .status("complete")
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .reportType(request.getReportType())
                .groups(List.of(groupResult))
                .supplementalData(supplementalData.isEmpty() ? null : supplementalData)
                .build();
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

    private boolean isPopulationExpression(String name) {
        return name.equals("Initial Population") ||
                name.equals("Denominator") ||
                name.equals("Denominator Exclusions") ||
                name.equals("Denominator Exceptions") ||
                name.equals("Numerator") ||
                name.equals("Numerator Exclusions") ||
                name.equals("Measure Population") ||
                name.equals("Measure Population Exclusions") ||
                name.equals("Measure Observation");
    }
}
