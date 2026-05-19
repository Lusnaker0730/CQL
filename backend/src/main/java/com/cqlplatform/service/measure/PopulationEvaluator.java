package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.PopulationMembershipTrace;
import com.cqlplatform.model.measure.PopulationMembershipTrace.GroupTrace;
import com.cqlplatform.model.measure.PopulationMembershipTrace.PopulationTraceEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Evaluates and aggregates population counts from CQL execution results.
 * Pure logic — no FHIR or database dependencies.
 */
@Component
@Slf4j
public class PopulationEvaluator {

    /** Standard population names recognized by eCQM evaluation. */
    public static final List<String> STANDARD_POPULATIONS = List.of(
            "Initial Population",
            "Denominator",
            "Denominator Exclusions",
            "Denominator Exceptions",
            "Numerator",
            "Numerator Exclusions"
    );

    /** Continuous-variable population names. */
    public static final List<String> CV_POPULATIONS = List.of(
            "Initial Population",
            "Measure Population",
            "Measure Population Exclusion"
    );

    private static final String OBSERVATION_VALUES_EXPR = "Measure Observation Values";
    private static final String OBSERVATION_VALUE_EXPR = "Measure Observation Value";

    /**
     * Creates an initialized population count map with all standard populations set to 0.
     */
    public Map<String, Integer> initializePopulationCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String pop : STANDARD_POPULATIONS) {
            counts.put(pop, 0);
        }
        return counts;
    }

    /**
     * Aggregates a single patient's CQL results into the running population counts.
     * Enforces HL7 proportion measure population hierarchy:
     * <ul>
     *   <li>Denominator is only counted if Initial Population is true</li>
     *   <li>Denominator Exclusions only if Denominator is true</li>
     *   <li>Numerator is only counted if Denominator is true AND not excluded</li>
     *   <li>Numerator Exclusions only if Numerator is true</li>
     *   <li>Denominator Exceptions only if Denominator is true but Numerator is false</li>
     * </ul>
     *
     * @param counts  running population counts (mutated in place)
     * @param results CQL expression results for one patient
     */
    public void aggregatePatientResults(Map<String, Integer> counts,
                                        Map<String, CqlExecutionResponse.ExpressionResult> results) {
        // Evaluate each population for this patient
        boolean inInitPop = isPopulationTrue(results, "Initial Population");
        boolean inDenom = inInitPop && isPopulationTrue(results, "Denominator");
        boolean denomExcluded = inDenom && isPopulationTrue(results, "Denominator Exclusions");
        boolean effectiveDenom = inDenom && !denomExcluded;
        boolean inNumer = effectiveDenom && isPopulationTrue(results, "Numerator");
        boolean numerExcluded = inNumer && isPopulationTrue(results, "Numerator Exclusions");
        boolean effectiveNumer = inNumer && !numerExcluded;
        boolean denomException = effectiveDenom && !effectiveNumer
                && isPopulationTrue(results, "Denominator Exceptions");

        if (inInitPop) increment(counts, "Initial Population");
        if (inDenom) increment(counts, "Denominator");
        if (denomExcluded) increment(counts, "Denominator Exclusions");
        if (effectiveNumer) increment(counts, "Numerator");
        if (numerExcluded) increment(counts, "Numerator Exclusions");
        if (denomException) increment(counts, "Denominator Exceptions");
    }

    /**
     * Aggregates a single patient's CQL results for RATIO measures. Ratio differs from
     * proportion in one key way per FHIR MeasureReport R4 spec: Numerator is NOT gated
     * by Denominator. Both populations are independent filters within Initial Population.
     *
     * <p>Canonical example: "encounters per patient". Denom = adult patients,
     * Numer = any encounter in period. Numer must be able to count events for patients
     * who aren't in Denom, and vice versa — a single patient can contribute multiple
     * encounters to Numer without appearing in Denom at all.
     *
     * <p>Denominator Exceptions don't exist for ratio (proportion-only concept per FHIR
     * spec) — we intentionally don't check for them here. If a ratio measure includes
     * them in its populationGroups definition they're silently ignored.
     */
    public void aggregateRatioPatientResults(Map<String, Integer> counts,
                                             Map<String, CqlExecutionResponse.ExpressionResult> results) {
        boolean inInitPop = isPopulationTrue(results, "Initial Population");
        boolean inDenom = inInitPop && isPopulationTrue(results, "Denominator");
        boolean denomExcluded = inDenom && isPopulationTrue(results, "Denominator Exclusions");
        // Ratio: Numer is gated by IP only, NOT by Denom (the key difference from proportion)
        boolean inNumer = inInitPop && isPopulationTrue(results, "Numerator");
        boolean numerExcluded = inNumer && isPopulationTrue(results, "Numerator Exclusions");
        boolean effectiveNumer = inNumer && !numerExcluded;

        if (inInitPop) increment(counts, "Initial Population");
        if (inDenom) increment(counts, "Denominator");
        if (denomExcluded) increment(counts, "Denominator Exclusions");
        if (effectiveNumer) increment(counts, "Numerator");
        if (numerExcluded) increment(counts, "Numerator Exclusions");
    }

    private boolean isPopulationTrue(Map<String, CqlExecutionResponse.ExpressionResult> results,
                                     String populationName) {
        Integer count = extractPopulationCount(results, populationName);
        return count != null && count > 0;
    }

    private void increment(Map<String, Integer> counts, String key) {
        counts.computeIfPresent(key, (k, v) -> v + 1);
    }

    /**
     * Creates an initialized population count map for continuous-variable measures.
     */
    public Map<String, Integer> initializeCvPopulationCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String pop : CV_POPULATIONS) {
            counts.put(pop, 0);
        }
        return counts;
    }

    /**
     * Aggregates a single patient's CQL results for continuous-variable measures.
     * Collects observation values into the shared accumulator list.
     *
     * <p>Convenience overload: uses the same map for population lookup and
     * observation extraction. Suitable for the legacy single-group path where
     * raw CQL results are passed directly.
     */
    public void aggregateCvPatientResults(Map<String, Integer> counts,
                                           Map<String, CqlExecutionResponse.ExpressionResult> results,
                                           List<Double> observationValues) {
        aggregateCvPatientResults(counts, results, results, observationValues);
    }

    /**
     * Aggregates a single patient's CQL results for continuous-variable measures
     * with separate maps for population lookup and observation extraction.
     *
     * <p>The per-group eCQM path canonicalizes suffixed population define names
     * ({@code "Initial Population 1"} → {@code "Initial Population"}) into a
     * filtered map, but that map intentionally only contains the
     * {@code PopulationDefinition} entries. {@code "Measure Observation Value"}
     * / {@code "Measure Observation Values"} are top-level CQL defines (not
     * populations), so they exist only in the raw {@code allResults} map.
     * Passing the same canonical map for both lookups caused the observation
     * extraction to find nothing → CV measureScore stuck at {@code null}
     * (BUG-474 follow-up regression — surfaced by smoke scenarios 03 / 05–09).
     *
     * <p>Delegates to the 5-arg variant with {@code null} observation names,
     * which falls back to the canonical unsuffixed defines.
     */
    public void aggregateCvPatientResults(Map<String, Integer> counts,
                                           Map<String, CqlExecutionResponse.ExpressionResult> populationResults,
                                           Map<String, CqlExecutionResponse.ExpressionResult> allResults,
                                           List<Double> observationValues) {
        aggregateCvPatientResults(counts, populationResults, allResults, null, observationValues);
    }

    /**
     * Multi-group CV aggregation — caller supplies the per-group observation
     * define names (typically from {@code GroupDefinition.observations[*]
     * .criteriaExpression}, e.g. {@code "Measure Observation Value 1"} for
     * group 1). When {@code observationExprNames} is {@code null} or empty,
     * the function falls back to the canonical unsuffixed names — preserving
     * the legacy single-group behavior.
     *
     * <p>Issue #539: multi-group CV measures were silently broken because
     * {@code EcqmCqlBuilder} emits suffixed observation defines per group but
     * the aggregator was hard-coded to look up the unsuffixed canonical names.
     */
    public void aggregateCvPatientResults(Map<String, Integer> counts,
                                           Map<String, CqlExecutionResponse.ExpressionResult> populationResults,
                                           Map<String, CqlExecutionResponse.ExpressionResult> allResults,
                                           List<String> observationExprNames,
                                           List<Double> observationValues) {
        boolean inInitPop = isPopulationTrue(populationResults, "Initial Population");
        boolean inMeasurePop = inInitPop && isPopulationTrue(populationResults, "Measure Population");
        boolean excluded = inMeasurePop && isPopulationTrue(populationResults, "Measure Population Exclusion");
        boolean effectiveMp = inMeasurePop && !excluded;

        if (inInitPop) increment(counts, "Initial Population");
        if (inMeasurePop) increment(counts, "Measure Population");
        if (excluded) increment(counts, "Measure Population Exclusion");

        if (effectiveMp) {
            if (observationExprNames == null || observationExprNames.isEmpty()) {
                // Legacy fallback: try episode-based list then patient-based scalar.
                List<Double> values = extractObservationValues(allResults, OBSERVATION_VALUES_EXPR);
                if (values.isEmpty()) {
                    values = extractObservationValues(allResults, OBSERVATION_VALUE_EXPR);
                }
                observationValues.addAll(values);
            } else {
                // Per-group: each named observation contributes its values.
                for (String exprName : observationExprNames) {
                    observationValues.addAll(extractObservationValues(allResults, exprName));
                }
            }
        }
    }

    /**
     * Extracts numeric observation values from CQL expression results for CV / ratio
     * aggregation. Handles:
     * <ul>
     *   <li>single Number — returned as a one-item Double list</li>
     *   <li>Iterable of Numbers — each mapped to Double</li>
     *   <li>Boolean TRUE — mapped to {@code 1.0}; FALSE is skipped</li>
     * </ul>
     *
     * <p>Boolean handling exists because authoring trees with boolean criteria (e.g.
     * {@code AgeRange}) produce boolean Measure Observations. For Count aggregate the
     * semantic is "count of patients whose observation returned a value" — a non-null
     * TRUE observation IS one such value. Without this, CV measures with boolean
     * criteria returned score=null regardless of data (observed in #PAT-082 scenario 03).
     * Aggregate methods that don't semantically apply to 1-valued booleans (Average /
     * Median will just be 1.0, Sum will equal Count) are still technically correct
     * and don't return null.
     */
    public List<Double> extractObservationValues(Map<String, CqlExecutionResponse.ExpressionResult> results,
                                                  String expressionName) {
        CqlExecutionResponse.ExpressionResult result = results.get(expressionName);
        if (result == null || result.getValue() == null) return List.of();

        Object value = result.getValue();
        if (value instanceof Number num) {
            return List.of(num.doubleValue());
        } else if (value instanceof Boolean bool) {
            // TRUE = observation "observed" (contributes 1.0); FALSE = not observed (skip).
            return bool ? List.of(1.0) : List.of();
        } else if (value instanceof Iterable<?> iterable) {
            List<Double> values = new ArrayList<>();
            for (Object item : iterable) {
                if (item instanceof Number num) {
                    values.add(num.doubleValue());
                } else if (item instanceof Boolean bool && bool) {
                    values.add(1.0);
                }
            }
            return values;
        }
        return List.of();
    }

    /**
     * Aggregates custom (non-standard) expressions from CQL results.
     * Accumulates numeric values, boolean true counts, and collection sizes.
     *
     * @param customExpressions running custom expression aggregation (mutated in place)
     * @param results           CQL expression results for one patient
     * @param standardNames     set of standard population names to skip
     */
    public void aggregateCustomExpressions(Map<String, Object> customExpressions,
                                           Map<String, CqlExecutionResponse.ExpressionResult> results,
                                           Set<String> standardNames) {
        if (results == null) return;
        for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : results.entrySet()) {
            String key = entry.getKey();
            if (standardNames.contains(key)) continue;

            Object value = entry.getValue().getValue();
            if (value instanceof Number) {
                int intVal = ((Number) value).intValue();
                int existing = customExpressions.containsKey(key)
                        ? ((Number) customExpressions.get(key)).intValue() : 0;
                customExpressions.put(key, existing + intVal);
            } else if (value instanceof Boolean) {
                int increment = (Boolean) value ? 1 : 0;
                int existing = customExpressions.containsKey(key)
                        ? ((Number) customExpressions.get(key)).intValue() : 0;
                customExpressions.put(key, existing + increment);
            } else if (value instanceof Collection<?> collection) {
                int existing = customExpressions.containsKey(key)
                        ? ((Number) customExpressions.get(key)).intValue() : 0;
                customExpressions.put(key, existing + collection.size());
            }
        }
    }

    // ---------- Population Membership Trace (debug mode) ----------

    /**
     * Builds a per-group trace answering "why did this patient (not) end up in the Numerator?"
     * Uses the measure's scoringType to dispatch to proportion/ratio, continuous-variable, or cohort logic.
     */
    public PopulationMembershipTrace buildTestCaseTrace(MeasureDefinition measure,
                                                        CqlExecutionResponse execResponse) {
        List<GroupTrace> groupTraces = new ArrayList<>();
        String scoringType = measure.getScoringType() != null ? measure.getScoringType() : "proportion";
        Map<String, CqlExecutionResponse.ExpressionResult> results =
                execResponse.getResults() != null ? execResponse.getResults() : Map.of();

        List<GroupDefinition> groups = measure.getGroupDefinitions() != null
                ? measure.getGroupDefinitions() : List.of();

        if (groups.isEmpty()) {
            // Fall back to a synthetic single group
            groupTraces.add(buildGroupTrace(null, scoringType, null, results));
        } else {
            for (GroupDefinition group : groups) {
                groupTraces.add(buildGroupTrace(group.getGroupId(), scoringType,
                        group.getDescription(), buildExpressionMap(group, results)));
            }
        }

        return PopulationMembershipTrace.builder().groups(groupTraces).build();
    }

    /**
     * Resolves each population's criteriaExpression in this group to its CqlExecutionResponse result.
     * Returns a canonical map keyed by population display name ("Initial Population", etc.).
     *
     * <p>Public so {@link MeasureEvaluationService} can canonicalize per-group CQL results
     * (with suffixes like "Initial Population 1" / "Initial Population 2") before calling
     * {@link #aggregatePatientResults}, which expects unsuffixed keys. Required for multi-group
     * eCQM evaluation per BUG #474.
     */
    public Map<String, CqlExecutionResponse.ExpressionResult> buildExpressionMap(
            GroupDefinition group, Map<String, CqlExecutionResponse.ExpressionResult> allResults) {
        Map<String, CqlExecutionResponse.ExpressionResult> canonical = new LinkedHashMap<>();
        if (group.getPopulations() == null) return canonical;
        for (PopulationDefinition pop : group.getPopulations()) {
            String displayName = toDisplayName(pop.getPopulationType());
            String exprName = pop.getCriteriaExpression();
            if (exprName != null && allResults.containsKey(exprName)) {
                canonical.put(displayName, allResults.get(exprName));

            }
        }
        return canonical;
    }

    /** Maps a population type (FHIR code or display name) to canonical display name. */
    private String toDisplayName(String populationType) {
        if (populationType == null) return "";
        return switch (populationType.toLowerCase(Locale.ROOT)) {
            case "initial-population", "initial population" -> "Initial Population";
            case "denominator" -> "Denominator";
            case "denominator-exclusion", "denominator exclusions" -> "Denominator Exclusions";
            case "denominator-exception", "denominator exceptions" -> "Denominator Exceptions";
            case "numerator" -> "Numerator";
            case "numerator-exclusion", "numerator exclusions" -> "Numerator Exclusions";
            case "measure-population", "measure population" -> "Measure Population";
            case "measure-population-exclusion", "measure population exclusion" -> "Measure Population Exclusion";
            default -> populationType;
        };
    }

    private GroupTrace buildGroupTrace(String groupId, String scoringType, String description,
                                       Map<String, CqlExecutionResponse.ExpressionResult> results) {
        List<PopulationTraceEntry> entries;
        switch (scoringType.toLowerCase(Locale.ROOT)) {
            case "continuous-variable" -> entries = buildCvEntries(results);
            case "cohort" -> entries = buildCohortEntries(results);
            case "ratio" -> entries = buildRatioEntries(results);
            case "proportion" -> entries = buildProportionEntries(results);
            default -> entries = buildProportionEntries(results);
        }
        return GroupTrace.builder()
                .groupId(groupId).description(description).scoringType(scoringType)
                .populations(entries).build();
    }

    private List<PopulationTraceEntry> buildProportionEntries(
            Map<String, CqlExecutionResponse.ExpressionResult> results) {
        List<PopulationTraceEntry> entries = new ArrayList<>();

        boolean ip = isPopulationTrue(results, "Initial Population");
        entries.add(populationEntry("Initial Population", "initial-population", results,
                ip, ip,
                ip ? "ip_true" : "ip_false",
                Map.of("ip", ip)));

        Boolean denomRaw = rawBool(results, "Denominator");
        boolean denom = ip && Boolean.TRUE.equals(denomRaw);
        entries.add(populationEntry("Denominator", "denominator", results,
                denomRaw, denom,
                !ip ? "denom_gatedByIpFalse" :
                        (Boolean.FALSE.equals(denomRaw) ? "denom_exprFalse" : "denom_true"),
                Map.of("ip", ip, "denom", Boolean.TRUE.equals(denomRaw))));

        Boolean denomExclRaw = rawBool(results, "Denominator Exclusions");
        boolean denomExcluded = denom && Boolean.TRUE.equals(denomExclRaw);
        if (hasPopulation(results, "Denominator Exclusions")) {
            String denomExclReason = !denom ? "denomExcl_gatedByDenomFalse"
                    : (denomExcluded ? "denom_excludedOut" : "denom_exprFalse");
            entries.add(populationEntry("Denominator Exclusions", "denominator-exclusion", results,
                    denomExclRaw, denomExcluded, denomExclReason,
                    Map.of("denom", denom, "exclusion", Boolean.TRUE.equals(denomExclRaw))));
        }

        boolean effectiveDenom = denom && !denomExcluded;

        Boolean numerRaw = rawBool(results, "Numerator");
        boolean numer = effectiveDenom && Boolean.TRUE.equals(numerRaw);
        entries.add(populationEntry("Numerator", "numerator", results,
                numerRaw, numer,
                !effectiveDenom ? "numer_gatedByDenomFalse" :
                        (Boolean.FALSE.equals(numerRaw) ? "numer_exprFalse" : "numer_true"),
                Map.of("denom", effectiveDenom, "numer", Boolean.TRUE.equals(numerRaw))));

        Boolean numerExclRaw = rawBool(results, "Numerator Exclusions");
        boolean numerExcluded = numer && Boolean.TRUE.equals(numerExclRaw);
        if (hasPopulation(results, "Numerator Exclusions")) {
            String numerExclReason = !numer ? "numerExcl_gatedByNumerFalse"
                    : (numerExcluded ? "numer_excludedOut" : "numer_exprFalse");
            entries.add(populationEntry("Numerator Exclusions", "numerator-exclusion", results,
                    numerExclRaw, numerExcluded, numerExclReason,
                    Map.of("numer", numer, "exclusion", Boolean.TRUE.equals(numerExclRaw))));
        }

        boolean effectiveNumer = numer && !numerExcluded;

        Boolean denomExceptRaw = rawBool(results, "Denominator Exceptions");
        if (hasPopulation(results, "Denominator Exceptions")) {
            boolean applied = effectiveDenom && !effectiveNumer && Boolean.TRUE.equals(denomExceptRaw);
            String reason;
            if (!effectiveDenom) reason = "denomException_gatedByDenomFalse";
            else if (effectiveNumer) reason = "denomException_gatedByNumerTrue";
            else if (applied) reason = "denomException_applied";
            else reason = "denomException_exprFalse";
            entries.add(populationEntry("Denominator Exceptions", "denominator-exception", results,
                    denomExceptRaw, applied, reason,
                    Map.of("denom", effectiveDenom, "numer", effectiveNumer,
                            "exception", Boolean.TRUE.equals(denomExceptRaw))));
        }

        return entries;
    }

    /**
     * Per-patient trace for RATIO scoring. Mirrors proportion's trace except Numerator is
     * gated by Initial Population, not Denominator — see {@link #aggregateRatioPatientResults}
     * for rationale. Also omits Denominator Exceptions (ratio doesn't have them per FHIR spec).
     */
    private List<PopulationTraceEntry> buildRatioEntries(
            Map<String, CqlExecutionResponse.ExpressionResult> results) {
        List<PopulationTraceEntry> entries = new ArrayList<>();

        boolean ip = isPopulationTrue(results, "Initial Population");
        entries.add(populationEntry("Initial Population", "initial-population", results,
                ip, ip,
                ip ? "ip_true" : "ip_false",
                Map.of("ip", ip)));

        Boolean denomRaw = rawBool(results, "Denominator");
        boolean denom = ip && Boolean.TRUE.equals(denomRaw);
        entries.add(populationEntry("Denominator", "denominator", results,
                denomRaw, denom,
                !ip ? "denom_gatedByIpFalse" :
                        (Boolean.FALSE.equals(denomRaw) ? "denom_exprFalse" : "denom_true"),
                Map.of("ip", ip, "denom", Boolean.TRUE.equals(denomRaw))));

        Boolean denomExclRaw = rawBool(results, "Denominator Exclusions");
        boolean denomExcluded = denom && Boolean.TRUE.equals(denomExclRaw);
        if (hasPopulation(results, "Denominator Exclusions")) {
            String denomExclReason = !denom ? "denomExcl_gatedByDenomFalse"
                    : (denomExcluded ? "denom_excludedOut" : "denom_exprFalse");
            entries.add(populationEntry("Denominator Exclusions", "denominator-exclusion", results,
                    denomExclRaw, denomExcluded, denomExclReason,
                    Map.of("denom", denom, "exclusion", Boolean.TRUE.equals(denomExclRaw))));
        }

        // Ratio: Numerator gated by IP, NOT by Denom.
        Boolean numerRaw = rawBool(results, "Numerator");
        boolean numer = ip && Boolean.TRUE.equals(numerRaw);
        entries.add(populationEntry("Numerator", "numerator", results,
                numerRaw, numer,
                !ip ? "numer_gatedByIpFalse" :
                        (Boolean.FALSE.equals(numerRaw) ? "numer_exprFalse" : "numer_true"),
                Map.of("ip", ip, "numer", Boolean.TRUE.equals(numerRaw))));

        Boolean numerExclRaw = rawBool(results, "Numerator Exclusions");
        boolean numerExcluded = numer && Boolean.TRUE.equals(numerExclRaw);
        if (hasPopulation(results, "Numerator Exclusions")) {
            String numerExclReason = !numer ? "numerExcl_gatedByNumerFalse"
                    : (numerExcluded ? "numer_excludedOut" : "numer_exprFalse");
            entries.add(populationEntry("Numerator Exclusions", "numerator-exclusion", results,
                    numerExclRaw, numerExcluded, numerExclReason,
                    Map.of("numer", numer, "exclusion", Boolean.TRUE.equals(numerExclRaw))));
        }

        return entries;
    }

    private List<PopulationTraceEntry> buildCvEntries(Map<String, CqlExecutionResponse.ExpressionResult> results) {
        List<PopulationTraceEntry> entries = new ArrayList<>();
        boolean ip = isPopulationTrue(results, "Initial Population");
        entries.add(populationEntry("Initial Population", "initial-population", results,
                ip, ip, ip ? "ip_true" : "ip_false", Map.of("ip", ip)));

        Boolean mpRaw = rawBool(results, "Measure Population");
        boolean mp = ip && Boolean.TRUE.equals(mpRaw);
        entries.add(populationEntry("Measure Population", "measure-population", results,
                mpRaw, mp,
                !ip ? "cv_mp_gatedByIpFalse" :
                        (Boolean.FALSE.equals(mpRaw) ? "cv_mp_exprFalse" : "cv_mp_true"),
                Map.of("ip", ip, "mp", Boolean.TRUE.equals(mpRaw))));

        Boolean exclRaw = rawBool(results, "Measure Population Exclusion");
        boolean excluded = mp && Boolean.TRUE.equals(exclRaw);
        if (hasPopulation(results, "Measure Population Exclusion")) {
            entries.add(populationEntry("Measure Population Exclusion", "measure-population-exclusion", results,
                    exclRaw, excluded,
                    excluded ? "cv_mp_excludedOut" : "cv_mp_exprFalse",
                    Map.of("mp", mp, "exclusion", Boolean.TRUE.equals(exclRaw))));
        }
        return entries;
    }

    private List<PopulationTraceEntry> buildCohortEntries(
            Map<String, CqlExecutionResponse.ExpressionResult> results) {
        boolean ip = isPopulationTrue(results, "Initial Population");
        return List.of(populationEntry("Initial Population", "initial-population", results,
                ip, ip, ip ? "ip_true" : "ip_false", Map.of("ip", ip)));
    }

    private PopulationTraceEntry populationEntry(String displayName, String fhirCode,
                                                 Map<String, CqlExecutionResponse.ExpressionResult> results,
                                                 Boolean rawResult, boolean effectiveResult,
                                                 String reasonCode, Map<String, Boolean> reasonInputs) {
        CqlExecutionResponse.ExpressionResult exprResult = results.get(displayName);
        String criteriaExpr = exprResult != null ? exprResult.getName() : null;
        return PopulationTraceEntry.builder()
                .populationType(fhirCode)
                .displayName(displayName)
                .criteriaExpression(criteriaExpr != null ? criteriaExpr : displayName)
                .rawResult(rawResult)
                .effectiveResult(effectiveResult)
                .reasonCode(reasonCode)
                .reasonInputs(reasonInputs)
                .build();
    }

    /** Returns the raw boolean interpretation or null if the expression isn't present. */
    private Boolean rawBool(Map<String, CqlExecutionResponse.ExpressionResult> results, String populationName) {
        Integer count = extractPopulationCount(results, populationName);
        return count == null ? null : count > 0;
    }

    private boolean hasPopulation(Map<String, CqlExecutionResponse.ExpressionResult> results, String name) {
        return results.containsKey(name);
    }

    public Integer extractPopulationCount(Map<String, CqlExecutionResponse.ExpressionResult> results,
                                          String populationName) {
        CqlExecutionResponse.ExpressionResult result = results.get(populationName);
        if (result == null) return null;

        Object value = result.getValue();
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof Iterable<?> iterable) {
            int count = 0;
            var iterator = iterable.iterator();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
            return count;
        }
        return null;
    }
}
