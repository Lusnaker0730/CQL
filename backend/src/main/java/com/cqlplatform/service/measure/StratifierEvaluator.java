package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.PopulationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.StratifierResult;
import com.cqlplatform.model.measure.PopulationTypeConstants;
import com.cqlplatform.model.measure.StratifierDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Evaluates and aggregates stratification results from CQL execution.
 * Pure logic — no FHIR or database dependencies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StratifierEvaluator {

    private final PopulationEvaluator populationEvaluator;
    private final MeasureScoreCalculator scoreCalculator;

    /**
     * Extracts all stratifier definitions from a measure's group definitions.
     */
    public List<StratifierDefinition> getStratifiers(MeasureDefinition definition) {
        if (definition == null || definition.getGroupDefinitions() == null) {
            return Collections.emptyList();
        }
        List<StratifierDefinition> all = new ArrayList<>();
        for (GroupDefinition group : definition.getGroupDefinitions()) {
            if (group.getStratifiers() != null) {
                all.addAll(group.getStratifiers());
            }
        }
        return all;
    }

    /**
     * The stratum one patient falls into for a stratifier, as the evaluation keys it: the
     * string form of the stratifier expression's value ({@code "true"} / {@code "false"} for
     * criteria stratifiers). {@code null} when the expression is missing from the results or
     * evaluates to null — such a patient is in no stratum.
     *
     * <p>Single source of the rule: the production aggregation below and the test case runner
     * (PAT-228) both go through it, so an expected stratum in a test means exactly what a
     * report would show.
     *
     * @param rawResults full CQL results map (stratifier defines are suffixed per group and
     *                   only exist here, not in the canonical population view)
     */
    public String resolveStratumValue(StratifierDefinition stratifier,
                                      Map<String, CqlExecutionResponse.ExpressionResult> rawResults) {
        String key = resolveStratumKey(stratifier, rawResults);
        return key == null ? null : displayValue(key);
    }

    // ---------------------------------------------------------------- PAT-235: multi-component strata

    /** Separates the components of an encoded stratum key (ASCII unit separator). */
    static final char COMPONENT_SEPARATOR = '\u001F';
    /** Separates a component's code from its value inside an encoded key (ASCII record separator). */
    static final char CODE_SEPARATOR = '\u001E';
    /** What a report shows between the components of a stratum. */
    static final String DISPLAY_SEPARATOR = " | ";

    /**
     * The accumulation key of one patient's stratum. Single-expression stratifiers key on the
     * value's text ({@link ValueKeys}); multi-component stratifiers (FHIR
     * {@code stratifier.component[]}) key on the encoded combination
     * {@code code\u001Evalue\u001Fcode\u001Evalue…} — self-describing, so
     * {@link #buildStratifierResults} can hand each stratum its component values back without
     * the definition. {@link ValueKeys} strips both separators from every value, so a value can
     * never fake a component boundary. A patient missing any one component's value is in no
     * stratum, as with a single null value.
     */
    String resolveStratumKey(StratifierDefinition stratifier,
                             Map<String, CqlExecutionResponse.ExpressionResult> rawResults) {
        if (stratifier == null || rawResults == null) return null;
        if (stratifier.hasComponents()) {
            StringBuilder key = new StringBuilder();
            for (StratifierDefinition.Component component : stratifier.getComponents()) {
                if (component.getCriteriaExpression() == null) return null;
                CqlExecutionResponse.ExpressionResult result = rawResults.get(component.getCriteriaExpression());
                String value = result == null ? null : stratumKey(result.getValue());
                if (value == null) return null;
                if (key.length() > 0) key.append(COMPONENT_SEPARATOR);
                String code = component.getCode() != null ? component.getCode() : component.getCriteriaExpression();
                key.append(code.replace(COMPONENT_SEPARATOR, ' ').replace(CODE_SEPARATOR, ' ')).append(CODE_SEPARATOR).append(value);
            }
            return key.length() == 0 ? null : key.toString();
        }
        if (stratifier.getCriteriaExpression() == null) return null;
        CqlExecutionResponse.ExpressionResult stratResult = rawResults.get(stratifier.getCriteriaExpression());
        if (stratResult == null) return null;
        return stratumKey(stratResult.getValue());
    }

    /** The components of an encoded key, in order; empty for a single-expression stratum. */
    static List<MeasureEvaluationResult.StratumComponent> decodeComponents(String key) {
        List<MeasureEvaluationResult.StratumComponent> out = new ArrayList<>();
        if (key == null || key.indexOf(CODE_SEPARATOR) < 0) return out;
        for (String part : key.split(String.valueOf(COMPONENT_SEPARATOR))) {
            int at = part.indexOf(CODE_SEPARATOR);
            if (at < 0) continue;
            out.add(MeasureEvaluationResult.StratumComponent.builder()
                    .code(part.substring(0, at)).value(part.substring(at + 1)).build());
        }
        return out;
    }

    /** {@code female | 65+} for an encoded multi-component key; the key itself otherwise. */
    static String displayValue(String key) {
        List<MeasureEvaluationResult.StratumComponent> components = decodeComponents(key);
        if (components.isEmpty()) return key;
        List<String> values = new ArrayList<>();
        for (MeasureEvaluationResult.StratumComponent c : components) values.add(c.getValue());
        return String.join(DISPLAY_SEPARATOR, values);
    }

    /** Kept for callers and tests; the rule lives in {@link ValueKeys} since PAT-234 shares it with supplemental data. */
    static final int MAX_STRATUM_KEY = ValueKeys.MAX_KEY;

    /** PAT-233 — the stratum a (serialised) CQL value denotes; see {@link ValueKeys#of}. */
    static String stratumKey(Object value) {
        return ValueKeys.of(value);
    }

    /**
     * Evaluates stratifiers for a single patient and accumulates into the stratification data.
     * Single-input overload for callers that don't need to distinguish stratifier-expression
     * lookup from population lookup (single-group measures where the CQL define names match
     * the canonical population names verbatim).
     */
    public void evaluatePatientStratifiers(List<StratifierDefinition> stratifiers,
                                           Map<String, CqlExecutionResponse.ExpressionResult> results,
                                           Map<String, Map<String, Map<String, Integer>>> stratificationData) {
        evaluatePatientStratifiers(stratifiers, results, results, stratificationData);
    }

    /**
     * Evaluates stratifiers for a single patient and accumulates into the stratification data.
     *
     * <p>BUG #474 follow-up: multi-group measures need two views of the result map.
     * The stratifier <em>expression</em> name is the suffixed CQL define ("Stratifier gender 1"),
     * which only exists in the raw CQL results map. The <em>population</em> count lookup
     * (Initial Population / Denominator / etc.) needs the canonical (unsuffixed) name view
     * built via {@link PopulationEvaluator#buildExpressionMap}. Single-group callers pass the
     * same map for both arguments via the convenience overload above.
     *
     * @param stratifiers           stratifier definitions for the current group
     * @param rawResults            full CQL results map; used to resolve stratifier expression names
     * @param canonicalPopulations  population results re-keyed by canonical name; used to count
     *                              per-stratum populations
     * @param stratificationData    accumulated: stratifierId → strataValue → populationType → count
     */
    public void evaluatePatientStratifiers(List<StratifierDefinition> stratifiers,
                                           Map<String, CqlExecutionResponse.ExpressionResult> rawResults,
                                           Map<String, CqlExecutionResponse.ExpressionResult> canonicalPopulations,
                                           Map<String, Map<String, Map<String, Integer>>> stratificationData) {
        for (StratifierDefinition stratifier : stratifiers) {
            String stratId = stratifier.getStratifierId();

            String strataValue = resolveStratumKey(stratifier, rawResults);
            if (strataValue == null) continue;

            // Insertion-ordered so strata come out in the order first seen, which for a
            // value stratifier is a stable, readable order (age bands, gender…).
            Map<String, Map<String, Integer>> strataMap = stratificationData
                    .computeIfAbsent(stratId, k -> new LinkedHashMap<>());
            Map<String, Integer> popCounts = strataMap
                    .computeIfAbsent(strataValue, k -> new LinkedHashMap<>());

            // Initialize population counts for this stratum if needed
            for (String popName : PopulationEvaluator.STANDARD_POPULATIONS) {
                popCounts.putIfAbsent(popName, 0);
            }

            // Add 1 for each population the patient belongs to (looked up by canonical name)
            for (String popName : PopulationEvaluator.STANDARD_POPULATIONS) {
                Integer count = populationEvaluator.extractPopulationCount(canonicalPopulations, popName);
                if (count != null && count > 0) {
                    popCounts.merge(popName, 1, Integer::sum);
                }
            }
        }
    }

    /**
     * Builds the final list of stratifier results from accumulated data.
     *
     * @param stratificationData accumulated data: stratifierId → strataValue → populationType → count
     * @param scoringType the measure's scoring type; the stratum score follows it (PAT-233 —
     *        it used to be the proportion formula for every scoring type). Proportion and ratio
     *        score from the stratum's counts, cohort reports the stratum's initial population,
     *        continuous-variable strata carry no score: observation values are not collected
     *        per stratum.
     * @return list of stratifier results with scores, strata in the order they were first seen
     */
    public List<StratifierResult> buildStratifierResults(
            Map<String, Map<String, Map<String, Integer>>> stratificationData, String scoringType) {
        List<StratifierResult> results = new ArrayList<>();

        for (Map.Entry<String, Map<String, Map<String, Integer>>> stratEntry : stratificationData.entrySet()) {
            String stratId = stratEntry.getKey();
            for (Map.Entry<String, Map<String, Integer>> strataEntry : stratEntry.getValue().entrySet()) {
                String strataValue = strataEntry.getKey();
                Map<String, Integer> popCounts = strataEntry.getValue();

                List<PopulationResult> stratPops = new ArrayList<>();
                for (Map.Entry<String, Integer> popEntry : popCounts.entrySet()) {
                    String popType = PopulationTypeConstants.cqlNameToFhirCode(popEntry.getKey());
                    stratPops.add(PopulationResult.builder()
                            .populationType(popType)
                            .populationId(popType)
                            .count(popEntry.getValue())
                            .build());
                }

                Double stratScore = stratumScore(popCounts, scoringType);

                List<MeasureEvaluationResult.StratumComponent> components = decodeComponents(strataValue);
                results.add(StratifierResult.builder()
                        .strataId(stratId)
                        .strataValue(displayValue(strataValue))
                        .components(components.isEmpty() ? null : components)
                        .populations(stratPops)
                        .measureScore(stratScore)
                        .build());
            }
        }

        return results;
    }

    private Double stratumScore(Map<String, Integer> popCounts, String scoringType) {
        String type = scoringType == null ? com.cqlplatform.model.measure.ScoringTypeConstants.PROPORTION
                : scoringType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case com.cqlplatform.model.measure.ScoringTypeConstants.COHORT ->
                    scoreCalculator.calculateCohortScore(popCounts.getOrDefault("Initial Population", 0));
            case com.cqlplatform.model.measure.ScoringTypeConstants.CONTINUOUS_VARIABLE -> null;
            default -> scoreCalculator.calculateScore(type,
                    popCounts.getOrDefault("Denominator", 0),
                    popCounts.getOrDefault("Denominator Exclusions", 0),
                    popCounts.getOrDefault("Numerator", 0));
        };
    }
}
