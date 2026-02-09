package com.cqlplatform.service.measure;

import org.springframework.stereotype.Component;

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
            case "proportion" -> calculateProportionScore(denominator, exclusions, numerator);
            case "ratio" -> calculateRatioScore(denominator, exclusions, numerator);
            case "cohort" -> null; // Cohort measures don't have a numeric score
            case "continuous-variable" -> null; // Requires observation values, not simple counts
            default -> calculateProportionScore(denominator, exclusions, numerator);
        };
    }

    private Double calculateRatioScore(Integer denominator, Integer exclusions, Integer numerator) {
        // Ratio scoring: numerator / denominator (without exclusion subtraction)
        if (denominator == null || denominator == 0) {
            return null;
        }
        int effectiveNumerator = numerator != null ? numerator : 0;
        return (double) effectiveNumerator / denominator * 100;
    }
}
