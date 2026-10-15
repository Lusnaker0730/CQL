package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.testsupport.Fuzz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-265 — the population hierarchy ({@link PopulationEvaluator#contribute}) under random per-patient
 * results: every value shape the engine can serialise (Booleans, numbers, nulls, absent defines,
 * episode lists with duplicates, lists mixing episodes with scalars, maps with and without ids), every
 * scoring type and both bases. Two kinds of check:
 * <ul>
 *   <li>invariants that hold for any input — counts are bounded by the Initial Population, a child
 *       population never exceeds its parent, a patient basis counts at most one, nothing throws;</li>
 *   <li>an independent model of the documented set algebra (the javadoc of {@code contribute}) that the
 *       counts must equal, and the per-group canonicalisation ({@code contributeToGroup} with suffixed
 *       define names) must agree with the direct computation.</li>
 * </ul>
 */
@DisplayName("PAT-265 — population algebra fuzz")
class PopulationEvaluatorFuzzTest {

    private static final List<String> EPISODES = List.of(
            "FHIR.Encounter/e1", "FHIR.Encounter/e2", "FHIR.Encounter/e3", "FHIR.Encounter/e4", "FHIR.Encounter/e5", "FHIR.Encounter/e6");
    private static final List<String> PROPORTION_POPULATIONS = List.of(
            "Initial Population", "Denominator", "Denominator Exclusions", "Denominator Exceptions", "Numerator", "Numerator Exclusions");
    private static final List<String> CV_POPULATIONS = List.of("Initial Population", "Measure Population", "Measure Population Exclusion");
    private static final Map<String, String> POPULATION_TYPES = Map.of(
            "Initial Population", "initial-population",
            "Denominator", "denominator",
            "Denominator Exclusions", "denominator-exclusion",
            "Denominator Exceptions", "denominator-exception",
            "Numerator", "numerator",
            "Numerator Exclusions", "numerator-exclusion",
            "Measure Population", "measure-population",
            "Measure Population Exclusion", "measure-population-exclusion");
    private static final String OBSERVATIONS = "Measure Observation Values";

    private final PopulationEvaluator evaluator = new PopulationEvaluator();

    record Input(String scoring, String basis, Map<String, ExpressionResult> results) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(scoring).append(" basis=").append(basis).append(" {");
            results.forEach((name, result) -> sb.append(name).append('=').append(result.getValue()).append("; "));
            return sb.append('}').toString();
        }
    }

    static Input generate(Random r) {
        String scoring = Fuzz.pick(r, "proportion", "ratio", "continuous-variable");
        String basis = Fuzz.pick(r, null, "boolean", "Boolean", "Encounter", "Procedure");
        boolean episodeFlavoured = basis != null && !basis.equalsIgnoreCase("boolean");
        List<String> names = "continuous-variable".equals(scoring) ? CV_POPULATIONS : PROPORTION_POPULATIONS;
        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        for (String name : names) {
            if (Fuzz.chance(r, 0.85)) results.put(name, result(name, randomValue(r, episodeFlavoured)));
        }
        if ("continuous-variable".equals(scoring) && Fuzz.chance(r, 0.8)) {
            List<Object> values = new ArrayList<>();
            int n = Fuzz.between(r, 0, 7);
            for (int i = 0; i < n; i++) values.add(Fuzz.pick(r, (Object) Fuzz.between(r, 0, 20), 2.5, true, false, "text", null));
            results.put(OBSERVATIONS, result(OBSERVATIONS, values));
        }
        return new Input(scoring, basis, results);
    }

    /** One define's serialised value. */
    static Object randomValue(Random r, boolean episodeFlavoured) {
        int kinds = episodeFlavoured ? 10 : 6;
        switch (r.nextInt(kinds)) {
            case 0: return Boolean.TRUE;
            case 1: return Boolean.FALSE;
            case 2: return null;
            case 3: return Fuzz.between(r, 0, 3);
            case 4: return Fuzz.pick(r, "text", "", "FHIR.Encounter/e2");
            case 5: return Map.of("resourceType", "Encounter", "id", "e" + Fuzz.between(r, 1, 6));
            case 6: case 7: {
                List<String> keys = new ArrayList<>();
                int n = Fuzz.between(r, 0, 6);
                for (int i = 0; i < n; i++) keys.add(Fuzz.pick(r, EPISODES));
                return keys;
            }
            case 8: {
                List<Object> mixed = new ArrayList<>();
                mixed.add(Fuzz.pick(r, EPISODES));
                mixed.add(Fuzz.pick(r, (Object) "x", 5, Map.of("resourceType", "Encounter"), Map.of("resourceType", "Encounter", "id", " ")));
                return mixed;
            }
            default: {
                List<Object> maps = new ArrayList<>();
                int n = Fuzz.between(r, 0, 4);
                for (int i = 0; i < n; i++) maps.add(Map.of("resourceType", "Encounter", "id", "e" + Fuzz.between(r, 1, 6)));
                return maps;
            }
        }
    }

    private static ExpressionResult result(String name, Object value) {
        return ExpressionResult.builder().name(name).value(value).valueType(value == null ? null : value.getClass().getSimpleName()).build();
    }

    // ───────────────────────────────────────────────────────────── properties

    @Test
    @DisplayName("counts respect the hierarchy, the basis and the Initial Population for any result shape")
    void contribute_holdsTheHierarchyInvariants() {
        Fuzz.forAll(20261020L, 600, PopulationEvaluatorFuzzTest::generate, (input, random) -> {
            PopulationEvaluator.PatientContribution c = evaluator.contribute(
                    input.scoring(), input.basis(), input.results(), input.results(), null);
            Map<String, Integer> counts = c.counts();
            boolean cv = "continuous-variable".equals(input.scoring());
            boolean ratio = "ratio".equals(input.scoring());
            assertThat(counts.keySet()).containsExactlyInAnyOrderElementsOf(cv ? CV_POPULATIONS : PROPORTION_POPULATIONS);
            int ip = counts.get("Initial Population");
            assertThat(ip).isGreaterThanOrEqualTo(0);
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                assertThat(e.getValue()).as(e.getKey()).isBetween(0, ip);
            }
            boolean patientBasis = input.basis() == null || input.basis().equalsIgnoreCase("boolean");
            if (patientBasis || c.fellBackToPatient()) {
                assertThat(ip).as("a patient counts at most once").isLessThanOrEqualTo(1);
            }
            if (cv) {
                int mp = counts.get("Measure Population");
                int mpExcl = counts.get("Measure Population Exclusion");
                assertThat(mp).isLessThanOrEqualTo(ip);
                assertThat(mpExcl).isLessThanOrEqualTo(mp);
                if (mp - mpExcl == 0) assertThat(c.observations()).as("no effective Measure Population, no observations").isEmpty();
            } else {
                int denom = counts.get("Denominator");
                int denomExcl = counts.get("Denominator Exclusions");
                int numer = counts.get("Numerator");
                int numerExcl = counts.get("Numerator Exclusions");
                int exceptions = counts.get("Denominator Exceptions");
                assertThat(denom).isLessThanOrEqualTo(ip);
                assertThat(denomExcl).isLessThanOrEqualTo(denom);
                int numeratorUniverse = ratio ? ip : denom - denomExcl;
                assertThat(numer + numerExcl).as("Numerator ∪ Numerator Exclusions ⊆ " + (ratio ? "IP" : "effective Denominator"))
                        .isLessThanOrEqualTo(numeratorUniverse);
                if (ratio) {
                    assertThat(exceptions).as("ratio has no Denominator Exceptions").isZero();
                } else {
                    assertThat(exceptions).isLessThanOrEqualTo(denom - denomExcl - numer);
                }
                assertThat(c.observations()).isEmpty();
            }
        });
    }

    @Test
    @DisplayName("counts equal an independent model of the documented set algebra")
    void contribute_matchesTheSetAlgebraModel() {
        Fuzz.forAll(20261021L, 600, PopulationEvaluatorFuzzTest::generate, (input, random) -> {
            PopulationEvaluator.PatientContribution c = evaluator.contribute(
                    input.scoring(), input.basis(), input.results(), input.results(), null);
            Model model = Model.of(input);
            assertThat(c.counts()).as("counts").isEqualTo(model.counts);
            assertThat(c.fellBackToPatient()).as("fellBackToPatient").isEqualTo(model.fellBack);
            assertThat(c.observations()).as("observations").isEqualTo(model.observations);
        });
    }

    @Test
    @DisplayName("contributeToGroup canonicalises suffixed define names to the same counts as the direct computation")
    void contributeToGroup_canonicalisesSuffixedNames() {
        Fuzz.forAll(20261022L, 400, PopulationEvaluatorFuzzTest::generate, (input, random) -> {
            // the per-group results: every define renamed "<name> 2", as EcqmCqlBuilder emits for group 2
            Map<String, ExpressionResult> suffixed = new LinkedHashMap<>();
            List<PopulationDefinition> populations = new ArrayList<>();
            input.results().forEach((name, result) -> {
                if (OBSERVATIONS.equals(name)) {
                    suffixed.put(name, result);   // the observation wrapper is looked up by its canonical name (null names fall back)
                } else {
                    suffixed.put(name + " 2", result);
                    populations.add(PopulationDefinition.builder().populationType(POPULATION_TYPES.get(name)).criteriaExpression(name + " 2").build());
                }
            });
            GroupDefinition group = GroupDefinition.builder().groupId("g2").populationBasis(input.basis()).populations(populations).build();
            PopulationEvaluator.PatientContribution viaGroup = evaluator.contributeToGroup(input.scoring(), List.of(group), group, suffixed);
            PopulationEvaluator.PatientContribution direct = evaluator.contribute(input.scoring(), input.basis(), input.results(), input.results(), null);
            assertThat(viaGroup.counts()).isEqualTo(direct.counts());
            assertThat(viaGroup.observations()).isEqualTo(direct.observations());
            assertThat(viaGroup.fellBackToPatient()).isEqualTo(direct.fellBackToPatient());
        });
    }

    // ───────────────────────────────────────────────────────────── the model

    /** The documented algebra, written from the javadoc rather than from the implementation. */
    static final class Model {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final List<Double> observations = new ArrayList<>();
        boolean fellBack;

        static Model of(Input input) {
            Model m = new Model();
            boolean cv = "continuous-variable".equals(input.scoring());
            boolean ratio = "ratio".equals(input.scoring());
            boolean episode = input.basis() != null && !input.basis().equalsIgnoreCase("boolean");
            Map<String, ExpressionResult> results = input.results();

            ExpressionResult ipResult = results.get("Initial Population");
            List<String> ipKeys = episode && ipResult != null ? keys(ipResult.getValue()) : null;
            Set<String> ip;
            if (episode && ipKeys != null) {
                ip = new LinkedHashSet<>(ipKeys);
            } else {
                if (episode && ipResult != null && ipResult.getValue() != null) m.fellBack = true;
                episode = false;
                ip = truthy(ipResult) ? Set.of("patient") : Set.of();
            }
            for (String name : cv ? CV_POPULATIONS : PROPORTION_POPULATIONS) m.counts.put(name, 0);
            m.counts.put("Initial Population", ip.size());

            if (cv) {
                Set<String> mp = members(results, "Measure Population", ip, episode);
                Set<String> mpExcluded = intersect(mp, members(results, "Measure Population Exclusion", ip, episode));
                Set<String> effective = minus(mp, mpExcluded);
                m.counts.put("Measure Population", mp.size());
                m.counts.put("Measure Population Exclusion", mpExcluded.size());
                if (!effective.isEmpty()) {
                    List<Double> values = numericValues(results.get(OBSERVATIONS));
                    List<String> mpKeys = episode ? keys(results.get("Measure Population").getValue()) : null;
                    if (episode && mpKeys != null && mpKeys.size() == values.size()) {
                        for (int i = 0; i < values.size(); i++) if (effective.contains(mpKeys.get(i))) m.observations.add(values.get(i));
                    } else {
                        m.observations.addAll(values);
                    }
                }
            } else {
                Set<String> denom = members(results, "Denominator", ip, episode);
                Set<String> denomExcluded = intersect(denom, members(results, "Denominator Exclusions", ip, episode));
                Set<String> effectiveDenom = minus(denom, denomExcluded);
                Set<String> numer = intersect(ratio ? ip : effectiveDenom, members(results, "Numerator", ip, episode));
                Set<String> numerExcluded = intersect(numer, members(results, "Numerator Exclusions", ip, episode));
                Set<String> effectiveNumer = minus(numer, numerExcluded);
                m.counts.put("Denominator", denom.size());
                m.counts.put("Denominator Exclusions", denomExcluded.size());
                m.counts.put("Numerator", effectiveNumer.size());
                m.counts.put("Numerator Exclusions", numerExcluded.size());
                if (!ratio) {
                    Set<String> exceptions = intersect(minus(effectiveDenom, effectiveNumer), members(results, "Denominator Exceptions", ip, episode));
                    m.counts.put("Denominator Exceptions", exceptions.size());
                }
            }
            return m;
        }

        /** The members a define keeps of the universe: its own episode list, else all (truthy) or none. */
        static Set<String> members(Map<String, ExpressionResult> results, String name, Set<String> universe, boolean episode) {
            ExpressionResult result = results.get(name);
            if (result == null || result.getValue() == null) return Set.of();
            if (episode) {
                List<String> keys = keys(result.getValue());
                if (keys != null) return intersect(universe, new LinkedHashSet<>(keys));
            }
            return truthy(result) ? new LinkedHashSet<>(universe) : Set.of();
        }

        /** "true" as the evaluator reads a value: Boolean true, a positive number, a non-empty list. */
        static boolean truthy(ExpressionResult result) {
            if (result == null) return false;
            Object value = result.getValue();
            if (value instanceof Boolean b) return b;
            if (value instanceof Number n) return n.intValue() > 0;
            if (value instanceof Iterable<?> items) return items.iterator().hasNext();
            return false;
        }

        /** Episode keys of a serialised value, or null when it is not an identifiable list of resources. */
        static List<String> keys(Object value) {
            if (value instanceof Iterable<?> items) {
                List<String> keys = new ArrayList<>();
                for (Object item : items) {
                    String key = keyOf(item);
                    if (key == null) return null;
                    keys.add(key);
                }
                return keys;
            }
            String single = keyOf(value);
            return single == null ? null : List.of(single);
        }

        static String keyOf(Object item) {
            if (item instanceof String s) {
                int slash = s.indexOf('/');
                return s.startsWith("FHIR.") && slash > 5 && slash < s.length() - 1 ? s : null;
            }
            if (item instanceof Map<?, ?> map) {
                Object type = map.get("resourceType");
                Object id = map.get("id");
                return type != null && id != null && !String.valueOf(id).isBlank() ? "FHIR." + type + "/" + id : null;
            }
            return null;
        }

        static List<Double> numericValues(ExpressionResult result) {
            if (result == null || result.getValue() == null) return List.of();
            Object value = result.getValue();
            List<Double> out = new ArrayList<>();
            if (value instanceof Number n) out.add(n.doubleValue());
            else if (value instanceof Boolean b) { if (b) out.add(1.0); }
            else if (value instanceof Iterable<?> items) {
                for (Object item : items) {
                    if (item instanceof Number n) out.add(n.doubleValue());
                    else if (item instanceof Boolean b && b) out.add(1.0);
                }
            }
            return out;
        }

        static Set<String> intersect(Set<String> a, Set<String> b) {
            Set<String> out = new LinkedHashSet<>(a);
            out.retainAll(b);
            return out;
        }

        static Set<String> minus(Set<String> a, Set<String> b) {
            Set<String> out = new LinkedHashSet<>(a);
            out.removeAll(b);
            return out;
        }
    }

    @SuppressWarnings("unused")
    private static List<Object> shuffled(Random r, List<?> items) {
        List<Object> copy = new ArrayList<>(items);
        Collections.shuffle(copy, r);
        return copy;
    }
}
