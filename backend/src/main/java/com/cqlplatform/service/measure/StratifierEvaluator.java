package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
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
            String expression = stratifier.getCriteriaExpression();

            CqlExecutionResponse.ExpressionResult stratResult = rawResults.get(expression);
            if (stratResult == null) continue;

            String strataValue = String.valueOf(stratResult.getValue());
            if ("null".equals(strataValue)) continue;

            Map<String, Map<String, Integer>> strataMap = stratificationData
                    .computeIfAbsent(stratId, k -> new HashMap<>());
            Map<String, Integer> popCounts = strataMap
                    .computeIfAbsent(strataValue, k -> new HashMap<>());

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
     * @return list of stratifier results with scores
     */
    public List<StratifierResult> buildStratifierResults(
            Map<String, Map<String, Map<String, Integer>>> stratificationData) {
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

                Integer denom = popCounts.getOrDefault("Denominator", 0);
                Integer denomExcl = popCounts.getOrDefault("Denominator Exclusions", 0);
                Integer numer = popCounts.getOrDefault("Numerator", 0);
                Double stratScore = scoreCalculator.calculateProportionScore(denom, denomExcl, numer);

                results.add(StratifierResult.builder()
                        .strataId(stratId)
                        .strataValue(strataValue)
                        .populations(stratPops)
                        .measureScore(stratScore)
                        .build());
            }
        }

        return results;
    }
}
