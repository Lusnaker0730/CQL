package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.ScoringTypeConstants;
import org.junit.jupiter.api.Test;

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
}
