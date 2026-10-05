package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.PopulationMembershipTrace;
import com.cqlplatform.model.measure.TestCaseExpectedValues;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
    void aggregateCv_perGroupPath_readsObservationFromRawResultsNotCanonicalMap() {
        // Regression lock for BUG-474 follow-up.
        // The per-group eCQM path canonicalizes suffixed population define names ("Initial
        // Population 1" → "Initial Population") into a FILTERED map containing only
        // PopulationDefinition entries. "Measure Observation Value" is a top-level CQL
        // define (not a population), so it only lives in the raw results map. The pre-fix
        // 3-arg aggregateCvPatientResults received the canonical map for BOTH lookups,
        // which silently dropped every patient's observation → measureScore stuck at null
        // (smoke scenarios 03/05–09 all surfaced as `score: null`).
        Map<String, CqlExecutionResponse.ExpressionResult> canonical = new LinkedHashMap<>();
        canonical.put("Initial Population", boolResult(true));
        canonical.put("Measure Population", boolResult(true));
        // Raw results has BOTH the canonical population entries AND the observation define.
        Map<String, CqlExecutionResponse.ExpressionResult> raw = new LinkedHashMap<>(canonical);
        raw.put("Measure Observation Value", boolResult(true));

        java.util.List<Double> observationValues = new java.util.ArrayList<>();
        Map<String, Integer> counts = evaluator.initializeCvPopulationCounts();

        evaluator.aggregateCvPatientResults(counts, canonical, raw, observationValues);

        assertThat(counts.get("Initial Population")).isEqualTo(1);
        assertThat(counts.get("Measure Population")).isEqualTo(1);
        // The fix: observation extracted from raw map → 1 boolean TRUE = 1.0
        assertThat(observationValues).containsExactly(1.0);
    }

    @Test
    void aggregateCv_perGroupSuffixedObservationDefines_readsExplicitNames() {
        // Issue #539 regression lock.
        // Multi-group CV measures: EcqmCqlBuilder emits "Measure Observation Value 1" /
        // "... 2" per group, not the unsuffixed canonical name. The 5-arg overload accepts
        // the explicit observation expression names and looks them up in allResults.
        Map<String, CqlExecutionResponse.ExpressionResult> canonical = new LinkedHashMap<>();
        canonical.put("Initial Population", boolResult(true));
        canonical.put("Measure Population", boolResult(true));
        Map<String, CqlExecutionResponse.ExpressionResult> raw = new LinkedHashMap<>(canonical);
        // Group 1's observation define — suffixed
        raw.put("Measure Observation Value 1", boolResult(true));
        // Group 2's observation — should NOT be picked up when we ask for group 1
        raw.put("Measure Observation Value 2", boolResult(true));

        java.util.List<Double> observationValues = new java.util.ArrayList<>();
        Map<String, Integer> counts = evaluator.initializeCvPopulationCounts();

        evaluator.aggregateCvPatientResults(
                counts, canonical, raw,
                java.util.List.of("Measure Observation Value 1"),
                observationValues);

        // Only group 1's observation contributes → 1 boolean TRUE = 1.0
        assertThat(observationValues).containsExactly(1.0);
        assertThat(counts.get("Initial Population")).isEqualTo(1);
        assertThat(counts.get("Measure Population")).isEqualTo(1);
    }

    @Test
    void aggregateCv_perGroupMultipleObservationDefines_aggregatesAll() {
        // A single group may declare multiple observations (e.g. count + average together);
        // each one's values should accumulate into the group's bucket.
        Map<String, CqlExecutionResponse.ExpressionResult> canonical = new LinkedHashMap<>();
        canonical.put("Initial Population", boolResult(true));
        canonical.put("Measure Population", boolResult(true));
        Map<String, CqlExecutionResponse.ExpressionResult> raw = new LinkedHashMap<>(canonical);
        raw.put("Obs A", boolResult(true));   // → 1.0
        raw.put("Obs B", CqlExecutionResponse.ExpressionResult.builder()
                .value(42.0).valueType("Decimal").build()); // → 42.0

        java.util.List<Double> observationValues = new java.util.ArrayList<>();
        Map<String, Integer> counts = evaluator.initializeCvPopulationCounts();

        evaluator.aggregateCvPatientResults(
                counts, canonical, raw,
                java.util.List.of("Obs A", "Obs B"),
                observationValues);

        assertThat(observationValues).containsExactly(1.0, 42.0);
    }

    @Test
    void aggregateCv_emptyObservationNames_fallsBackToCanonical() {
        // Empty list (not null) should still trigger the legacy unsuffixed-name fallback,
        // matching the 4-arg overload's behavior.
        Map<String, CqlExecutionResponse.ExpressionResult> raw = new LinkedHashMap<>();
        raw.put("Initial Population", boolResult(true));
        raw.put("Measure Population", boolResult(true));
        raw.put("Measure Observation Value", boolResult(true));

        java.util.List<Double> observationValues = new java.util.ArrayList<>();
        Map<String, Integer> counts = evaluator.initializeCvPopulationCounts();

        evaluator.aggregateCvPatientResults(
                counts, raw, raw, java.util.List.of(), observationValues);

        assertThat(observationValues).containsExactly(1.0);
    }

    @Test
    void aggregateCv_legacyTwoArgOverload_stillWorks() {
        // The legacy single-group path passes the same raw map for both lookups — make sure
        // the 2-arg convenience overload keeps the same behavior after the 3-map refactor.
        Map<String, CqlExecutionResponse.ExpressionResult> raw = new LinkedHashMap<>();
        raw.put("Initial Population", boolResult(true));
        raw.put("Measure Population", boolResult(true));
        raw.put("Measure Observation Value", boolResult(true));

        java.util.List<Double> observationValues = new java.util.ArrayList<>();
        Map<String, Integer> counts = evaluator.initializeCvPopulationCounts();

        evaluator.aggregateCvPatientResults(counts, raw, observationValues);

        assertThat(observationValues).containsExactly(1.0);
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

    // ===== PAT-243 — episode-based groups count episodes, not patients =====

    private CqlExecutionResponse.ExpressionResult episodes(String... ids) {
        List<Object> list = new ArrayList<>();
        for (String id : ids) list.add("FHIR.Encounter/" + id);
        return CqlExecutionResponse.ExpressionResult.builder().value(list).valueType("List").build();
    }

    private static PopulationDefinition pop(String type, String expression) {
        return PopulationDefinition.builder().populationType(type).criteriaExpression(expression).build();
    }

    private static GroupDefinition encounterGroup() {
        return GroupDefinition.builder().groupId("g1").populationBasis("Encounter")
                .populations(List.of(pop("initial-population", "Initial Population"),
                        pop("denominator", "Denominator"), pop("numerator", "Numerator")))
                .build();
    }

    @Test
    void episodeProportion_countsTheEpisodesThatSurviveTheHierarchy() {
        Map<String, CqlExecutionResponse.ExpressionResult> r = new LinkedHashMap<>();
        r.put("Initial Population", episodes("e1", "e2", "e3"));
        r.put("Denominator", episodes("e1", "e2", "e9"));          // e9 is not an IP episode → ignored
        r.put("Denominator Exclusions", episodes("e2"));
        r.put("Numerator", episodes("e1", "e2"));                  // e2 was excluded → does not count
        r.put("Numerator Exclusions", boolResult(false));
        r.put("Denominator Exceptions", episodes("e1", "e3"));     // e1 is in the numerator, e3 not in the denominator

        PopulationEvaluator.PatientContribution c = evaluator.contribute("proportion", "Encounter", r, r, null);

        assertThat(c.episodeBased()).isTrue();
        assertThat(c.fellBackToPatient()).isFalse();
        assertThat(c.counts())
                .containsEntry("Initial Population", 3)
                .containsEntry("Denominator", 2)
                .containsEntry("Denominator Exclusions", 1)
                .containsEntry("Numerator", 1)
                .containsEntry("Numerator Exclusions", 0)
                .containsEntry("Denominator Exceptions", 0);
    }

    @Test
    void episodeProportion_aBooleanChildKeepsAllOrNoneOfTheParentsEpisodes() {
        Map<String, CqlExecutionResponse.ExpressionResult> r = new LinkedHashMap<>();
        r.put("Initial Population", episodes("e1", "e2", "e3"));
        r.put("Denominator", boolResult(true));                    // a patient-level criterion
        r.put("Numerator", episodes("e3"));

        PopulationEvaluator.PatientContribution c = evaluator.contribute("proportion", "Encounter", r, r, null);
        assertThat(c.counts()).containsEntry("Denominator", 3).containsEntry("Numerator", 1);

        r.put("Denominator", boolResult(false));
        c = evaluator.contribute("proportion", "Encounter", r, r, null);
        assertThat(c.counts()).containsEntry("Initial Population", 3).containsEntry("Denominator", 0).containsEntry("Numerator", 0);
    }

    @Test
    void episodeRatio_numeratorIsGatedByTheInitialPopulationOnly() {
        Map<String, CqlExecutionResponse.ExpressionResult> r = new LinkedHashMap<>();
        r.put("Initial Population", episodes("e1", "e2", "e3"));
        r.put("Denominator", episodes("e1"));
        r.put("Numerator", episodes("e2", "e3", "e9"));

        PopulationEvaluator.PatientContribution c = evaluator.contribute("ratio", "Encounter", r, r, null);

        assertThat(c.counts()).containsEntry("Denominator", 1).containsEntry("Numerator", 2);
    }

    @Test
    void episodeCv_keepsOnlyTheObservationsOfEpisodesThatSurviveTheExclusion() {
        Map<String, CqlExecutionResponse.ExpressionResult> canonical = new LinkedHashMap<>();
        canonical.put("Initial Population", episodes("e1", "e2", "e3"));
        canonical.put("Measure Population", episodes("e1", "e2", "e3"));
        canonical.put("Measure Population Exclusion", episodes("e2"));
        Map<String, CqlExecutionResponse.ExpressionResult> all = new LinkedHashMap<>(canonical);
        all.put("Measure Observation Values", CqlExecutionResponse.ExpressionResult.builder()
                .value(List.of(2.0, 4.0, 6.0)).valueType("List").build());

        PopulationEvaluator.PatientContribution c = evaluator.contribute("continuous-variable", "Encounter", canonical, all, null);

        assertThat(c.counts()).containsEntry("Initial Population", 3).containsEntry("Measure Population", 3)
                .containsEntry("Measure Population Exclusion", 1);
        assertThat(c.observations()).containsExactly(2.0, 6.0);

        // a wrapper that does not line up with the Measure Population list: every value counts, as before
        all.put("Measure Observation Values", CqlExecutionResponse.ExpressionResult.builder()
                .value(List.of(2.0, 4.0)).valueType("List").build());
        assertThat(evaluator.contribute("continuous-variable", "Encounter", canonical, all, null).observations())
                .containsExactly(2.0, 4.0);
    }

    @Test
    void episodeBasis_withABooleanInitialPopulation_fallsBackToCountingThePatient_andSaysSo() {
        Map<String, CqlExecutionResponse.ExpressionResult> r = new LinkedHashMap<>();
        r.put("Initial Population", boolResult(true));
        r.put("Denominator", episodes("e1", "e2"));
        r.put("Numerator", boolResult(true));

        PopulationEvaluator.PatientContribution c = evaluator.contribute("proportion", "Encounter", r, r, null);

        assertThat(c.episodeBased()).isFalse();
        assertThat(c.fellBackToPatient()).isTrue();
        assertThat(c.counts()).containsEntry("Initial Population", 1).containsEntry("Denominator", 1).containsEntry("Numerator", 1);

        // an Initial Population that is false is simply "not in the IP" — nothing to warn about
        r.put("Initial Population", boolResult(false));
        c = evaluator.contribute("proportion", "Encounter", r, r, null);
        assertThat(c.fellBackToPatient()).isTrue();
        assertThat(c.counts()).containsEntry("Initial Population", 0).containsEntry("Denominator", 0);
    }

    @Test
    void patientBasis_readsListsAsTruthy_exactlyLikeBefore() {
        Map<String, CqlExecutionResponse.ExpressionResult> r = new LinkedHashMap<>();
        r.put("Initial Population", episodes("e1", "e2"));
        r.put("Denominator", boolResult(true));
        r.put("Numerator", episodes("e9"));

        PopulationEvaluator.PatientContribution c = evaluator.contribute("proportion", "boolean", r, r, null);

        assertThat(c.episodeBased()).isFalse();
        assertThat(c.fellBackToPatient()).isFalse();
        assertThat(c.counts()).containsEntry("Initial Population", 1).containsEntry("Denominator", 1).containsEntry("Numerator", 1);
    }

    @Test
    void contributeToGroup_readsTheGroupsBasis_andTheTestCaseRunnerSeesEpisodeCounts() {
        GroupDefinition group = encounterGroup();
        Map<String, CqlExecutionResponse.ExpressionResult> r = new LinkedHashMap<>();
        r.put("Initial Population", episodes("e1", "e2"));
        r.put("Denominator", episodes("e1", "e2"));
        r.put("Numerator", episodes("e1"));

        TestCaseExpectedValues.GroupValues values = evaluator.evaluateSinglePatient("proportion", List.of(group), group, r);

        assertThat(values.getPopulations()).containsEntry("initial-population", 2)
                .containsEntry("denominator", 2).containsEntry("numerator", 1);
    }

    @Test
    void trace_carriesEpisodeCounts_forAnEpisodeBasedGroup() {
        GroupDefinition group = encounterGroup();
        Map<String, CqlExecutionResponse.ExpressionResult> r = new LinkedHashMap<>();
        r.put("Initial Population", episodes("e1", "e2"));
        r.put("Denominator", episodes("e1", "e2"));
        r.put("Numerator", episodes("e1"));
        MeasureDefinition measure = MeasureDefinition.builder().scoringType("proportion").groupDefinitions(List.of(group)).build();
        CqlExecutionResponse response = CqlExecutionResponse.builder().success(true).results(r).build();

        PopulationMembershipTrace trace = evaluator.buildTestCaseTrace(measure, response);

        PopulationMembershipTrace.GroupTrace g = trace.getGroups().get(0);
        assertThat(g.getPopulationBasis()).isEqualTo("Encounter");
        assertThat(g.getPopulations())
                .extracting(PopulationMembershipTrace.PopulationTraceEntry::getDisplayName,
                        PopulationMembershipTrace.PopulationTraceEntry::getMemberCount,
                        PopulationMembershipTrace.PopulationTraceEntry::getEffectiveResult)
                .contains(tuple("Initial Population", 2, true), tuple("Denominator", 2, true), tuple("Numerator", 1, true));
    }
}
