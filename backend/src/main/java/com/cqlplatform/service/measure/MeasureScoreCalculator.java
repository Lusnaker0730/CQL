package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.MeasureEvaluationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calculates measure scores based on scoring type.
 * Pure function — fully testable with no external dependencies.
 */
@Component
public class MeasureScoreCalculator {

    /**
     * Calculates the proportion score: (numerator / (denominator - exclusions)) * 100.
     *
     * @param denominator  total denominator count
     * @param exclusions   denominator exclusion count (may be null)
     * @param numerator    numerator count (may be null)
     * @return the percentage score, or null if denominator is zero/invalid
     */
    public Double calculateProportionScore(Integer denominator, Integer exclusions, Integer numerator) {
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

    /**
     * Calculates measure score based on scoring type.
     * Currently supports proportion scoring; extensible for ratio, continuous-variable, cohort.
     *
     * @param scoringType  the measure scoring type
     * @param denominator  denominator count
     * @param exclusions   denominator exclusion count
     * @param numerator    numerator count
     * @return the calculated score, or null if not computable
     */
    public Double calculateScore(String scoringType, Integer denominator,
                                 Integer exclusions, Integer numerator) {
        if (scoringType == null) {
            return calculateProportionScore(denominator, exclusions, numerator);
        }
        return switch (scoringType.toLowerCase()) {
            case com.cqlplatform.model.measure.ScoringTypeConstants.PROPORTION -> calculateProportionScore(denominator, exclusions, numerator);
            case com.cqlplatform.model.measure.ScoringTypeConstants.RATIO -> calculateRatioScore(denominator, exclusions, numerator);
            // Cohort doesn't fit this signature (needs IP count, not denom). Callers
            // evaluating a cohort measure must dispatch directly to calculateCohortScore.
            case com.cqlplatform.model.measure.ScoringTypeConstants.COHORT -> null;
            case com.cqlplatform.model.measure.ScoringTypeConstants.CONTINUOUS_VARIABLE -> null; // Requires observation values, not simple counts
            default -> calculateProportionScore(denominator, exclusions, numerator);
        };
    }

    /**
     * Cohort score per FHIR MeasureReport spec: the count of the Initial Population.
     * Upcast to Double because MeasureReport.group.measureScore is a Quantity with a
     * decimal value. Null IP → null score; zero IP → 0.0 (zero is a valid cohort score —
     * it means "no patients matched", distinct from "couldn't compute").
     */
    public Double calculateCohortScore(Integer initialPopulationCount) {
        if (initialPopulationCount == null) return null;
        return (double) initialPopulationCount;
    }

    /**
     * Calculates continuous-variable score by aggregating observation values.
     */
    public Double calculateContinuousVariableScore(List<Double> values, String aggregateMethod) {
        if (values == null || values.isEmpty()) return null;
        return switch (aggregateMethod != null ? aggregateMethod.toLowerCase() : "average") {
            case "sum" -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "median" -> calculateMedian(values);
            case "minimum" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "maximum" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case "count" -> (double) values.size();
            default -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        };
    }

    /**
     * Computes full observation statistics for continuous-variable measures.
     */
    public MeasureEvaluationResult.ObservationStatistics computeObservationStats(
            List<Double> values, String aggregateMethod, String unit) {
        if (values == null || values.isEmpty()) return null;
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return MeasureEvaluationResult.ObservationStatistics.builder()
                .aggregateMethod(aggregateMethod != null ? aggregateMethod.toLowerCase() : "average")
                .aggregateValue(calculateContinuousVariableScore(values, aggregateMethod))
                .observationCount(values.size())
                .minimum(values.stream().mapToDouble(Double::doubleValue).min().orElse(0))
                .maximum(values.stream().mapToDouble(Double::doubleValue).max().orElse(0))
                .average(avg)
                .median(calculateMedian(values))
                .unit(unit)
                .build();
    }

    private Double calculateMedian(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
        return sorted.get(size / 2);
    }

    private Double calculateRatioScore(Integer denominator, Integer exclusions, Integer numerator) {
        // Ratio scoring: numerator / denominator (without exclusion subtraction)
        if (denominator == null || denominator == 0) {
            return null;
        }
        int effectiveNumerator = numerator != null ? numerator : 0;
        return (double) effectiveNumerator / denominator * 100;
    }

    /**
     * Calculates rate score: (events / observation sum) × rate multiplier.
     * Used for rates like "per 1,000 person-years".
     */
    public Double calculateRateScore(Integer numeratorCount, Double observationSum, double rateMultiplier) {
        if (numeratorCount == null || observationSum == null || observationSum == 0) {
            return null;
        }
        return (double) numeratorCount / observationSum * rateMultiplier;
    }
}
