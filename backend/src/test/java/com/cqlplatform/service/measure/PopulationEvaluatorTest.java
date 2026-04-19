package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lock the FHIR-spec distinction between proportion and ratio aggregation:
 * proportion gates Numer by Denom (Numer ⊆ Denom); ratio gates Numer by IP only
 * (Numer independent of Denom). Before these tests the evaluator used proportion
 * semantics for both — so ratio measures where Numer was disjoint from Denom got
 * Numer=0 regardless of the actual data.
 */
class PopulationEvaluatorTest {

    private final PopulationEvaluator evaluator = new PopulationEvaluator();

    /** Builds a results map with each population's truth value preloaded. */
    private Map<String, CqlExecutionResponse.ExpressionResult> results(boolean ip, boolean denom, boolean numer) {
        return resultsWithExclusions(ip, denom, numer, false, false);
    }

    private Map<String, CqlExecutionResponse.ExpressionResult> resultsWithExclusions(
            boolean ip, boolean denom, boolean numer, boolean denomExcl, boolean numerExcl) {
        Map<String, CqlExecutionResponse.ExpressionResult> map = new LinkedHashMap<>();
        map.put("Initial Population", boolResult(ip));
        map.put("Denominator", boolResult(denom));
        map.put("Numerator", boolResult(numer));
        map.put("Denominator Exclusions", boolResult(denomExcl));
        map.put("Numerator Exclusions", boolResult(numerExcl));
        return map;
    }

    private CqlExecutionResponse.ExpressionResult boolResult(boolean value) {
        return CqlExecutionResponse.ExpressionResult.builder()
                .value(value)
                .valueType("Boolean")
                .build();
    }

    private Map<String, Integer> freshCounts() {
        Map<String, Integer> c = new HashMap<>();
        c.put("Initial Population", 0);
        c.put("Denominator", 0);
        c.put("Denominator Exclusions", 0);
        c.put("Numerator", 0);
        c.put("Numerator Exclusions", 0);
        c.put("Denominator Exceptions", 0);
        return c;
    }

    // ── aggregatePatientResults (proportion semantics — regression lock) ────

    @Test
    void proportion_numerGatedByDenom_patientNotInDenomDoesNotCountInNumer() {
        // Patient satisfies Numer expression but NOT Denom — proportion must not count
        // Numer. This is the foundational Numer ⊆ Denom invariant.
        Map<String, Integer> counts = freshCounts();
        evaluator.aggregatePatientResults(counts, results(true, false, true));
        assertThat(counts.get("Initial Population")).isEqualTo(1);
        assertThat(counts.get("Denominator")).isEqualTo(0);
        assertThat(counts.get("Numerator")).isEqualTo(0); // gated by Denom=false
    }

    @Test
    void proportion_patientInDenomAndNumer_countsBoth() {
        Map<String, Integer> counts = freshCounts();
        evaluator.aggregatePatientResults(counts, results(true, true, true));
        assertThat(counts.get("Initial Population")).isEqualTo(1);
        assertThat(counts.get("Denominator")).isEqualTo(1);
        assertThat(counts.get("Numerator")).isEqualTo(1);
    }

    // ── aggregateRatioPatientResults (ratio semantics — the fix) ────────────
    //
    // Per FHIR MeasureReport R4 spec, ratio's Numer is INDEPENDENT of Denom.
    // Both are filtered only by Initial Population. This is what makes ratio
    // useful for rate-style measures (Numer can exceed Denom).

    @Test
    void ratio_numerIndependent_patientNotInDenomStillCountsInNumer() {
        // Same patient shape as the proportion "not counted" case above — for ratio,
        // Numer MUST count because it's independent of Denom. This is the single
        // behavior difference that defines ratio vs proportion.
        Map<String, Integer> counts = freshCounts();
        evaluator.aggregateRatioPatientResults(counts, results(true, false, true));
        assertThat(counts.get("Initial Population")).isEqualTo(1);
        assertThat(counts.get("Denominator")).isEqualTo(0);
        assertThat(counts.get("Numerator")).isEqualTo(1); // NOT gated by Denom
    }

    @Test
    void ratio_patientInDenomNotNumer_countsDenomOnly() {
        Map<String, Integer> counts = freshCounts();
        evaluator.aggregateRatioPatientResults(counts, results(true, true, false));
        assertThat(counts.get("Denominator")).isEqualTo(1);
        assertThat(counts.get("Numerator")).isEqualTo(0);
    }

    @Test
    void ratio_patientNotInIp_nothingCounts() {
        // IP is still the universe for ratio — Denom/Numer both gated by IP.
        Map<String, Integer> counts = freshCounts();
        evaluator.aggregateRatioPatientResults(counts, results(false, true, true));
        assertThat(counts.get("Initial Population")).isEqualTo(0);
        assertThat(counts.get("Denominator")).isEqualTo(0);
        assertThat(counts.get("Numerator")).isEqualTo(0);
    }

    @Test
    void ratio_numerExclusion_reducesNumer_notDenom() {
        // Numer excluded → reduces Numer count (exclusion applies). Denom untouched.
        Map<String, Integer> counts = freshCounts();
        evaluator.aggregateRatioPatientResults(counts,
                resultsWithExclusions(true, true, true, false, true));
        assertThat(counts.get("Denominator")).isEqualTo(1);
        assertThat(counts.get("Numerator")).isEqualTo(0); // excluded
        assertThat(counts.get("Numerator Exclusions")).isEqualTo(1);
    }

    @Test
    void ratio_denomExclusion_reducesDenom_notNumer() {
        // Denom excluded → reduces Denom. Numer independent, so Numer still counts.
        Map<String, Integer> counts = freshCounts();
        evaluator.aggregateRatioPatientResults(counts,
                resultsWithExclusions(true, true, true, true, false));
        assertThat(counts.get("Denominator")).isEqualTo(1); // still counted (the count)
        assertThat(counts.get("Denominator Exclusions")).isEqualTo(1);
        assertThat(counts.get("Numerator")).isEqualTo(1); // NOT affected by denom excl
    }

    // ── extractObservationValues (CV Count on boolean observations) ─────────
    //
    // Before this fix, boolean Measure Observation values (emitted by the CQL
    // generator when the observation criteria tree is a boolean-returning element
    // like AgeRange) were silently dropped — extractObservationValues only accepted
    // Number / Iterable-of-Number. That made CV measures with boolean criteria
    // return measureScore=null regardless of data. Now TRUE maps to 1.0 so Count
    // aggregate correctly counts matching patients.

    @Test
    void extractObservationValues_booleanTrue_shouldReturnOne() {
        Map<String, CqlExecutionResponse.ExpressionResult> input = Map.of(
                "Measure Observation Value", boolResult(true));
        assertThat(evaluator.extractObservationValues(input, "Measure Observation Value"))
                .containsExactly(1.0);
    }

    @Test
    void extractObservationValues_booleanFalse_shouldReturnEmpty() {
        // FALSE = observation not observed for this patient; don't contribute to list.
        Map<String, CqlExecutionResponse.ExpressionResult> input = Map.of(
                "Measure Observation Value", boolResult(false));
        assertThat(evaluator.extractObservationValues(input, "Measure Observation Value"))
                .isEmpty();
    }

    @Test
    void extractObservationValues_numericValue_unchangedByFix() {
        // Regression lock: numeric observations (e.g. HbA1c value) still work.
        CqlExecutionResponse.ExpressionResult numResult = CqlExecutionResponse.ExpressionResult.builder()
                .value(7.5)
                .valueType("Decimal")
                .build();
        Map<String, CqlExecutionResponse.ExpressionResult> input = Map.of("Measure Observation Value", numResult);
        assertThat(evaluator.extractObservationValues(input, "Measure Observation Value"))
                .containsExactly(7.5);
    }

    @Test
    void extractObservationValues_nullValue_shouldReturnEmpty() {
        CqlExecutionResponse.ExpressionResult nullResult = CqlExecutionResponse.ExpressionResult.builder()
                .value(null)
                .build();
        Map<String, CqlExecutionResponse.ExpressionResult> input = Map.of("Measure Observation Value", nullResult);
        assertThat(evaluator.extractObservationValues(input, "Measure Observation Value")).isEmpty();
    }

    @Test
    void ratio_noDenomExceptionsConcept() {
        // Ratio doesn't have Denominator Exceptions (proportion-only per FHIR spec).
        // Even if the map had one, ratio aggregation ignores it.
        Map<String, Integer> counts = freshCounts();
        Map<String, CqlExecutionResponse.ExpressionResult> input =
                resultsWithExclusions(true, true, false, false, false);
        input.put("Denominator Exceptions", boolResult(true));
        evaluator.aggregateRatioPatientResults(counts, input);
        assertThat(counts.get("Denominator Exceptions")).isEqualTo(0);
    }
}
