package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.PopulationMembershipTrace;
import com.cqlplatform.model.measure.PopulationMembershipTrace.GroupTrace;
import com.cqlplatform.model.measure.PopulationMembershipTrace.PopulationTraceEntry;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.model.measure.TestCaseExpectedValues;
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

    /** The one member a patient-based population contributes: the patient. */
    static final String PATIENT_KEY = "patient";

    /** PAT-243 — true for a resource-type basis (Encounter, Procedure, …); false for Boolean / unset. */
    public static boolean isEpisodeBasis(String populationBasis) {
        return populationBasis != null && !populationBasis.isBlank() && !"boolean".equalsIgnoreCase(populationBasis);
    }

    /**
     * PAT-243 — what one patient contributes to one population group.
     *
     * @param counts            effective member count per canonical population name ("Initial
     *                          Population", "Denominator", …): the patient (0 / 1) for a
     *                          patient-based group, episodes for an episode-based one
     * @param observations      continuous-variable observation values of the effective Measure
     *                          Population members (empty for other scoring types)
     * @param episodeBased      true when this patient's members were counted as episodes
     * @param fellBackToPatient true when the group is episode-based but the Initial Population
     *                          did not return an identifiable episode list, so the patient was
     *                          counted as one member (the pre-PAT-243 behaviour)
     */
    public record PatientContribution(Map<String, Integer> counts, List<Double> observations,
                                      boolean episodeBased, boolean fellBackToPatient) {}

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
     * <p>Patient-based (basis Boolean): the hierarchy of {@link #contribute} with the patient as
     * the only possible member.
     *
     * @param counts  running population counts (mutated in place)
     * @param results CQL expression results for one patient
     */
    public void aggregatePatientResults(Map<String, Integer> counts,
                                        Map<String, CqlExecutionResponse.ExpressionResult> results) {
        addCounts(counts, contribute(ScoringTypeConstants.PROPORTION, null, results, results, null).counts());
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
        addCounts(counts, contribute(ScoringTypeConstants.RATIO, null, results, results, null).counts());
    }

    private boolean isPopulationTrue(Map<String, CqlExecutionResponse.ExpressionResult> results,
                                     String populationName) {
        Integer count = extractPopulationCount(results, populationName);
        return count != null && count > 0;
    }

    /** Adds a patient's contribution to the running counts (only the keys the running map knows). */
    private static void addCounts(Map<String, Integer> counts, Map<String, Integer> contribution) {
        for (Map.Entry<String, Integer> entry : contribution.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                counts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
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
        PatientContribution contribution = contribute(ScoringTypeConstants.CONTINUOUS_VARIABLE, null,
                populationResults, allResults, observationExprNames);
        addCounts(counts, contribution.counts());
        observationValues.addAll(contribution.observations());
    }

    /**
     * PAT-243 — one patient's contribution to one group of the measure, basis-aware: the group's
     * canonical population view is built here and the group's own
     * {@link GroupDefinition#getPopulationBasis() population basis} decides whether episodes or
     * the patient are counted. The production aggregation and the test case runner both go
     * through this, so a passing test means "the report counts this patient the same way".
     */
    public PatientContribution contributeToGroup(String scoringType, List<GroupDefinition> groupDefs,
                                                 GroupDefinition group,
                                                 Map<String, CqlExecutionResponse.ExpressionResult> allResults) {
        Map<String, CqlExecutionResponse.ExpressionResult> canonical = buildExpressionMap(group, allResults);
        List<String> observationNames = ScoringTypeConstants.CONTINUOUS_VARIABLE.equals(scoringType)
                ? observationExpressionNames(groupDefs, group) : null;
        return contribute(scoringType, group.getPopulationBasis(), canonical, allResults, observationNames);
    }

    /**
     * PAT-243 — the population hierarchy of a scoring type applied to one patient's results, as
     * set algebra over the members of the Initial Population.
     *
     * <p>Patient-based (basis Boolean / unset): the Initial Population's only possible member is
     * the patient; every other define is true (keeps the parent's members) or false (keeps none)
     * — exactly the Boolean hierarchy the evaluator always applied.
     *
     * <p>Episode-based (basis Encounter, Procedure, …): the Initial Population define returns the
     * list of episodes ({@link EpisodeKeys}); a child population that returns a list keeps the
     * parent's episodes that are also in its own list, while a child that returns a Boolean
     * keeps all (true) or none (false) of the parent's episodes — so a patient-level criterion
     * still works inside an episode-based measure. Counts are episode counts. When the Initial
     * Population does not return an identifiable episode list (a Boolean, or resources without
     * ids) the patient is counted as one member and {@link PatientContribution#fellBackToPatient()}
     * says so; the caller surfaces that as a warning.
     *
     * <p>Continuous-variable observations: the episode-based wrapper
     * ({@code ("Measure Population") MP return "Measure Observation"(MP)}) yields one value per
     * Measure Population episode in list order, so values are matched to episodes by position and
     * only those of the effective Measure Population (minus exclusions) are kept. When the two
     * lists do not line up, every value counts (the pre-PAT-243 behaviour).
     */
    public PatientContribution contribute(String scoringType, String populationBasis,
                                          Map<String, CqlExecutionResponse.ExpressionResult> canonical,
                                          Map<String, CqlExecutionResponse.ExpressionResult> allResults,
                                          List<String> observationExprNames) {
        boolean isCv = ScoringTypeConstants.CONTINUOUS_VARIABLE.equals(scoringType);
        boolean isRatio = ScoringTypeConstants.RATIO.equals(scoringType);
        boolean episode = isEpisodeBasis(populationBasis);
        boolean fellBack = false;

        CqlExecutionResponse.ExpressionResult ipResult = canonical.get("Initial Population");
        List<String> ipKeys = episode && ipResult != null ? EpisodeKeys.of(ipResult.getValue()) : null;
        Set<String> ip;
        if (episode && ipKeys != null) {
            ip = new LinkedHashSet<>(ipKeys);
        } else {
            if (episode && ipResult != null && ipResult.getValue() != null) fellBack = true;
            episode = false;
            ip = isPopulationTrue(canonical, "Initial Population") ? Set.of(PATIENT_KEY) : Set.of();
        }

        Map<String, Integer> counts = isCv ? initializeCvPopulationCounts() : initializePopulationCounts();
        List<Double> observations = new ArrayList<>();
        counts.put("Initial Population", ip.size());

        if (isCv) {
            Set<String> mp = and(ip, members(canonical, "Measure Population", ip, episode));
            Set<String> mpExcluded = and(mp, members(canonical, "Measure Population Exclusion", ip, episode));
            Set<String> effectiveMp = minus(mp, mpExcluded);
            counts.put("Measure Population", mp.size());
            counts.put("Measure Population Exclusion", mpExcluded.size());
            if (!effectiveMp.isEmpty()) {
                observations.addAll(cvObservations(canonical, allResults, observationExprNames, episode, effectiveMp));
            }
        } else {
            Set<String> denom = and(ip, members(canonical, "Denominator", ip, episode));
            Set<String> denomExcluded = and(denom, members(canonical, "Denominator Exclusions", ip, episode));
            Set<String> effectiveDenom = minus(denom, denomExcluded);
            // Ratio: Numer is gated by IP only, NOT by Denom (the key difference from proportion)
            Set<String> numer = and(isRatio ? ip : effectiveDenom, members(canonical, "Numerator", ip, episode));
            Set<String> numerExcluded = and(numer, members(canonical, "Numerator Exclusions", ip, episode));
            Set<String> effectiveNumer = minus(numer, numerExcluded);
            counts.put("Denominator", denom.size());
            counts.put("Denominator Exclusions", denomExcluded.size());
            counts.put("Numerator", effectiveNumer.size());
            counts.put("Numerator Exclusions", numerExcluded.size());
            if (!isRatio) {
                Set<String> denomException = and(minus(effectiveDenom, effectiveNumer),
                        members(canonical, "Denominator Exceptions", ip, episode));
                counts.put("Denominator Exceptions", denomException.size());
            }
        }
        return new PatientContribution(counts, observations, episode, fellBack);
    }

    /**
     * The members a population define keeps of the Initial Population's members: its own episode
     * list (episode mode and the define returned one), else all of them when the define is truthy,
     * none when it is false / null / absent.
     */
    private Set<String> members(Map<String, CqlExecutionResponse.ExpressionResult> canonical, String populationName,
                                Set<String> universe, boolean episode) {
        CqlExecutionResponse.ExpressionResult result = canonical.get(populationName);
        if (result == null || result.getValue() == null) return Set.of();
        if (episode) {
            List<String> keys = EpisodeKeys.of(result.getValue());
            if (keys != null) return and(universe, new LinkedHashSet<>(keys));
        }
        return isPopulationTrue(canonical, populationName) ? new LinkedHashSet<>(universe) : Set.of();
    }

    private static Set<String> and(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.retainAll(b);
        return out;
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    private List<Double> cvObservations(Map<String, CqlExecutionResponse.ExpressionResult> canonical,
                                        Map<String, CqlExecutionResponse.ExpressionResult> allResults,
                                        List<String> observationExprNames, boolean episode, Set<String> effectiveMp) {
        List<Double> values;
        if (observationExprNames == null || observationExprNames.isEmpty()) {
            // Legacy fallback: try episode-based list then patient-based scalar.
            values = extractObservationValues(allResults, OBSERVATION_VALUES_EXPR);
            if (values.isEmpty()) {
                values = extractObservationValues(allResults, OBSERVATION_VALUE_EXPR);
            }
        } else {
            // Per-group: each named observation contributes its values.
            values = new ArrayList<>();
            for (String exprName : observationExprNames) {
                values.addAll(extractObservationValues(allResults, exprName));
            }
        }
        if (!episode) return values;
        CqlExecutionResponse.ExpressionResult mpResult = canonical.get("Measure Population");
        List<String> mpKeys = mpResult == null ? null : EpisodeKeys.of(mpResult.getValue());
        if (mpKeys == null || mpKeys.size() != values.size()) return values;
        List<Double> kept = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (effectiveMp.contains(mpKeys.get(i))) kept.add(values.get(i));
        }
        return kept;
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
     * CQL define names that carry one group's continuous-variable observation values.
     *
     * <p>{@code EcqmCqlBuilder.appendObservationWrapper} emits ONE wrapper define per group:
     * {@code "Measure Observation Values{suffix}"} (episode-based) or
     * {@code "Measure Observation Value{suffix}"} (patient-based), where the suffix is
     * {@code " N"} (1-indexed) for multi-group measures and empty for a single group.
     * {@code ObservationDefinition.criteriaExpression} holds the FUNCTION name, which never
     * surfaces as a standalone result, so the wrapper has to be looked up instead (issue #539).
     *
     * @return the names to try, or {@code null} when the group declares no observations —
     *         {@link #aggregateCvPatientResults} then falls back to the unsuffixed defines
     */
    public List<String> observationExpressionNames(List<GroupDefinition> groupDefs, GroupDefinition group) {
        if (group == null || group.getObservations() == null || group.getObservations().isEmpty()) {
            return null;
        }
        int groupIdx = groupDefs != null ? groupDefs.indexOf(group) : -1;
        String suffix = groupDefs != null && groupDefs.size() > 1 && groupIdx >= 0 ? " " + (groupIdx + 1) : "";
        return List.of(OBSERVATION_VALUES_EXPR + suffix, OBSERVATION_VALUE_EXPR + suffix);
    }

    /**
     * What ONE patient contributes to one population group, computed with exactly the rules the
     * production evaluation applies per patient (PAT-228). The test case runner compares these
     * values with the expectation, so a passing test means "the report counts this patient the
     * same way" — it deliberately does not re-implement the population hierarchy.
     *
     * <ul>
     *   <li>populations — effective counts after the scoring type's hierarchy (proportion /
     *       cohort, ratio, or continuous-variable), keyed by the group's own
     *       {@link PopulationDefinition#getPopulationType()}. Patient-based: 0 or 1.</li>
     *   <li>observations — continuous-variable: this group's wrapper define, only when the
     *       patient is in the effective Measure Population. Other scoring types: the unsuffixed
     *       observation defines, which production collects once per patient and not per group —
     *       so they are reported on the first group only.</li>
     * </ul>
     *
     * @param groupDefs  all groups of the measure (the index decides the multi-group suffix)
     * @param allResults the patient's full CQL results map
     */
    public TestCaseExpectedValues.GroupValues evaluateSinglePatient(
            String scoringType, List<GroupDefinition> groupDefs, GroupDefinition group,
            Map<String, CqlExecutionResponse.ExpressionResult> allResults) {
        boolean isCv = ScoringTypeConstants.CONTINUOUS_VARIABLE.equals(scoringType);

        // PAT-243: basis-aware — an episode-based group yields episode counts here.
        PatientContribution contribution = contributeToGroup(scoringType, groupDefs, group, allResults);
        Map<String, Integer> counts = contribution.counts();
        List<Double> observations = new ArrayList<>(contribution.observations());

        if (!isCv) {
            boolean firstGroup = groupDefs == null || groupDefs.isEmpty() || groupDefs.indexOf(group) <= 0;
            if (firstGroup) {
                List<Double> values = extractObservationValues(allResults, OBSERVATION_VALUE_EXPR);
                if (values.isEmpty()) {
                    values = extractObservationValues(allResults, OBSERVATION_VALUES_EXPR);
                }
                observations.addAll(values);
            }
        }

        Map<String, Integer> byType = new LinkedHashMap<>();
        if (group.getPopulations() != null) {
            for (PopulationDefinition pop : group.getPopulations()) {
                if (pop.getPopulationType() == null) continue;
                byType.put(pop.getPopulationType(), counts.getOrDefault(toDisplayName(pop.getPopulationType()), 0));
            }
        }
        return TestCaseExpectedValues.GroupValues.builder()
                .groupId(group.getGroupId())
                .populations(byType)
                .observations(observations)
                .build();
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
                GroupTrace trace = buildGroupTrace(group.getGroupId(), scoringType,
                        group.getDescription(), buildExpressionMap(group, results));
                applyEpisodeCounts(trace, contributeToGroup(scoringType, groups, group, results), group.getPopulationBasis());
                groupTraces.add(trace);
            }
        }

        return PopulationMembershipTrace.builder().groups(groupTraces).build();
    }

    /**
     * PAT-243 — an episode-based group's trace carries the member (episode) count per population,
     * and "effective" means at least one episode; the Boolean raw / effective columns stay as the
     * author's define returned them.
     */
    private static void applyEpisodeCounts(GroupTrace trace, PatientContribution contribution, String basis) {
        trace.setPopulationBasis(basis);
        if (!contribution.episodeBased() || trace.getPopulations() == null) return;
        for (PopulationTraceEntry entry : trace.getPopulations()) {
            Integer count = contribution.counts().get(entry.getDisplayName());
            if (count == null) continue;
            entry.setMemberCount(count);
            entry.setEffectiveResult(count > 0);
        }
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
