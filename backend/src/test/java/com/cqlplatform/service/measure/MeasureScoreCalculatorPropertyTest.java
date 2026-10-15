package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.testsupport.Fuzz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * PAT-265 — properties of the score calculator: the percentile (PAT-264) is bounded, monotone in p,
 * permutation-invariant and agrees with the median at p = 50; every aggregate method string, including
 * typos, is handled without an exception and known methods equal their textbook definition; the
 * proportion score is the rate or null.
 */
@DisplayName("PAT-265 — score calculator properties")
class MeasureScoreCalculatorPropertyTest {

    private static final Set<String> CANONICAL = Set.of("count", "sum", "average", "median", "minimum", "maximum", "percentile");

    private final MeasureScoreCalculator calculator = new MeasureScoreCalculator();

    record Sample(List<Double> values, double p) {
    }

    static List<Double> randomValues(Random r, int minSize, int maxSize) {
        int n = Fuzz.between(r, minSize, maxSize);
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            values.add(switch (Fuzz.between(r, 0, 3)) {
                case 0 -> (double) Fuzz.between(r, 0, 10);
                case 1 -> Fuzz.between(r, -1000, 1000) / 8.0;
                case 2 -> r.nextDouble() * 1e6 - 5e5;
                default -> values.isEmpty() ? 42.0 : values.get(r.nextInt(values.size()));   // a duplicate
            });
        }
        return values;
    }

    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    @Test
    @DisplayName("percentile is within [min, max], monotone in p, permutation-invariant, and the median at p = 50")
    void percentile_isBoundedMonotoneAndOrderFree() {
        Fuzz.forAll(20261030L, 500, r -> new Sample(randomValues(r, 1, 40), Fuzz.between(r, -30, 130)), (sample, r) -> {
            List<Double> values = sample.values();
            double min = Collections.min(values);
            double max = Collections.max(values);
            double at = MeasureScoreCalculator.percentile(values, sample.p());
            assertThat(at).isBetween(min, max);
            if (sample.p() <= 0) assertThat(at).isEqualTo(min);
            if (sample.p() >= 100) assertThat(at).isEqualTo(max);

            double higher = sample.p() + Fuzz.between(r, 0, 60);
            assertThat(MeasureScoreCalculator.percentile(values, higher)).as("monotone in p").isGreaterThanOrEqualTo(at - 1e-9);

            List<Double> shuffled = new ArrayList<>(values);
            Collections.shuffle(shuffled, r);
            assertThat(MeasureScoreCalculator.percentile(shuffled, sample.p())).as("order of the values does not matter").isEqualTo(at);

            assertThat(MeasureScoreCalculator.percentile(values, 50)).as("p50 is the median").isCloseTo(median(values), within(1e-9));
            assertThat(MeasureScoreCalculator.percentile(Collections.nCopies(values.size(), 7.5), sample.p())).as("all equal").isEqualTo(7.5);
        });
    }

    record MethodCase(List<Double> values, String method, Double p) {
    }

    @Test
    @DisplayName("continuous-variable scoring never throws, is null for unknown methods / empty values, and matches the definitions otherwise")
    void continuousVariableScore_matchesTheDefinitions() {
        List<String> methods = new ArrayList<>(List.of("Sum", "sum", "AVG", "avg", "mean", "Mean", "Average", "Median", "MIN", "min", "Minimum",
                "max", "Maximum", "Count", "COUNT", "percentile", "Percentile", "Minumum", "mode", "", " sum ", "p90", "average "));
        Fuzz.forAll(20261031L, 600, r -> new MethodCase(
                randomValues(r, 0, 25),
                Fuzz.chance(r, 0.1) ? null : Fuzz.chance(r, 0.1) ? randomText(r) : Fuzz.pick(r, methods),
                Fuzz.chance(r, 0.3) ? null : (double) Fuzz.between(r, -10, 110)), (c, r) -> {
            Double score = calculator.calculateContinuousVariableScore(c.values(), c.method(), c.p());
            String canonical = MeasureScoreCalculator.normalizeAggregateMethod(c.method());
            if (c.values().isEmpty() || canonical == null || ("percentile".equals(canonical) && c.p() == null)) {
                assertThat(score).as("score for " + c).isNull();
                return;
            }
            double expected = switch (canonical) {
                case "count" -> c.values().size();
                case "sum" -> c.values().stream().mapToDouble(Double::doubleValue).sum();
                case "average" -> c.values().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                case "median" -> median(c.values());
                case "minimum" -> Collections.min(c.values());
                case "maximum" -> Collections.max(c.values());
                case "percentile" -> MeasureScoreCalculator.percentile(c.values(), c.p());
                default -> throw new AssertionError("unexpected canonical method " + canonical);
            };
            assertThat(score).as(canonical + " of " + c.values()).isCloseTo(expected, within(1e-6));
            assertThat(calculator.calculateContinuousVariableScore(c.values(), c.method())).as("2-arg overload = percentile null")
                    .isEqualTo("percentile".equals(canonical) ? null : score);

            MeasureEvaluationResult.ObservationStatistics stats = calculator.computeObservationStats(c.values(), c.method(), c.p(), "d");
            assertThat(stats).isNotNull();
            assertThat(stats.getObservationCount()).isEqualTo(c.values().size());
            assertThat(stats.getAggregateMethod()).isEqualTo(canonical);
            assertThat(stats.getAggregateValue()).isCloseTo(expected, within(1e-6));
            assertThat(stats.getMinimum()).isEqualTo(Collections.min(c.values()));
        });
    }

    @Test
    @DisplayName("normalizeAggregateMethod is case-insensitive, idempotent and maps blanks to average")
    void normalizeAggregateMethod_isIdempotent() {
        Fuzz.forAll(20261032L, 300, r -> Fuzz.chance(r, 0.15) ? randomText(r)
                : Fuzz.pick(r, "sum", "Min", "MAX", "avg", "mean", "median", "count", "percentile", "minimum", "maximum", "average", "", "  ", null), (method, r) -> {
            String canonical = MeasureScoreCalculator.normalizeAggregateMethod(method);
            if (method == null || method.isBlank()) {
                assertThat(canonical).isEqualTo("average");
                return;
            }
            if (canonical != null) {
                assertThat(CANONICAL).contains(canonical);
                assertThat(MeasureScoreCalculator.normalizeAggregateMethod(canonical)).isEqualTo(canonical);
                assertThat(MeasureScoreCalculator.normalizeAggregateMethod(method.toUpperCase())).isEqualTo(canonical);
                assertThat(MeasureScoreCalculator.normalizeAggregateMethod("  " + method + "\t")).isEqualTo(canonical);
            } else {
                assertThat(MeasureScoreCalculator.normalizeAggregateMethod(method.toLowerCase())).isNull();
            }
        });
    }

    record Proportion(Integer denominator, Integer exclusions, Integer numerator) {
    }

    @Test
    @DisplayName("the proportion score is numerator / (denominator − exclusions) × 100, or null when there is nothing to divide by")
    void proportionScore_isTheRateOrNull() {
        Fuzz.forAll(20261033L, 600, r -> new Proportion(
                Fuzz.chance(r, 0.1) ? null : Fuzz.between(r, -3, 60),
                Fuzz.chance(r, 0.3) ? null : Fuzz.between(r, -2, 15),
                Fuzz.chance(r, 0.2) ? null : Fuzz.between(r, -2, 70)), (p, r) -> {
            Double score = calculator.calculateProportionScore(p.denominator(), p.exclusions(), p.numerator());
            Double viaType = calculator.calculateScore("proportion", p.denominator(), p.exclusions(), p.numerator());
            assertThat(viaType).isEqualTo(score);
            assertThat(calculator.calculateScore(null, p.denominator(), p.exclusions(), p.numerator())).isEqualTo(score);
            if (p.denominator() == null || p.denominator() == 0) {
                assertThat(score).isNull();
                return;
            }
            int effective = p.denominator() - (p.exclusions() == null ? 0 : p.exclusions());
            if (effective <= 0) {
                assertThat(score).isNull();
                return;
            }
            int numerator = p.numerator() == null ? 0 : p.numerator();
            assertThat(score).isCloseTo((double) numerator / effective * 100, within(1e-9));
            if (numerator >= 0 && numerator <= effective) assertThat(score).isBetween(0.0, 100.0);
            assertThat(calculator.calculateScore("cohort", p.denominator(), p.exclusions(), p.numerator())).isNull();
            assertThat(calculator.calculateScore("continuous-variable", p.denominator(), p.exclusions(), p.numerator())).isNull();
        });
    }

    private static String randomText(Random r) {
        int n = Fuzz.between(r, 1, 12);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append((char) Fuzz.between(r, 'A', 'z'));
        return sb.toString();
    }
}
