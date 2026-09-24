package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.MeasureEvaluationResult.StratifierResult;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.model.measure.StratifierDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-233 — value-based strata and per-scoring-type stratum scores. The evaluator has always
 * keyed strata by the string form of the stratifier expression's value; these tests pin what
 * that key is for the values a value stratifier can return, and that a stratum's score follows
 * the measure's scoring type instead of always being a proportion.
 */
class StratifierEvaluatorTest {

    private StratifierEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new StratifierEvaluator(new PopulationEvaluator(), new MeasureScoreCalculator());
    }

    private static CqlExecutionResponse.ExpressionResult value(Object v) {
        return CqlExecutionResponse.ExpressionResult.builder().value(v).build();
    }

    private static StratifierDefinition stratifier(String id, String expression, String kind) {
        return StratifierDefinition.builder().stratifierId(id).criteriaExpression(expression).kind(kind).build();
    }

    // ---------------------------------------------------------------- stratum keys

    @Test
    void stratumKey_criteriaAndValueStratifiers() {
        assertThat(StratifierEvaluator.stratumKey(true)).isEqualTo("true");
        assertThat(StratifierEvaluator.stratumKey(false)).isEqualTo("false");
        assertThat(StratifierEvaluator.stratumKey("female")).isEqualTo("female");
        assertThat(StratifierEvaluator.stratumKey("  65+  ")).isEqualTo("65+");
        assertThat(StratifierEvaluator.stratumKey(3)).isEqualTo("3");
        assertThat(StratifierEvaluator.stratumKey(new java.math.BigDecimal("2.50"))).isEqualTo("2.50");
    }

    @Test
    void stratumKey_nothingMeansNoStratum() {
        assertThat(StratifierEvaluator.stratumKey(null)).isNull();
        assertThat(StratifierEvaluator.stratumKey("")).isNull();
        assertThat(StratifierEvaluator.stratumKey("   ")).isNull();
        assertThat(StratifierEvaluator.stratumKey("null")).isNull();   // the legacy "rendered null" rule
        assertThat(StratifierEvaluator.stratumKey(List.of())).isNull();
    }

    @Test
    void stratumKey_codesAndConcepts_readAsTheirCodeOrDisplay() {
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("code", "E11.9");
        code.put("system", "http://hl7.org/fhir/sid/icd-10-cm");
        assertThat(StratifierEvaluator.stratumKey(code)).isEqualTo("E11.9");
        code.put("display", "Type 2 diabetes");
        assertThat(StratifierEvaluator.stratumKey(code)).isEqualTo("E11.9 (Type 2 diabetes)");

        Map<String, Object> concept = new LinkedHashMap<>();
        concept.put("codes", List.of(code));
        assertThat(StratifierEvaluator.stratumKey(concept)).isEqualTo("E11.9 (Type 2 diabetes)");
        concept.put("display", "Diabetes");
        assertThat(StratifierEvaluator.stratumKey(concept)).isEqualTo("Diabetes");

        // any other map is not a code — it renders as text rather than pretending
        assertThat(StratifierEvaluator.stratumKey(Map.of("x", 1))).isEqualTo("{x=1}");
    }

    @Test
    void stratumKey_listsJoin_andLongKeysAreCut() {
        assertThat(StratifierEvaluator.stratumKey(List.of("a", "b"))).isEqualTo("a, b");
        assertThat(StratifierEvaluator.stratumKey(java.util.Arrays.asList("a", null, ""))).isEqualTo("a");
        String longKey = "x".repeat(300);
        String cut = StratifierEvaluator.stratumKey(longKey);
        assertThat(cut).hasSize(StratifierEvaluator.MAX_STRATUM_KEY).endsWith("…");
    }

    // ---------------------------------------------------------------- bucketing

    @Test
    void valueStratifier_bucketsPatientsByTheReturnedValue_inFirstSeenOrder() {
        StratifierDefinition byGender = stratifier("sex", "Stratifier sex", StratifierDefinition.KIND_VALUE);
        Map<String, Map<String, Map<String, Integer>>> data = new HashMap<>();

        // three patients: female in IP+Denom+Numer, male in IP+Denom, female in IP only
        for (Object[] patient : new Object[][]{
                {"female", true, true, true}, {"male", true, true, false}, {"female", true, false, false}, {null, true, true, true}}) {
            Map<String, CqlExecutionResponse.ExpressionResult> results = new HashMap<>();
            results.put("Stratifier sex", value(patient[0]));
            results.put("Initial Population", value(patient[1]));
            results.put("Denominator", value(patient[2]));
            results.put("Numerator", value(patient[3]));
            evaluator.evaluatePatientStratifiers(List.of(byGender), results, data);
        }

        List<StratifierResult> strata = evaluator.buildStratifierResults(data, ScoringTypeConstants.PROPORTION);
        assertThat(strata).extracting(StratifierResult::getStrataValue).containsExactly("female", "male");
        StratifierResult female = strata.get(0);
        assertThat(female.getStrataId()).isEqualTo("sex");
        assertThat(female.getPopulations()).extracting(p -> p.getPopulationType() + "=" + p.getCount())
                .contains("initial-population=2", "denominator=1", "numerator=1");
        assertThat(female.getMeasureScore()).isEqualTo(100.0);
        assertThat(strata.get(1).getMeasureScore()).isEqualTo(0.0);
        // the patient whose value was null is in no stratum at all
        assertThat(strata).allSatisfy(s -> assertThat(s.getPopulations())
                .filteredOn(p -> "initial-population".equals(p.getPopulationType()))
                .extracting(p -> p.getCount()).doesNotContain(4));
    }

    @Test
    void resolveStratumValue_usesTheSameKeyRule() {
        Map<String, CqlExecutionResponse.ExpressionResult> results = Map.of(
                "Stratifier band", value("65+"), "Stratifier flag", value(false), "Stratifier none", value(null));
        assertThat(evaluator.resolveStratumValue(stratifier("band", "Stratifier band", "value"), results)).isEqualTo("65+");
        assertThat(evaluator.resolveStratumValue(stratifier("flag", "Stratifier flag", null), results)).isEqualTo("false");
        assertThat(evaluator.resolveStratumValue(stratifier("none", "Stratifier none", null), results)).isNull();
        assertThat(evaluator.resolveStratumValue(stratifier("gone", "Stratifier gone", null), results)).isNull();
    }

    // ---------------------------------------------------------------- scores follow the scoring type

    private Map<String, Map<String, Map<String, Integer>>> oneStratum(int ip, int denom, int denomExcl, int numer) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Initial Population", ip);
        counts.put("Denominator", denom);
        counts.put("Denominator Exclusions", denomExcl);
        counts.put("Numerator", numer);
        Map<String, Map<String, Map<String, Integer>>> data = new LinkedHashMap<>();
        data.put("s", new LinkedHashMap<>(Map.of("true", counts)));
        return data;
    }

    @Test
    void stratumScore_followsTheMeasureScoringType() {
        // 10 in IP, 8 in denominator of which 2 excluded, 3 in numerator
        Map<String, Map<String, Map<String, Integer>>> data = oneStratum(10, 8, 2, 3);

        assertThat(evaluator.buildStratifierResults(data, ScoringTypeConstants.PROPORTION).get(0).getMeasureScore())
                .as("proportion: numer / (denom - excl)").isEqualTo(50.0);
        assertThat(evaluator.buildStratifierResults(data, null).get(0).getMeasureScore())
                .as("no scoring type behaves as proportion (legacy measures)").isEqualTo(50.0);
        assertThat(evaluator.buildStratifierResults(data, ScoringTypeConstants.RATIO).get(0).getMeasureScore())
                .as("ratio: numer / denom, exclusions not subtracted").isEqualTo(37.5);
        assertThat(evaluator.buildStratifierResults(data, ScoringTypeConstants.COHORT).get(0).getMeasureScore())
                .as("cohort: the stratum's initial population").isEqualTo(10.0);
        assertThat(evaluator.buildStratifierResults(data, ScoringTypeConstants.CONTINUOUS_VARIABLE).get(0).getMeasureScore())
                .as("CV: observation values are not collected per stratum").isNull();
        assertThat(evaluator.buildStratifierResults(data, "Proportion").get(0).getMeasureScore())
                .as("case-insensitive").isEqualTo(50.0);
    }

    // ---------------------------------------------------------------- PAT-235: multi-component strata

    private static StratifierDefinition sexByAge() {
        return StratifierDefinition.builder().stratifierId("sex-age").components(List.of(
                StratifierDefinition.Component.builder().code("sex").criteriaExpression("Stratifier sex-age sex").kind("value").build(),
                StratifierDefinition.Component.builder().code("age").criteriaExpression("Stratifier sex-age age").kind("value").build()))
                .build();
    }

    @Test
    void componentStratifier_bucketsByTheCombination_andReportsEachComponent() {
        Map<String, Map<String, Map<String, Integer>>> data = new HashMap<>();
        Object[][] patients = {
                {"female", "65+", true, true, true},
                {"male", "18-49", true, false, false},
                {"female", "65+", true, true, false},
                {"female", null, true, true, true},      // missing a component → no stratum
                {null, "65+", true, true, true},
        };
        for (Object[] p : patients) {
            Map<String, CqlExecutionResponse.ExpressionResult> results = new HashMap<>();
            results.put("Stratifier sex-age sex", value(p[0]));
            results.put("Stratifier sex-age age", value(p[1]));
            results.put("Initial Population", value(p[2]));
            results.put("Denominator", value(p[3]));
            results.put("Numerator", value(p[4]));
            evaluator.evaluatePatientStratifiers(List.of(sexByAge()), results, data);
        }

        List<StratifierResult> strata = evaluator.buildStratifierResults(data, ScoringTypeConstants.PROPORTION);
        assertThat(strata).extracting(StratifierResult::getStrataValue).containsExactly("female | 65+", "male | 18-49");
        StratifierResult femaleElderly = strata.get(0);
        assertThat(femaleElderly.getComponents()).extracting(c -> c.getCode() + "=" + c.getValue())
                .containsExactly("sex=female", "age=65+");
        assertThat(femaleElderly.getPopulations()).extracting(p -> p.getPopulationType() + "=" + p.getCount())
                .contains("initial-population=2", "denominator=2", "numerator=1");
        assertThat(femaleElderly.getMeasureScore()).isEqualTo(50.0);
        assertThat(strata.get(1).getComponents()).extracting(c -> c.getValue()).containsExactly("male", "18-49");
        // the two patients missing a component value are in no stratum: 2 + 1 = 3 of 5 in strata
        assertThat(strata).flatExtracting(StratifierResult::getPopulations)
                .filteredOn(p -> "initial-population".equals(p.getPopulationType()))
                .extracting(p -> p.getCount()).containsExactly(2, 1);
    }

    @Test
    void componentStratifier_testCaseRunnerSeesTheSameCombinedValue_andSeparatorsCannotBeForged() {
        Map<String, CqlExecutionResponse.ExpressionResult> results = Map.of(
                "Stratifier sex-age sex", value("female"),   // a value trying to fake a boundary
                "Stratifier sex-age age", value("65+"));
        assertThat(evaluator.resolveStratumValue(sexByAge(), results)).isEqualTo("fe male | 65+");

        String key = evaluator.resolveStratumKey(sexByAge(), results);
        assertThat(StratifierEvaluator.decodeComponents(key)).extracting(c -> c.getCode() + "=" + c.getValue())
                .containsExactly("sex=fe male", "age=65+");
        assertThat(StratifierEvaluator.decodeComponents("plain")).isEmpty();
        assertThat(StratifierEvaluator.displayValue("plain")).isEqualTo("plain");

        // a stratifier with components but a component missing from the results → no stratum
        assertThat(evaluator.resolveStratumValue(sexByAge(), Map.of("Stratifier sex-age sex", value("female")))).isNull();
        // single-expression stratifiers keep carrying no components
        Map<String, Map<String, Map<String, Integer>>> data = new HashMap<>();
        evaluator.evaluatePatientStratifiers(List.of(stratifier("s", "Stratifier s", null)),
                Map.of("Stratifier s", value(true), "Initial Population", value(true)), data);
        assertThat(evaluator.buildStratifierResults(data, null).get(0).getComponents()).isNull();
    }
}
