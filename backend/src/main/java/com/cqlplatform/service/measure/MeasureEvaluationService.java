package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.*;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.fhir.FhirDataProviderService;
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

    @Value("${measure.reporting.default-period-start:2024-01-01}")
    private String defaultPeriodStart;

    @Value("${measure.reporting.default-period-end:2024-12-31}")
    private String defaultPeriodEnd;

    public MeasureEvaluationResult evaluateMeasure(MeasureEvaluationRequest request) {
        log.info("Evaluating measure: {} for patient: {}",
                request.getMeasureId(), request.getPatientId());

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

            for (String patientId : patientsToEvaluate) {
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
                } catch (Exception e) {
                    log.error("Failed evaluation for patient {}", patientId, e);
                }
            }

            return buildAggregatedResult(request, populationCounts, periodStart, periodEnd,
                    patientsToEvaluate.size());

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

    private void aggregateResults(Map<String, Integer> counts,
            Map<String, CqlExecutionResponse.ExpressionResult> results) {
        for (String key : counts.keySet()) {
            Integer count = extractPopulationCount(results, key);
            if (count != null && count > 0) {
                counts.put(key, counts.get(key) + count);
            }
        }
    }

    private MeasureEvaluationResult buildAggregatedResult(
            MeasureEvaluationRequest request,
            Map<String, Integer> counts,
            LocalDate periodStart,
            LocalDate periodEnd,
            int totalPatients) {

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

        GroupResult groupResult = GroupResult.builder()
                .groupId("group-1")
                .description("Primary measure group (Total Patients: " + totalPatients + ")")
                .populations(populations)
                .measureScore(measureScore)
                .measureScoreUnit("percentage")
                .build();

        return MeasureEvaluationResult.builder()
                .measureId(request.getMeasureId())
                .measureName(request.getMeasureId())
                .status("complete")
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .reportType(request.getReportType())
                .groups(List.of(groupResult))
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
}
