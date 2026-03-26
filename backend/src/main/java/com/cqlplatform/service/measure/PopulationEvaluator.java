package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Evaluates and aggregates population counts from CQL execution results.
 * Pure logic — no FHIR or database dependencies.
 */
@Component
@Slf4j
public class PopulationEvaluator {

    /** Standard population names recognized by eCQM evaluation. */
    public static final List<String> STANDARD_POPULATIONS = List.of(
            "Initial Population",
            "Denominator",
            "Denominator Exclusions",
            "Denominator Exceptions",
            "Numerator",
            "Numerator Exclusions"
    );

    /** Continuous-variable population names. */
    public static final List<String> CV_POPULATIONS = List.of(
            "Initial Population",
            "Measure Population",
            "Measure Population Exclusion"
    );

    private static final String OBSERVATION_VALUES_EXPR = "Measure Observation Values";
    private static final String OBSERVATION_VALUE_EXPR = "Measure Observation Value";

    /**
     * Creates an initialized population count map with all standard populations set to 0.
     */
    public Map<String, Integer> initializePopulationCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String pop : STANDARD_POPULATIONS) {
            counts.put(pop, 0);
        }
        return counts;
    }

    /**
     * Aggregates a single patient's CQL results into the running population counts.
     * Enforces HL7 proportion measure population hierarchy:
     * <ul>
     *   <li>Denominator is only counted if Initial Population is true</li>
     *   <li>Denominator Exclusions only if Denominator is true</li>
     *   <li>Numerator is only counted if Denominator is true AND not excluded</li>
     *   <li>Numerator Exclusions only if Numerator is true</li>
     *   <li>Denominator Exceptions only if Denominator is true but Numerator is false</li>
     * </ul>
     *
     * @param counts  running population counts (mutated in place)
     * @param results CQL expression results for one patient
     */
    public void aggregatePatientResults(Map<String, Integer> counts,
                                        Map<String, CqlExecutionResponse.ExpressionResult> results) {
        // Evaluate each population for this patient
        boolean inInitPop = isPopulationTrue(results, "Initial Population");
        boolean inDenom = inInitPop && isPopulationTrue(results, "Denominator");
        boolean denomExcluded = inDenom && isPopulationTrue(results, "Denominator Exclusions");
        boolean effectiveDenom = inDenom && !denomExcluded;
        boolean inNumer = effectiveDenom && isPopulationTrue(results, "Numerator");
        boolean numerExcluded = inNumer && isPopulationTrue(results, "Numerator Exclusions");
        boolean effectiveNumer = inNumer && !numerExcluded;
        boolean denomException = effectiveDenom && !effectiveNumer
                && isPopulationTrue(results, "Denominator Exceptions");

        if (inInitPop) increment(counts, "Initial Population");
        if (inDenom) increment(counts, "Denominator");
        if (denomExcluded) increment(counts, "Denominator Exclusions");
        if (effectiveNumer) increment(counts, "Numerator");
        if (numerExcluded) increment(counts, "Numerator Exclusions");
        if (denomException) increment(counts, "Denominator Exceptions");
    }

    private boolean isPopulationTrue(Map<String, CqlExecutionResponse.ExpressionResult> results,
                                     String populationName) {
        Integer count = extractPopulationCount(results, populationName);
        return count != null && count > 0;
    }

    private void increment(Map<String, Integer> counts, String key) {
        counts.computeIfPresent(key, (k, v) -> v + 1);
    }

    /**
     * Creates an initialized population count map for continuous-variable measures.
     */
    public Map<String, Integer> initializeCvPopulationCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String pop : CV_POPULATIONS) {
            counts.put(pop, 0);
        }
        return counts;
    }

    /**
     * Aggregates a single patient's CQL results for continuous-variable measures.
     * Collects observation values into the shared accumulator list.
     */
    public void aggregateCvPatientResults(Map<String, Integer> counts,
                                           Map<String, CqlExecutionResponse.ExpressionResult> results,
                                           List<Double> observationValues) {
        boolean inInitPop = isPopulationTrue(results, "Initial Population");
        boolean inMeasurePop = inInitPop && isPopulationTrue(results, "Measure Population");
        boolean excluded = inMeasurePop && isPopulationTrue(results, "Measure Population Exclusion");
        boolean effectiveMp = inMeasurePop && !excluded;

        if (inInitPop) increment(counts, "Initial Population");
        if (inMeasurePop) increment(counts, "Measure Population");
        if (excluded) increment(counts, "Measure Population Exclusion");

        if (effectiveMp) {
            // Collect observation values — try episode-based list first, then patient-based scalar
            List<Double> values = extractObservationValues(results, OBSERVATION_VALUES_EXPR);
            if (values.isEmpty()) {
                values = extractObservationValues(results, OBSERVATION_VALUE_EXPR);
            }
            observationValues.addAll(values);
        }
    }

    /**
     * Extracts numeric observation values from CQL expression results.
     * Handles single Number, Iterable of Numbers, and nested Quantity types.
     */
    public List<Double> extractObservationValues(Map<String, CqlExecutionResponse.ExpressionResult> results,
                                                  String expressionName) {
        CqlExecutionResponse.ExpressionResult result = results.get(expressionName);
        if (result == null || result.getValue() == null) return List.of();

        Object value = result.getValue();
        if (value instanceof Number num) {
            return List.of(num.doubleValue());
        } else if (value instanceof Iterable<?> iterable) {
            List<Double> values = new ArrayList<>();
            for (Object item : iterable) {
                if (item instanceof Number num) {
                    values.add(num.doubleValue());
                }
            }
            return values;
        }
        return List.of();
    }

    /**
     * Aggregates custom (non-standard) expressions from CQL results.
     * Accumulates numeric values, boolean true counts, and collection sizes.
     *
     * @param customExpressions running custom expression aggregation (mutated in place)
     * @param results           CQL expression results for one patient
     * @param standardNames     set of standard population names to skip
     */
    public void aggregateCustomExpressions(Map<String, Object> customExpressions,
                                           Map<String, CqlExecutionResponse.ExpressionResult> results,
                                           Set<String> standardNames) {
        if (results == null) return;
        for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : results.entrySet()) {
            String key = entry.getKey();
            if (standardNames.contains(key)) continue;

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

    /**
     * Extracts a population count from a single patient's CQL result.
     * Handles Boolean (true=1), Number, and Iterable result types.
     *
     * @param results        CQL expression results
     * @param populationName the population expression name
     * @return the count, or null if the expression is not present
     */
    public Integer extractPopulationCount(Map<String, CqlExecutionResponse.ExpressionResult> results,
                                          String populationName) {
        CqlExecutionResponse.ExpressionResult result = results.get(populationName);
        if (result == null) return null;

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
}
