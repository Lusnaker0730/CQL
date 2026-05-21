package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.ScoringTypeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeasureScoreCalculatorTest {

    private final MeasureScoreCalculator calc = new MeasureScoreCalculator();

    // ── calculateCohortScore ────────────────────────────────────────────────
    // Per FHIR MeasureReport spec, cohort score = count(Initial Population). Previously
    // this was hardcoded to null (comment claimed cohort "has no numeric score"), which
    // made cohort measures show blank in dashboards / reports. Locked here so the
    // regression can't come back.

    @Test
    void cohortScore_shouldReturnIpCountAsDouble() {
        assertThat(calc.calculateCohortScore(42)).isEqualTo(42.0);
    }

    @Test
    void cohortScore_zeroIp_shouldReturnZero_notNull() {
        // Zero is a valid cohort result (no patients matched) — distinct from "couldn't
        // compute". Null score at the API layer would confuse downstream consumers.
        assertThat(calc.calculateCohortScore(0)).isEqualTo(0.0);
    }

    @Test
    void cohortScore_nullIp_shouldReturnNull() {
        assertThat(calc.calculateCohortScore(null)).isNull();
    }

    @Test
    void calculateScore_cohortDispatchesToNullFromThisEntryPoint() {
        // calculateScore(scoringType, denom, excl, numer) doesn't receive IP, so it can't
        // compute cohort — it returns null, and callers of the cohort path must dispatch
        // directly to calculateCohortScore(ip). This test locks that contract so a future
        // refactor doesn't silently re-introduce the "cohort score = 0.0 because denom=0"
        // mis-route.
        assertThat(calc.calculateScore(ScoringTypeConstants.COHORT, 100, 0, 50)).isNull();
    }

    // ── proportion / ratio (baseline regression locks) ──────────────────────

    @Test
    void proportionScore_standard() {
        // 3 / 5 = 0.6 → 60.0 as percentage
        assertThat(calc.calculateProportionScore(5, 0, 3)).isEqualTo(60.0);
    }

    @Test
    void proportionScore_zeroDenom_null() {
        assertThat(calc.calculateProportionScore(0, 0, 0)).isNull();
    }

    @Test
    void ratioScore_viaDispatcher() {
        // 2 / 3 = 66.666... (backend emits as percentage)
        assertThat(calc.calculateScore(ScoringTypeConstants.RATIO, 3, 0, 2))
                .isCloseTo(66.666, org.assertj.core.api.Assertions.within(0.01));
    }

    // ── normalizeAggregateMethod + CV score alias handling ──────────────────
    // Before this fix, calculateContinuousVariableScore silently fell through to
    // Average for any unknown input including abbreviations like "Min" / "Max".
    // A measure author writing aggregateMethod="Min" saw a plausible-looking but
    // wrong number (the average). The fix normalizes aliases and returns null
    // for unknowns so the author sees a visible gap instead of quiet corruption.

    @ParameterizedTest
    @CsvSource({
            "Count, count", "count, count", "COUNT, count",
            "Sum, sum", "sum, sum",
            "Average, average", "average, average", "Avg, average", "avg, average",
            "Mean, average", "mean, average",
            "Median, median", "median, median",
            "Minimum, minimum", "minimum, minimum", "Min, minimum", "min, minimum", "MIN, minimum",
            "Maximum, maximum", "maximum, maximum", "Max, maximum", "max, maximum", "MAX, maximum"
    })
    void normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String input, String expected) {
        assertThat(MeasureScoreCalculator.normalizeAggregateMethod(input)).isEqualTo(expected);
    }

    @Test
    void normalizeAggregateMethod_nullOrBlank_defaultsToAverage() {
        // Preserves prior semantic: "no aggregate specified" → Average.
        assertThat(MeasureScoreCalculator.normalizeAggregateMethod(null)).isEqualTo("average");
        assertThat(MeasureScoreCalculator.normalizeAggregateMethod("")).isEqualTo("average");
        assertThat(MeasureScoreCalculator.normalizeAggregateMethod("   ")).isEqualTo("average");
    }

    @Test
    void normalizeAggregateMethod_unknown_returnsNull_notSilentFallthrough() {
        // The bug we're fixing: typos like "Minumum" used to return Average silently.
        // Now they return null so the caller can surface the issue.
        assertThat(MeasureScoreCalculator.normalizeAggregateMethod("Minumum")).isNull();
        assertThat(MeasureScoreCalculator.normalizeAggregateMethod("garbage")).isNull();
        assertThat(MeasureScoreCalculator.normalizeAggregateMethod("avergae")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "Min, 1.0",      // min{1,2,3,4,5} = 1
            "min, 1.0",
            "Minimum, 1.0",
            "Max, 5.0",      // max = 5
            "max, 5.0",
            "Maximum, 5.0",
            "Avg, 3.0",      // avg = 3
            "Mean, 3.0",
            "Average, 3.0",
            "Sum, 15.0",     // sum = 15
            "Median, 3.0",   // median of {1,2,3,4,5} = 3
            "Count, 5.0"     // 5 values
    })
    void cvScore_aliasesAndCanonicals_computeCorrectly(String method, double expected) {
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        Double actual = calc.calculateContinuousVariableScore(values, method);
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void cvScore_unknownMethod_shouldReturnNull_notFallThroughToAverage() {
        // The critical behavior change: unknown method = null (visible), not
        // average (invisible). Prior code returned 3.0 here for typo "Minumum".
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        assertThat(calc.calculateContinuousVariableScore(values, "Minumum")).isNull();
        assertThat(calc.calculateContinuousVariableScore(values, "wrong")).isNull();
    }

    @Test
    void cvScore_nullOrBlankMethod_stillAverages() {
        // Keep compatibility: when author omits aggregateMethod entirely, fall back to
        // Average (same as pre-fix). Only TYPOS should be rejected — missing config is OK.
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        assertThat(calc.calculateContinuousVariableScore(values, null)).isEqualTo(3.0);
        assertThat(calc.calculateContinuousVariableScore(values, "")).isEqualTo(3.0);
    }

    @Test
    void computeObservationStats_shouldNormalizeDisplayedMethod() {
        // The response's .aggregateMethod should always be a canonical value, so
        // downstream consumers (dashboards, reports, FHIR MeasureReport) see a stable
        // vocabulary regardless of what the author typed.
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        var stats = calc.computeObservationStats(values, "Min", "days");
        assertThat(stats.getAggregateMethod()).isEqualTo("minimum");
        assertThat(stats.getAggregateValue()).isEqualTo(1.0);
    }
}
