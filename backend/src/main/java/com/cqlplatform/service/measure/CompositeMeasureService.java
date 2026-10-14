package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.EvaluationStatusConstants;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.ComponentScore;
import com.cqlplatform.model.measure.MeasureEvaluationResult.GroupResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.PopulationResult;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.util.Stopwatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.cqlplatform.model.measure.PopulationTypeConstants.*;

/**
 * Evaluates a composite measure from its components (PAT-259, QM IG chapter 5).
 *
 * <p>Every component is evaluated through the guarded 3-arg {@link MeasureEvaluationService#evaluateMeasure}
 * (PAT-219: each component must be {@code active}), then the composite scoring method combines them:
 * <ul>
 *   <li><b>opportunity</b> — every component denominator membership is one opportunity:
 *       Σ numerators / Σ (denominators − exclusions), the same rule as a proportion group;</li>
 *   <li><b>all-or-nothing</b> — a subject is in the composite numerator only when it is in the numerator of
 *       every component whose (effective) denominator it is in; composite denominator = the union;</li>
 *   <li><b>linear</b> — subject-level linear combination: per subject, the share of its component
 *       denominators whose numerator it reached, averaged over the subjects (the IG models it as a CV measure);</li>
 *   <li><b>weighted</b> — component-level: Σ weight × component score / Σ weight, weights from
 *       {@code componentWeights} (cqfm-weight), 1 when unset. Equal weights = component-level linear combination.</li>
 * </ul>
 * all-or-nothing and linear need to know WHICH subjects each component counted, so their components are run as
 * {@code subject-list} reports; the subject ids are dropped again unless the caller asked for a subject list.
 * The composite's own report is saved under the composite's id (the components already save theirs).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompositeMeasureService {

    static final String SUMMARY_GROUP_ID = "composite-summary";

    private final MeasureDefinitionService definitionService;
    private final MeasureEvaluationService evaluationService;
    private final MeasureScoreCalculator scoreCalculator;
    /** Optional (ObjectProvider breaks the report ↔ evaluation wiring cycle); null in unit tests. */
    private final ObjectProvider<MeasureReportService> reportService;

    /** One component's evaluation plus what the composite reads from it. */
    private record ComponentEvaluation(MeasureDefinition component, MeasureEvaluationResult result, Double weight,
                                       GroupResult primaryGroup, Map<String, Integer> counts,
                                       Map<String, Set<String>> subjects) {
        boolean hasSubjects() {
            return subjects != null && subjects.containsKey(DENOMINATOR);
        }
        Set<String> effectiveDenominator() {
            Set<String> den = new LinkedHashSet<>(subjects.getOrDefault(DENOMINATOR, Set.of()));
            den.removeAll(subjects.getOrDefault(DENOMINATOR_EXCLUSION, Set.of()));
            return den;
        }
        Set<String> numerator() {
            return subjects.getOrDefault(NUMERATOR, Set.of());
        }
        Set<String> initialPopulation() {
            return subjects.getOrDefault(INITIAL_POPULATION, Set.of());
        }
    }

    public MeasureEvaluationResult evaluateComposite(MeasureDefinition composite, MeasureEvaluationRequest baseRequest) {
        // PAT-219: the composite parent never passes through MeasureEvaluationService itself (only its
        // components do), so it needs its own lifecycle gate — before any component is even looked up.
        MeasureStatusGuard.requireEvaluable(composite.getId(), composite);
        Stopwatch watch = Stopwatch.start();

        List<Long> componentIds = composite.getComponentMeasureIds();
        if (componentIds == null || componentIds.isEmpty()) {
            throw new IllegalArgumentException("Composite measure has no component measures defined");
        }
        String method = normalizeMethod(composite.getCompositeScoring());
        boolean needsSubjects = ScoringTypeConstants.COMPOSITE_ALL_OR_NOTHING.equals(method)
                || ScoringTypeConstants.COMPOSITE_LINEAR.equals(method);
        boolean callerWantsSubjects = "subject-list".equals(baseRequest.getReportType());

        List<ComponentEvaluation> evaluations = new ArrayList<>();
        for (Long componentId : componentIds) {
            MeasureDefinition component = definitionService.getById(componentId)
                    .orElseThrow(() -> new IllegalArgumentException("Component measure not found: " + componentId));

            MeasureEvaluationRequest componentRequest = new MeasureEvaluationRequest();
            componentRequest.setMeasureId(componentId.toString());
            componentRequest.setMeasureCql(component.getCqlContent());
            componentRequest.setPatientId(baseRequest.getPatientId());
            componentRequest.setSubjectType(baseRequest.getSubjectType());
            componentRequest.setPeriodStart(baseRequest.getPeriodStart());
            componentRequest.setPeriodEnd(baseRequest.getPeriodEnd());
            componentRequest.setFhirServerUrl(baseRequest.getFhirServerUrl());
            componentRequest.setConnectionId(baseRequest.getConnectionId());
            componentRequest.setReportType(needsSubjects || callerWantsSubjects ? "subject-list" : baseRequest.getReportType());

            MeasureEvaluationResult componentResult = evaluationService.evaluateMeasure(componentRequest, componentId, component);
            evaluations.add(describe(component, componentResult, weightOf(composite, componentId)));
        }

        MeasureEvaluationResult result = aggregate(composite, method, evaluations, baseRequest, callerWantsSubjects);
        persist(result, composite, baseRequest, watch.elapsedMs());
        return result;
    }

    static String normalizeMethod(String raw) {
        String method = raw == null || raw.isBlank()
                ? ScoringTypeConstants.COMPOSITE_OPPORTUNITY : raw.trim().toLowerCase(Locale.ROOT);
        if (!ScoringTypeConstants.COMPOSITE_SCORING_METHODS.contains(method)) {
            throw new IllegalArgumentException("Unknown composite scoring method: " + raw);
        }
        return method;
    }

    private static Double weightOf(MeasureDefinition composite, Long componentId) {
        if (composite.getComponentWeights() == null) return null;
        return composite.getComponentWeights().get(componentId.toString());
    }

    private static ComponentEvaluation describe(MeasureDefinition component, MeasureEvaluationResult result, Double weight) {
        GroupResult primary = result.getGroups() != null && !result.getGroups().isEmpty() ? result.getGroups().get(0) : null;
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Set<String>> subjects = null;
        if (primary != null && primary.getPopulations() != null) {
            for (PopulationResult pop : primary.getPopulations()) {
                if (pop.getPopulationType() == null) continue;
                counts.put(pop.getPopulationType(), pop.getCount() != null ? pop.getCount() : 0);
                if (pop.getSubjectIds() != null) {
                    if (subjects == null) subjects = new LinkedHashMap<>();
                    subjects.put(pop.getPopulationType(), new LinkedHashSet<>(pop.getSubjectIds()));
                }
            }
        }
        return new ComponentEvaluation(component, result, weight, primary, counts, subjects);
    }

    private MeasureEvaluationResult aggregate(MeasureDefinition composite, String method,
                                              List<ComponentEvaluation> evaluations,
                                              MeasureEvaluationRequest baseRequest, boolean callerWantsSubjects) {
        List<String> warnings = new ArrayList<>();
        List<ComponentScore> componentScores = new ArrayList<>();
        for (ComponentEvaluation ev : evaluations) {
            componentScores.add(ComponentScore.builder()
                    .measureId(ev.component().getId())
                    .measureName(ev.component().getTitle() != null ? ev.component().getTitle() : ev.component().getName())
                    .version(ev.component().getVersion())
                    .groupId(ev.primaryGroup() != null ? ev.primaryGroup().getGroupId() : null)
                    .weight(ScoringTypeConstants.COMPOSITE_WEIGHTED.equals(method) ? (ev.weight() != null ? ev.weight() : 1.0) : null)
                    .score(ev.primaryGroup() != null ? ev.primaryGroup().getMeasureScore() : null)
                    .initialPopulation(ev.counts().get(INITIAL_POPULATION))
                    .denominator(ev.counts().get(DENOMINATOR))
                    .denominatorExclusions(ev.counts().get(DENOMINATOR_EXCLUSION))
                    .numerator(ev.counts().get(NUMERATOR))
                    .status(ev.result().getStatus())
                    .build());
            if ("error".equals(ev.result().getStatus())) {
                warnings.add("Component '" + ev.component().getName() + "' failed: "
                        + (ev.result().getErrorMessage() != null ? ev.result().getErrorMessage() : "evaluation error"));
            }
        }

        Double score;
        List<PopulationResult> summaryPopulations;
        switch (method) {
            case ScoringTypeConstants.COMPOSITE_ALL_OR_NOTHING -> {
                if (!allHaveSubjects(evaluations, warnings, method)) {
                    score = null;
                    summaryPopulations = summedPopulations(evaluations, null);
                } else {
                    Set<String> ip = new TreeSet<>();
                    Set<String> den = new TreeSet<>();
                    for (ComponentEvaluation ev : evaluations) {
                        ip.addAll(ev.initialPopulation());
                        den.addAll(ev.effectiveDenominator());
                    }
                    Set<String> num = new TreeSet<>();
                    for (String subject : den) {
                        boolean allMet = true;
                        for (ComponentEvaluation ev : evaluations) {
                            if (ev.effectiveDenominator().contains(subject) && !ev.numerator().contains(subject)) {
                                allMet = false;
                                break;
                            }
                        }
                        if (allMet) num.add(subject);
                    }
                    score = den.isEmpty() ? null : (double) num.size() / den.size() * 100;
                    summaryPopulations = List.of(
                            population(INITIAL_POPULATION, ip, callerWantsSubjects),
                            population(DENOMINATOR, den, callerWantsSubjects),
                            population(NUMERATOR, num, callerWantsSubjects));
                }
            }
            case ScoringTypeConstants.COMPOSITE_LINEAR -> {
                if (!allHaveSubjects(evaluations, warnings, method)) {
                    score = null;
                    summaryPopulations = summedPopulations(evaluations, null);
                } else {
                    Set<String> ip = new TreeSet<>();
                    Set<String> den = new TreeSet<>();
                    for (ComponentEvaluation ev : evaluations) {
                        ip.addAll(ev.initialPopulation());
                        den.addAll(ev.effectiveDenominator());
                    }
                    double sum = 0;
                    for (String subject : den) {
                        int eligible = 0;
                        int met = 0;
                        for (ComponentEvaluation ev : evaluations) {
                            if (ev.effectiveDenominator().contains(subject)) {
                                eligible++;
                                if (ev.numerator().contains(subject)) met++;
                            }
                        }
                        if (eligible > 0) sum += (double) met / eligible;
                    }
                    score = den.isEmpty() ? null : sum / den.size() * 100;
                    summaryPopulations = List.of(
                            population(INITIAL_POPULATION, ip, callerWantsSubjects),
                            population(DENOMINATOR, den, callerWantsSubjects));
                }
            }
            case ScoringTypeConstants.COMPOSITE_WEIGHTED -> {
                double weighted = 0;
                double weightSum = 0;
                for (ComponentEvaluation ev : evaluations) {
                    Double componentScore = ev.primaryGroup() != null ? ev.primaryGroup().getMeasureScore() : null;
                    if (componentScore == null) continue;
                    double w = ev.weight() != null ? ev.weight() : 1.0;
                    weighted += w * componentScore;
                    weightSum += w;
                }
                score = weightSum > 0 ? weighted / weightSum : null;
                summaryPopulations = summedPopulations(evaluations, null);
            }
            default -> { // opportunity
                int num = 0;
                int den = 0;
                int excl = 0;
                for (ComponentEvaluation ev : evaluations) {
                    num += ev.counts().getOrDefault(NUMERATOR, 0);
                    den += ev.counts().getOrDefault(DENOMINATOR, 0);
                    excl += ev.counts().getOrDefault(DENOMINATOR_EXCLUSION, 0);
                }
                score = scoreCalculator.calculateProportionScore(den, excl, num);
                summaryPopulations = summedPopulations(evaluations, null);
            }
        }

        GroupResult summary = GroupResult.builder()
                .groupId(SUMMARY_GROUP_ID)
                .description("Composite measure (" + method + " scoring) - " + evaluations.size() + " components")
                .populationBasis("boolean")
                .populations(summaryPopulations)
                .measureScore(score)
                .measureScoreUnit("percentage")
                .build();

        List<GroupResult> groups = new ArrayList<>();
        groups.add(summary);
        for (ComponentEvaluation ev : evaluations) {
            if (ev.result().getGroups() == null) continue;
            String prefix = "component-" + ev.component().getId();
            for (GroupResult group : ev.result().getGroups()) {
                groups.add(GroupResult.builder()
                        .groupId(prefix + ":" + group.getGroupId())
                        .description(componentLabel(ev.component()) + (group.getDescription() != null ? " — " + group.getDescription() : ""))
                        .populationBasis(group.getPopulationBasis())
                        .populations(callerWantsSubjects ? group.getPopulations() : withoutSubjects(group.getPopulations()))
                        .measureScore(group.getMeasureScore())
                        .measureScoreUnit(group.getMeasureScoreUnit())
                        .stratifiers(group.getStratifiers())
                        .totalPatients(group.getTotalPatients())
                        .observationStatistics(group.getObservationStatistics())
                        .build());
            }
        }

        boolean anyError = evaluations.stream().anyMatch(ev -> "error".equals(ev.result().getStatus()));
        MeasureEvaluationResult first = evaluations.isEmpty() ? null : evaluations.get(0).result();
        return MeasureEvaluationResult.builder()
                .measureId(composite.getId() != null ? composite.getId().toString() : composite.getName())
                .measureName(composite.getName())
                .status(anyError ? "error" : EvaluationStatusConstants.COMPLETE)
                .errorMessage(anyError ? String.join("; ", warnings) : null)
                .periodStart(baseRequest.getPeriodStart() != null ? baseRequest.getPeriodStart() : first != null ? first.getPeriodStart() : null)
                .periodEnd(baseRequest.getPeriodEnd() != null ? baseRequest.getPeriodEnd() : first != null ? first.getPeriodEnd() : null)
                .reportType(baseRequest.getReportType() != null ? baseRequest.getReportType() : "summary")
                .groups(groups)
                .componentScores(componentScores)
                .warnings(warnings.isEmpty() ? null : warnings)
                .build();
    }

    private static boolean allHaveSubjects(List<ComponentEvaluation> evaluations, List<String> warnings, String method) {
        List<String> missing = new ArrayList<>();
        for (ComponentEvaluation ev : evaluations) {
            if (!ev.hasSubjects()) missing.add(ev.component().getName());
        }
        if (missing.isEmpty()) return true;
        warnings.add(method + " scoring needs the subject list of every component; none came back for "
                + String.join(", ", missing) + " — the composite score is not computed");
        return false;
    }

    /** Σ over the components: the summary populations of opportunity / weighted scoring (opportunities, not people). */
    private static List<PopulationResult> summedPopulations(List<ComponentEvaluation> evaluations, Set<String> ignored) {
        int ip = 0;
        int den = 0;
        int excl = 0;
        int num = 0;
        for (ComponentEvaluation ev : evaluations) {
            ip += ev.counts().getOrDefault(INITIAL_POPULATION, 0);
            den += ev.counts().getOrDefault(DENOMINATOR, 0);
            excl += ev.counts().getOrDefault(DENOMINATOR_EXCLUSION, 0);
            num += ev.counts().getOrDefault(NUMERATOR, 0);
        }
        return List.of(
                PopulationResult.builder().populationType(INITIAL_POPULATION).populationId(INITIAL_POPULATION).count(ip).build(),
                PopulationResult.builder().populationType(DENOMINATOR).populationId(DENOMINATOR).count(den).build(),
                PopulationResult.builder().populationType(DENOMINATOR_EXCLUSION).populationId(DENOMINATOR_EXCLUSION).count(excl).build(),
                PopulationResult.builder().populationType(NUMERATOR).populationId(NUMERATOR).count(num).build());
    }

    private static PopulationResult population(String type, Set<String> subjects, boolean withIds) {
        return PopulationResult.builder()
                .populationType(type)
                .populationId(type)
                .count(subjects.size())
                .subjectIds(withIds ? new ArrayList<>(subjects) : null)
                .build();
    }

    private static List<PopulationResult> withoutSubjects(List<PopulationResult> populations) {
        if (populations == null) return null;
        List<PopulationResult> out = new ArrayList<>();
        for (PopulationResult p : populations) {
            out.add(p.getSubjectIds() == null ? p : PopulationResult.builder()
                    .populationType(p.getPopulationType()).populationId(p.getPopulationId()).count(p.getCount()).build());
        }
        return out;
    }

    private static String componentLabel(MeasureDefinition component) {
        String name = component.getTitle() != null ? component.getTitle() : component.getName();
        return component.getVersion() != null ? name + " v" + component.getVersion() : name;
    }

    private void persist(MeasureEvaluationResult result, MeasureDefinition composite, MeasureEvaluationRequest request, long durationMs) {
        MeasureReportService reports = reportService != null ? reportService.getIfAvailable() : null;
        if (reports == null || composite.getId() == null) return;
        try {
            reports.saveReport(result, composite.getId(), request.getFhirServerUrl(), null, durationMs);
        } catch (Exception e) {
            log.warn("Failed to save the composite measure report for {}, the result is still valid", composite.getId(), e);
        }
    }
}
