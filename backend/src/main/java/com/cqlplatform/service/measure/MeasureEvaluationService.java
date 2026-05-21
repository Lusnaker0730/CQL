package com.cqlplatform.service.measure;

import com.cqlplatform.exception.FhirServerUnavailableException;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.model.measure.MeasureEvaluationResult.*;
import com.cqlplatform.service.cql.CqlExecutionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.cqlplatform.model.measure.EvaluationStatusConstants;
import static com.cqlplatform.model.measure.PopulationTypeConstants.*;

/**
 * Orchestrates measure evaluation by delegating to focused services.
 * Flow: discover patients → execute CQL → evaluate populations → stratify → score → save.
 */
@Service
@Slf4j
public class MeasureEvaluationService {

    private final CqlExecutionService cqlExecutionService;
    private final com.cqlplatform.service.cql.CqlTranslationService cqlTranslationService;
    private final com.cqlplatform.service.fhir.FhirDataProviderService fhirDataProviderService;
    private final PatientDiscoveryService patientDiscoveryService;
    private final PopulationEvaluator populationEvaluator;
    private final StratifierEvaluator stratifierEvaluator;
    private final MeasureScoreCalculator scoreCalculator;
    private final java.util.concurrent.ExecutorService measureExecutor;

    public MeasureEvaluationService(
            CqlExecutionService cqlExecutionService,
            com.cqlplatform.service.cql.CqlTranslationService cqlTranslationService,
            com.cqlplatform.service.fhir.FhirDataProviderService fhirDataProviderService,
            PatientDiscoveryService patientDiscoveryService,
            PopulationEvaluator populationEvaluator,
            StratifierEvaluator stratifierEvaluator,
            MeasureScoreCalculator scoreCalculator,
            @org.springframework.beans.factory.annotation.Qualifier("cqlExecutionExecutor")
            java.util.concurrent.ExecutorService measureExecutor) {
        this.cqlExecutionService = cqlExecutionService;
        this.cqlTranslationService = cqlTranslationService;
        this.fhirDataProviderService = fhirDataProviderService;
        this.patientDiscoveryService = patientDiscoveryService;
        this.populationEvaluator = populationEvaluator;
        this.stratifierEvaluator = stratifierEvaluator;
        this.scoreCalculator = scoreCalculator;
        this.measureExecutor = measureExecutor;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MeasureReportService measureReportService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Timer measureEvaluationTimer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter measureEvaluationCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter measureEvaluationErrorCounter;

    @Value("${measure.reporting.default-period-start:}")
    private String defaultPeriodStart;

    @Value("${measure.reporting.default-period-end:}")
    private String defaultPeriodEnd;

    @Value("${cql.execution.measure-timeout-seconds:120}")
    private int measureTimeoutSeconds;

    /**
     * Fraction of per-patient evaluation failures (errorCount / totalPatients) at or
     * above which the whole measure run is aborted with errorResult instead of
     * returning a partial denominator. Default 1.0 preserves the previous behaviour
     * (only abort when ALL patients fail). Operators wanting stricter clinical
     * safety should lower this in production yml — e.g. 0.5 means "abort if half
     * or more of patients failed". PAT-140.
     */
    @Value("${measure.error-threshold-ratio:1.0}")
    private double errorThresholdRatio = 1.0;

    public MeasureEvaluationResult evaluateMeasure(MeasureEvaluationRequest request) {
        return evaluateMeasure(request, null, null);
    }

    public MeasureEvaluationResult evaluateMeasure(MeasureEvaluationRequest request,
                                                    Long measureDefinitionId,
                                                    MeasureDefinition measureDefinition) {
        log.info("Evaluating measure: {} for patient: {}", request.getMeasureId(), request.getPatientId());
        if (measureEvaluationCounter != null) measureEvaluationCounter.increment();
        Timer.Sample sample = measureEvaluationTimer != null ? Timer.start() : null;
        long startTime = System.currentTimeMillis();

        MeasureEvaluationContext context = buildContext(request, measureDefinitionId, measureDefinition);

        try {
            // 1. Pre-translate CQL once (MADiE pattern: translate once, reuse Java objects for all patients)
            CqlExecutionService.PreTranslatedContext preTranslated = null;
            if (context.getMeasureCql() != null) {
                long translateStart = System.currentTimeMillis();
                try {
                    log.info("Starting CQL pre-translation ({} chars)", context.getMeasureCql().length());
                    preTranslated = cqlExecutionService.translateOnce(context.getMeasureCql());
                    log.info("Pre-translated CQL in {}ms (will reuse for all patients)",
                            System.currentTimeMillis() - translateStart);
                } catch (Throwable e) {
                    log.error("CQL pre-translation failed: {} ({}), will translate per patient",
                            e.getMessage(), e.getClass().getName(), e);
                }
            } else {
                log.warn("context.getMeasureCql() is null, skipping pre-translation");
            }

            // 2. Discover patients
            List<String> patients = patientDiscoveryService.discoverPatients(context);
            if (patients.isEmpty()) {
                stopTimer(sample);
                incrementErrorCounter();
                return errorResult(context, patientDiscoveryService.buildNoPatientsMessage(context));
            }

            // 3. Bulk-fetch all patient resources in one batch (instead of per-patient HTTP requests)
            java.util.Map<String, java.util.List<org.hl7.fhir.r4.model.Resource>> bulkData = null;
            if (preTranslated != null) {
                long bulkStart = System.currentTimeMillis();
                try {
                    Set<String> retrieveTypes = cqlExecutionService.extractRetrieveTypesFromLibrary(
                            preTranslated.elmLibrary(), preTranslated.libraryManager());
                    retrieveTypes.add("Patient");
                    bulkData = fhirDataProviderService.bulkFetchAllPatients(
                            context.getFhirServerUrl(), patients, retrieveTypes);
                    log.info("Bulk data fetch: {} patients in {}ms",
                            patients.size(), System.currentTimeMillis() - bulkStart);
                } catch (FhirServerUnavailableException e) {
                    // PAT-112a: upstream FHIR is down — don't fall back to per-patient
                    // (which hits the same server). Fail loud so the caller gets a
                    // FHIR_UPSTREAM_UNAVAILABLE envelope instead of partial results.
                    stopTimer(sample);
                    incrementErrorCounter();
                    log.error("Measure evaluation aborted: bulk fetch detected upstream FHIR outage", e);
                    return errorResult(context,
                            "Measure evaluation aborted: upstream FHIR server unavailable. " + e.getMessage(), e);
                } catch (Exception e) {
                    // Non-availability error — e.g. malformed bulk query, patient list too large.
                    // Per-patient fallback is safe because it's a different request shape.
                    log.warn("Bulk fetch failed (non-availability), will fall back to per-patient fetch: {}", e.getMessage());
                }
            }

            // 4. Execute CQL per patient and aggregate
            final CqlExecutionService.PreTranslatedContext pt = preTranslated;
            final java.util.Map<String, java.util.List<org.hl7.fhir.r4.model.Resource>> bd = bulkData;
            AggregationState state = executeAndAggregate(context, patients, pt, bd);

            // PAT-112a: if ANY patient evaluation failed because of a FHIR outage,
            // abort the entire measure rather than returning a falsified denominator.
            // This is the clinical-safety rule: partial measure scores are worse than
            // no score — a half-populated denominator silently inflates the quality
            // rate and can mislead P4P / QI reporting.
            if (state.fhirOutageError != null) {
                stopTimer(sample);
                incrementErrorCounter();
                log.error("Measure evaluation aborted: {} patient(s) failed due to FHIR outage",
                        state.errorCount, state.fhirOutageError);
                return errorResult(context,
                        "Measure evaluation aborted: upstream FHIR server unavailable during "
                                + state.errorCount + " of " + patients.size() + " patient evaluations. "
                                + state.fhirOutageError.getMessage(),
                        state.fhirOutageError, state.errorCount, patients.size());
            }

            // 3. Check non-FHIR partial-failure threshold (PAT-140 — partial scores are
            // worse than no score, same rationale as the FHIR outage abort). Default
            // ratio 1.0 preserves old behaviour (only abort on 100% failure); ops can
            // lower it via measure.error-threshold-ratio.
            if (state.errorCount > 0 && patients.size() > 0
                    && (double) state.errorCount / patients.size() >= errorThresholdRatio) {
                stopTimer(sample);
                incrementErrorCounter();
                return errorResult(context, String.format(
                        "Measure evaluation aborted: %d of %d patient evaluations failed (threshold ratio %.2f). "
                                + "Check server logs for details.",
                        state.errorCount, patients.size(), errorThresholdRatio),
                        null, state.errorCount, patients.size());
            }

            // 4. Build final result
            MeasureEvaluationResult result = buildResult(context, state, patients.size());
            stopTimer(sample);

            // 5. Auto-save report
            long durationMs = System.currentTimeMillis() - startTime;
            autoSaveReport(result, measureDefinitionId, context.getFhirServerUrl(), durationMs);

            return result;

        } catch (Exception e) {
            incrementErrorCounter();
            stopTimer(sample);
            log.error("Measure evaluation failed", e);
            return errorResult(context, e.getMessage(), e);
        }
    }

    private MeasureEvaluationContext buildContext(MeasureEvaluationRequest request,
                                                  Long measureDefinitionId,
                                                  MeasureDefinition measureDefinition) {
        int currentYear = LocalDate.now().getYear();
        LocalDate periodStart = request.getPeriodStart() != null
                ? request.getPeriodStart()
                : (defaultPeriodStart != null && !defaultPeriodStart.isBlank()
                        ? LocalDate.parse(defaultPeriodStart)
                        : LocalDate.of(currentYear, 1, 1));
        LocalDate periodEnd = request.getPeriodEnd() != null
                ? request.getPeriodEnd()
                : (defaultPeriodEnd != null && !defaultPeriodEnd.isBlank()
                        ? LocalDate.parse(defaultPeriodEnd)
                        : LocalDate.of(currentYear, 12, 31));

        return MeasureEvaluationContext.builder()
                .request(request)
                .measureDefinition(measureDefinition)
                .measureDefinitionId(measureDefinitionId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .timeoutSeconds(measureTimeoutSeconds)
                .build();
    }

    private AggregationState executeAndAggregate(MeasureEvaluationContext context, List<String> patients,
                                                   CqlExecutionService.PreTranslatedContext preTranslated,
                                                   java.util.Map<String, java.util.List<org.hl7.fhir.r4.model.Resource>> bulkData) {
        MeasureDefinition def = context.getMeasureDefinition();
        String scoringType = def != null ? def.getScoringType() : null;
        boolean isCv = ScoringTypeConstants.CONTINUOUS_VARIABLE.equals(scoringType);
        boolean isRatio = ScoringTypeConstants.RATIO.equals(scoringType);

        // BUG #474: per-group counts. For multi-group measures we MUST iterate each group
        // separately and canonicalize the suffixed CQL define names ("Initial Population 1"
        // → "Initial Population") via PopulationEvaluator.buildExpressionMap. Tracking
        // counts per groupId lets buildMultiGroupResult emit one GroupResult per group.
        // Single-group measures degenerate to a single entry in this outer map.
        List<GroupDefinition> groupDefs = (def != null && def.getGroupDefinitions() != null
                && !def.getGroupDefinitions().isEmpty())
                ? def.getGroupDefinitions()
                : List.of();
        Map<String, Map<String, Integer>> perGroupCounts = new LinkedHashMap<>();
        if (groupDefs.isEmpty()) {
            perGroupCounts.put(DEFAULT_GROUP_ID, isCv
                    ? populationEvaluator.initializeCvPopulationCounts()
                    : populationEvaluator.initializePopulationCounts());
        } else {
            for (GroupDefinition g : groupDefs) {
                String gid = g.getGroupId() != null ? g.getGroupId() : DEFAULT_GROUP_ID;
                perGroupCounts.put(gid, isCv
                        ? populationEvaluator.initializeCvPopulationCounts()
                        : populationEvaluator.initializePopulationCounts());
            }
        }
        // Names treated as "standard population defines" for custom-expression filtering.
        // Includes both canonical names AND any per-group suffixed names that may appear in
        // results so multi-group measures don't surface "Initial Population 1" / "Denominator 2"
        // as if they were custom SDE expressions.
        Set<String> standardNames = new HashSet<>(populationEvaluator.initializePopulationCounts().keySet());
        standardNames.addAll(populationEvaluator.initializeCvPopulationCounts().keySet());
        for (GroupDefinition g : groupDefs) {
            if (g.getPopulations() == null) continue;
            for (PopulationDefinition p : g.getPopulations()) {
                if (p.getCriteriaExpression() != null) standardNames.add(p.getCriteriaExpression());
            }
        }
        // Always exclude observation wrapper names from custom expressions
        standardNames.add("Measure Observation Values");
        standardNames.add("Measure Observation Value");
        Map<String, Object> customExpressions = new LinkedHashMap<>();
        // BUG #474 follow-up: stratification data is now per-group. Each group's stratifiers
        // accumulate into their own bucket so multi-group measures don't cross-contaminate
        // strata between groups. For single-group / no-GroupDefinition measures the outer
        // map has one entry keyed by DEFAULT_GROUP_ID (or the lone group's id).
        Map<String, Map<String, Map<String, Map<String, Integer>>>> perGroupStratData = new HashMap<>();
        List<Double> observationValues = Collections.synchronizedList(new ArrayList<>());
        // Issue #539: per-group CV observation values. Each group's list is independently
        // synchronized so concurrent patient evaluations can append safely without contention
        // across groups. Single-group / legacy paths share DEFAULT_GROUP_ID.
        Map<String, List<Double>> observationValuesByGroup = new ConcurrentHashMap<>();

        // Execute CQL for all patients in parallel using cqlExecutionExecutor (10-20 threads).
        // Pre-translated path runs doExecutePreTranslated directly on the caller thread
        // (no nested executor submit), so no deadlock risk.
        record PatientResult(String patientId, CqlExecutionResponse response, Exception error) {}

        List<CompletableFuture<PatientResult>> futures = patients.stream()
                .map(patientId -> CompletableFuture.supplyAsync(() -> {
                    try {
                        CqlExecutionResponse resp = executeForPatient(context, patientId, preTranslated, bulkData);
                        return new PatientResult(patientId, resp, null);
                    } catch (Exception e) {
                        return new PatientResult(patientId, null, e);
                    }
                }, measureExecutor))
                .toList();

        // Wait for all patients to finish (with overall timeout)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(context.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Measure evaluation timed out or interrupted after {}s", context.getTimeoutSeconds());
            futures.forEach(f -> f.cancel(true));
        }

        // Aggregate results sequentially (thread-safe)
        int errorCount = 0;
        // PAT-112a: track FHIR-outage errors separately so the caller can abort
        // the whole measure evaluation rather than return a partial (falsified)
        // denominator. Non-FHIR errors still follow the existing errorCount path.
        Throwable fhirOutageError = null;
        for (CompletableFuture<PatientResult> future : futures) {
            PatientResult pr;
            try {
                pr = future.getNow(null);
            } catch (Exception e) {
                errorCount++;
                if (fhirOutageError == null) {
                    Throwable outage = findFhirOutageCause(e);
                    if (outage != null) fhirOutageError = outage;
                }
                continue;
            }
            if (pr == null || pr.error() != null) {
                errorCount++;
                if (pr != null) {
                    log.error("Failed evaluation for patient {}", pr.patientId(), pr.error());
                    if (fhirOutageError == null) {
                        Throwable outage = findFhirOutageCause(pr.error());
                        if (outage != null) fhirOutageError = outage;
                    }
                }
                continue;
            }
            Map<String, CqlExecutionResponse.ExpressionResult> results = pr.response().getResults();
            // BUG #474: per-group aggregation. For each population group, canonicalize the
            // suffixed CQL define names ("Initial Population 1" → "Initial Population") and
            // run the same single-group aggregation logic on the group-local counts map.
            if (groupDefs.isEmpty()) {
                // Legacy single-group path (no GroupDefinition wired): use the raw results
                // directly, matching prior behaviour for measures evaluated without a measure
                // definition (e.g. ad-hoc CQL execution). Writes to the global observationValues
                // list — read by buildContinuousVariableResult (single-group builder).
                Map<String, Integer> counts = perGroupCounts.get(DEFAULT_GROUP_ID);
                if (isCv) {
                    populationEvaluator.aggregateCvPatientResults(counts, results, observationValues);
                } else if (isRatio) {
                    populationEvaluator.aggregateRatioPatientResults(counts, results);
                } else {
                    populationEvaluator.aggregatePatientResults(counts, results);
                }
            } else {
                for (GroupDefinition g : groupDefs) {
                    String gid = g.getGroupId() != null ? g.getGroupId() : DEFAULT_GROUP_ID;
                    Map<String, Integer> counts = perGroupCounts.get(gid);
                    Map<String, CqlExecutionResponse.ExpressionResult> canonical =
                            populationEvaluator.buildExpressionMap(g, results);
                    if (isCv) {
                        // Issue #539: compute this group's wrapper observation define names.
                        // EcqmCqlBuilder.appendObservationWrapper emits ONE wrapper per group
                        // named "Measure Observation Value{suffix}" (patient-based) or
                        // "Measure Observation Values{suffix}" (episode-based). The suffix is
                        // " N" for multi-group (1-indexed) or "" for single-group.
                        // ObservationDefinition.criteriaExpression on the entity holds the
                        // FUNCTION name ("Measure Observation N"), not the wrapper define —
                        // CQL functions don't surface as standalone results, so we must look
                        // up the wrapper instead.
                        int groupIdx = groupDefs.indexOf(g);
                        String suffix = groupDefs.size() > 1 ? " " + (groupIdx + 1) : "";
                        List<String> obsExprNames = (g.getObservations() == null
                                || g.getObservations().isEmpty())
                                ? null
                                : List.of(
                                        "Measure Observation Values" + suffix,
                                        "Measure Observation Value" + suffix);
                        // Per-group CV observations: write to the per-group bucket so
                        // buildMultiGroupResult can compute each group's score independently.
                        // Also mirror into the legacy global list so single-group builders that
                        // still read state.observationValues see the primary group's values.
                        List<Double> groupObsValues = observationValuesByGroup
                                .computeIfAbsent(gid, k -> Collections.synchronizedList(new ArrayList<>()));
                        int sizeBefore = groupObsValues.size();
                        populationEvaluator.aggregateCvPatientResults(
                                counts, canonical, results, obsExprNames, groupObsValues);
                        // Newly-added entries this patient contributed — also append to global.
                        if (groupObsValues.size() > sizeBefore) {
                            observationValues.addAll(groupObsValues.subList(sizeBefore, groupObsValues.size()));
                        }
                    } else if (isRatio) {
                        populationEvaluator.aggregateRatioPatientResults(counts, canonical);
                    } else {
                        populationEvaluator.aggregatePatientResults(counts, canonical);
                    }
                    // Per-group stratifiers: evaluate this group's stratifiers using the raw
                    // results (for the suffixed Stratifier expression itself) plus canonical
                    // results (for population lookup inside each stratum). Stratification data
                    // accumulates into the group-local bucket so groups don't share strata.
                    if (g.getStratifiers() != null && !g.getStratifiers().isEmpty()) {
                        Map<String, Map<String, Map<String, Integer>>> groupStratData =
                                perGroupStratData.computeIfAbsent(gid, k -> new HashMap<>());
                        stratifierEvaluator.evaluatePatientStratifiers(
                                g.getStratifiers(), results, canonical, groupStratData);
                    }
                }
            }
            // Observation values: for now still collected globally across groups. Multi-group
            // measures with per-group observations (rare in proportion/cohort, possible in
            // ratio/CV) would need per-group observation lists — out of scope for this PR
            // which lands proportion multi-group. Tracked for follow-up.
            if (isRatio || (!isCv && !groupDefs.isEmpty())) {
                List<Double> patientObs = populationEvaluator.extractObservationValues(results, "Measure Observation Value");
                if (patientObs.isEmpty()) {
                    patientObs = populationEvaluator.extractObservationValues(results, "Measure Observation Values");
                }
                observationValues.addAll(patientObs);
            } else if (groupDefs.isEmpty() && !isCv) {
                // Preserve the legacy fallback for the proportion no-GroupDefinition path.
                List<Double> patientObs = populationEvaluator.extractObservationValues(results, "Measure Observation Value");
                if (patientObs.isEmpty()) {
                    patientObs = populationEvaluator.extractObservationValues(results, "Measure Observation Values");
                }
                observationValues.addAll(patientObs);
            }
            populationEvaluator.aggregateCustomExpressions(customExpressions, results, standardNames);

            // Legacy single-group stratifier path: when no GroupDefinition is wired, fall back
            // to the global stratifier list so existing ad-hoc evaluation flows continue to work.
            if (groupDefs.isEmpty()) {
                List<StratifierDefinition> legacy =
                        stratifierEvaluator.getStratifiers(context.getMeasureDefinition());
                if (!legacy.isEmpty()) {
                    Map<String, Map<String, Map<String, Integer>>> stratData =
                            perGroupStratData.computeIfAbsent(DEFAULT_GROUP_ID, k -> new HashMap<>());
                    stratifierEvaluator.evaluatePatientStratifiers(legacy, results, stratData);
                }
            }
        }

        return new AggregationState(perGroupCounts, customExpressions, perGroupStratData,
                errorCount, observationValues, observationValuesByGroup, fhirOutageError);
    }

    private CqlExecutionResponse executeForPatient(MeasureEvaluationContext context, String patientId,
                                                     CqlExecutionService.PreTranslatedContext preTranslated,
                                                     java.util.Map<String, java.util.List<org.hl7.fhir.r4.model.Resource>> bulkData) {
        CqlExecutionRequest execRequest = new CqlExecutionRequest();
        execRequest.setCql(context.getMeasureCql());
        execRequest.setPatientId(patientId);
        execRequest.setFhirServerUrl(context.getFhirServerUrl());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("Measurement Period",
                new Interval(
                        new DateTime(OffsetDateTime.of(context.getPeriodStart(), LocalTime.MIN, ZoneOffset.UTC)),
                        true,
                        new DateTime(OffsetDateTime.of(context.getPeriodEnd(), LocalTime.MAX, ZoneOffset.UTC)),
                        true));
        execRequest.setParameters(parameters);

        // Use pre-translated context if available (skips CQL→ELM per patient)
        if (preTranslated != null) {
            // If bulk data is available, create a PrefetchRetrieveProvider from it
            // (no FHIR HTTP request needed per patient)
            if (bulkData != null && bulkData.containsKey(patientId)) {
                var provider = new com.cqlplatform.service.cds.PrefetchRetrieveProvider(
                        bulkData.get(patientId), patientId);
                return cqlExecutionService.executeWithPreTranslated(execRequest, preTranslated, provider);
            }
            return cqlExecutionService.executeWithPreTranslated(execRequest, preTranslated);
        }
        return cqlExecutionService.execute(execRequest);
    }

    /**
     * BUG #474: extracts the primary (first) group's population counts from the per-group
     * AggregationState. Single-group result builders (proportion, ratio, cohort, CV)
     * always operate on the first group; multi-group measures route through
     * {@link #buildMultiGroupResult} which iterates groups directly.
     */
    private static Map<String, Integer> primaryGroupCounts(AggregationState state) {
        if (state.populationCounts.isEmpty()) {
            return new HashMap<>();
        }
        return state.populationCounts.values().iterator().next();
    }

    /**
     * BUG #474 follow-up: extracts the primary group's stratification data. Single-group
     * builders use this; {@link #buildMultiGroupResult} reads per-group buckets directly.
     */
    private static Map<String, Map<String, Map<String, Integer>>> primaryGroupStratData(AggregationState state) {
        if (state.stratificationData.isEmpty()) {
            return Map.of();
        }
        return state.stratificationData.values().iterator().next();
    }

    private MeasureEvaluationResult buildResult(MeasureEvaluationContext context,
                                                 AggregationState state,
                                                 int totalPatients) {
        Map<String, Integer> counts = primaryGroupCounts(state);
        MeasureDefinition def = context.getMeasureDefinition();

        // Continuous-variable measures use a separate result builder
        String scoringType = def != null ? def.getScoringType() : null;
        boolean isMultiGroup = def != null && def.getGroupDefinitions() != null
                && def.getGroupDefinitions().size() > 1;

        // Issue #539: multi-group CV measures route through buildMultiGroupResult, which
        // computes per-group CV score from state.observationValuesByGroup[gid]. Single-group
        // CV stays on buildCvResult which reads the global state.observationValues list.
        if (isMultiGroup) {
            return buildMultiGroupResult(context, state, totalPatients);
        }

        if (ScoringTypeConstants.CONTINUOUS_VARIABLE.equals(scoringType)) {
            return buildCvResult(context, state, totalPatients);
        }

        // Cohort measures: only Initial Population exists; score is IP count.
        // Keeping this OUT of the proportion/ratio flow below avoids emitting
        // zero-valued Denominator / Numerator rows that don't exist for cohort per FHIR spec.
        if (ScoringTypeConstants.COHORT.equals(scoringType)) {
            return buildCohortResult(context, state, totalPatients);
        }

        List<PopulationResult> populations = new ArrayList<>();
        populations.add(populationResult(INITIAL_POPULATION, counts.get("Initial Population")));
        populations.add(populationResult(DENOMINATOR, counts.get("Denominator")));

        if (counts.get("Denominator Exclusions") > 0)
            populations.add(populationResult(DENOMINATOR_EXCLUSION, counts.get("Denominator Exclusions")));
        if (counts.get("Denominator Exceptions") > 0)
            populations.add(populationResult(DENOMINATOR_EXCEPTION, counts.get("Denominator Exceptions")));

        populations.add(populationResult(NUMERATOR, counts.get("Numerator")));

        if (counts.get("Numerator Exclusions") > 0)
            populations.add(populationResult(NUMERATOR_EXCLUSION, counts.get("Numerator Exclusions")));

        // Check for ratio with observation values (rate-based scoring like "per 1,000 person-years")
        boolean hasObservations = !state.observationValues.isEmpty();
        Double measureScore;
        ObservationStatistics obsStats = null;
        String scoreUnit = "percentage";

        if (ScoringTypeConstants.RATIO.equals(scoringType) && hasObservations) {
            double rateMultiplier = 1000;
            String scoringUnit = null;
            if (def != null && def.getGroupDefinitions() != null && !def.getGroupDefinitions().isEmpty()) {
                GroupDefinition group = def.getGroupDefinitions().get(0);
                scoringUnit = group.getScoringUnit();
                if (group.getRateIndex() != null && group.getRateIndex() > 0) {
                    rateMultiplier = group.getRateIndex();
                }
            }
            String aggregateMethod = "sum";
            if (def != null && def.getGroupDefinitions() != null && !def.getGroupDefinitions().isEmpty()) {
                GroupDefinition group = def.getGroupDefinitions().get(0);
                if (group.getObservations() != null && !group.getObservations().isEmpty()) {
                    String method = group.getObservations().get(0).getAggregateMethod();
                    if (method != null && !method.isBlank()) aggregateMethod = method;
                }
            }
            Double obsSum = state.observationValues.stream().mapToDouble(Double::doubleValue).sum();
            measureScore = scoreCalculator.calculateRateScore(
                    counts.get("Numerator"), obsSum, rateMultiplier);
            obsStats = scoreCalculator.computeObservationStats(
                    state.observationValues, aggregateMethod, scoringUnit);
            scoreUnit = scoringUnit != null ? scoringUnit : "per " + (int) rateMultiplier;
        } else {
            // Ratio (without observations) and proportion diverge here: ratio allows the
            // score to exceed 100% (Numer can be larger than Denom since they're
            // independent counts per FHIR spec); proportion caps at 100% by construction
            // (Numer ⊆ Denom enforced upstream). calculateRatioScore skips the
            // denom-exclusion subtraction too — ratio uses raw denom as the divisor.
            measureScore = scoreCalculator.calculateScore(scoringType,
                    counts.get("Denominator"), counts.get("Denominator Exclusions"), counts.get("Numerator"));
        }

        List<StratifierResult> stratifierResults = stratifierEvaluator.buildStratifierResults(primaryGroupStratData(state));

        GroupResult groupResult = GroupResult.builder()
                .groupId("group-1")
                .description("Primary measure group")
                .populations(populations)
                .measureScore(measureScore)
                .measureScoreUnit(scoreUnit)
                .observationStatistics(obsStats)
                .stratifiers(stratifierResults.isEmpty() ? null : stratifierResults)
                .totalPatients(totalPatients)
                .build();

        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .measureName(context.getMeasureId())
                .status(EvaluationStatusConstants.COMPLETE)
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
                .reportType(context.getReportType())
                .groups(List.of(groupResult))
                .supplementalData(state.customExpressions.isEmpty() ? null : state.customExpressions)
                .errorCount(state.errorCount)
                .evaluatedPatientCount(totalPatients)
                .build();
    }

    /**
     * Cohort-scoring result: only Initial Population is meaningful. Score IS the IP count
     * per FHIR spec. We deliberately omit Denominator / Numerator rows because they
     * don't exist in cohort measures — emitting zero-valued ones (as the
     * proportion/ratio path did before) was a code-review-surfaced gap from #PAT-082.
     */
    private MeasureEvaluationResult buildCohortResult(MeasureEvaluationContext context,
                                                      AggregationState state, int totalPatients) {
        Map<String, Integer> counts = primaryGroupCounts(state);
        Integer ipCount = counts.getOrDefault("Initial Population", 0);

        List<PopulationResult> populations = new ArrayList<>();
        populations.add(populationResult(INITIAL_POPULATION, ipCount));

        Double measureScore = scoreCalculator.calculateCohortScore(ipCount);

        List<StratifierResult> stratifierResults = stratifierEvaluator.buildStratifierResults(primaryGroupStratData(state));

        GroupResult groupResult = GroupResult.builder()
                .groupId("group-1")
                .description("Primary measure group")
                .populations(populations)
                .measureScore(measureScore)
                .measureScoreUnit("count")
                .stratifiers(stratifierResults.isEmpty() ? null : stratifierResults)
                .totalPatients(totalPatients)
                .build();

        MeasureDefinition def = context.getMeasureDefinition();
        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .measureName(def != null && def.getName() != null ? def.getName() : context.getMeasureId())
                .status(EvaluationStatusConstants.COMPLETE)
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
                .reportType(context.getReportType())
                .groups(List.of(groupResult))
                .supplementalData(state.customExpressions.isEmpty() ? null : state.customExpressions)
                .errorCount(state.errorCount)
                .evaluatedPatientCount(totalPatients)
                .build();
    }

    private MeasureEvaluationResult buildCvResult(MeasureEvaluationContext context,
                                                    AggregationState state, int totalPatients) {
        Map<String, Integer> counts = primaryGroupCounts(state);
        MeasureDefinition def = context.getMeasureDefinition();

        List<PopulationResult> populations = new ArrayList<>();
        populations.add(populationResult(INITIAL_POPULATION, counts.getOrDefault("Initial Population", 0)));
        populations.add(populationResult(MEASURE_POPULATION, counts.getOrDefault("Measure Population", 0)));
        Integer mpExcl = counts.getOrDefault("Measure Population Exclusion", 0);
        if (mpExcl > 0) {
            populations.add(populationResult(MEASURE_POPULATION_EXCLUSION, mpExcl));
        }

        String aggregateMethod = "average";
        String scoringUnit = null;
        if (def != null && def.getGroupDefinitions() != null && !def.getGroupDefinitions().isEmpty()) {
            GroupDefinition group = def.getGroupDefinitions().get(0);
            scoringUnit = group.getScoringUnit();
            if (group.getObservations() != null && !group.getObservations().isEmpty()) {
                String method = group.getObservations().get(0).getAggregateMethod();
                if (method != null && !method.isBlank()) aggregateMethod = method;
            }
        }

        ObservationStatistics obsStats = null;
        Double measureScore = null;
        if (!state.observationValues.isEmpty()) {
            obsStats = scoreCalculator.computeObservationStats(
                    state.observationValues, aggregateMethod, scoringUnit);
            measureScore = obsStats.getAggregateValue();
        }

        List<StratifierResult> stratifierResults = stratifierEvaluator.buildStratifierResults(primaryGroupStratData(state));

        GroupResult groupResult = GroupResult.builder()
                .groupId("group-1")
                .description("Primary measure group")
                .populations(populations)
                .measureScore(measureScore)
                .measureScoreUnit(scoringUnit != null ? scoringUnit : "value")
                .observationStatistics(obsStats)
                .stratifiers(stratifierResults.isEmpty() ? null : stratifierResults)
                .totalPatients(totalPatients)
                .build();

        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .measureName(context.getMeasureDefinition() != null && context.getMeasureDefinition().getName() != null
                        ? context.getMeasureDefinition().getName() : context.getMeasureId())
                .status(EvaluationStatusConstants.COMPLETE)
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
                .reportType(context.getReportType())
                .groups(List.of(groupResult))
                .supplementalData(state.customExpressions.isEmpty() ? null : state.customExpressions)
                .errorCount(state.errorCount)
                .evaluatedPatientCount(totalPatients)
                .build();
    }

    private MeasureEvaluationResult buildMultiGroupResult(MeasureEvaluationContext context,
                                                           AggregationState state,
                                                           int totalPatients) {
        MeasureDefinition def = context.getMeasureDefinition();
        String scoringType = def.getScoringType();
        boolean isCv = ScoringTypeConstants.CONTINUOUS_VARIABLE.equals(scoringType);
        List<GroupResult> groups = new ArrayList<>();

        for (var groupDef : def.getGroupDefinitions()) {
            List<PopulationResult> populations = new ArrayList<>();
            // BUG #474: read counts from this group's per-group entry. Counts are keyed by
            // canonical population display name ("Initial Population", "Denominator", ...) —
            // not by the suffixed CQL define name — because aggregation went through
            // PopulationEvaluator.buildExpressionMap which canonicalizes per group.
            String gid = groupDef.getGroupId() != null ? groupDef.getGroupId() : DEFAULT_GROUP_ID;
            Map<String, Integer> counts = state.populationCounts.getOrDefault(gid, Map.of());

            if (groupDef.getPopulations() != null) {
                for (var popDef : groupDef.getPopulations()) {
                    String canonical = canonicalPopulationName(popDef.getPopulationType());
                    Integer count = counts.getOrDefault(canonical, 0);
                    populations.add(populationResult(popDef.getPopulationType(), count));
                }
            }

            // Issue #539: compute per-group CV score from per-group observation values bucket.
            // Proportion / ratio fall through to the legacy denom/numer path below.
            Double score;
            String scoreUnit;
            ObservationStatistics obsStats = null;
            if (isCv) {
                List<Double> groupObsValues = state.observationValuesByGroup
                        .getOrDefault(gid, List.of());
                String aggregateMethod = "average";
                String scoringUnit = groupDef.getScoringUnit();
                if (groupDef.getObservations() != null && !groupDef.getObservations().isEmpty()) {
                    String method = groupDef.getObservations().get(0).getAggregateMethod();
                    if (method != null && !method.isBlank()) aggregateMethod = method;
                }
                if (!groupObsValues.isEmpty()) {
                    obsStats = scoreCalculator.computeObservationStats(
                            groupObsValues, aggregateMethod, scoringUnit);
                    score = obsStats.getAggregateValue();
                } else {
                    score = null;
                }
                scoreUnit = scoringUnit != null ? scoringUnit : "value";
            } else {
                Integer denom = populations.stream()
                        .filter(p -> DENOMINATOR.equals(p.getPopulationType()))
                        .map(PopulationResult::getCount)
                        .findFirst().orElse(0);
                Integer denomEx = populations.stream()
                        .filter(p -> DENOMINATOR_EXCLUSION.equals(p.getPopulationType()))
                        .map(PopulationResult::getCount)
                        .findFirst().orElse(0);
                Integer numer = populations.stream()
                        .filter(p -> NUMERATOR.equals(p.getPopulationType()))
                        .map(PopulationResult::getCount)
                        .findFirst().orElse(0);
                score = scoreCalculator.calculateProportionScore(denom, denomEx, numer);
                scoreUnit = "percentage";
            }

            String desc = groupDef.getDescription() != null ? groupDef.getDescription() : "";
            if (groupDef.getRateDescription() != null) {
                desc = groupDef.getRateDescription() + (desc.isEmpty() ? "" : " - " + desc);
            }

            // BUG #474 follow-up: per-group stratifier results. Each group's stratification
            // bucket builds independently so multi-group + per-group stratifier measures
            // (e.g. proportion stratified by gender per clinical pathway) report each group's
            // strata correctly.
            Map<String, Map<String, Map<String, Integer>>> stratData =
                    state.stratificationData.getOrDefault(gid, Map.of());
            List<StratifierResult> stratResults = stratData.isEmpty()
                    ? List.of()
                    : stratifierEvaluator.buildStratifierResults(stratData);

            groups.add(GroupResult.builder()
                    .groupId(groupDef.getGroupId())
                    .description(desc)
                    .populations(populations)
                    .measureScore(score)
                    .measureScoreUnit(scoreUnit)
                    .observationStatistics(obsStats)
                    .stratifiers(stratResults.isEmpty() ? null : stratResults)
                    .totalPatients(totalPatients)
                    .build());
        }

        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .measureName(context.getMeasureDefinition() != null && context.getMeasureDefinition().getName() != null
                        ? context.getMeasureDefinition().getName() : context.getMeasureId())
                .status(EvaluationStatusConstants.COMPLETE)
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
                .reportType(context.getReportType())
                .groups(groups)
                .supplementalData(state.customExpressions.isEmpty() ? null : state.customExpressions)
                .errorCount(state.errorCount)
                .evaluatedPatientCount(totalPatients)
                .build();
    }

    private static PopulationResult populationResult(String type, Integer count) {
        return PopulationResult.builder()
                .populationType(type)
                .populationId(type)
                .count(count)
                .build();
    }

    /**
     * BUG #474: maps a FHIR population type (FHIR code or display name) to the canonical
     * key used in {@link AggregationState#populationCounts} inner maps. Mirrors
     * {@code PopulationEvaluator.toDisplayName} but kept here to avoid widening that class's
     * public surface. Keep these two methods in sync.
     */
    private static String canonicalPopulationName(String populationType) {
        if (populationType == null) return "";
        return switch (populationType.toLowerCase(java.util.Locale.ROOT)) {
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

    private MeasureEvaluationResult errorResult(MeasureEvaluationContext context, String message) {
        return errorResult(context, message, null, null, null);
    }

    private MeasureEvaluationResult errorResult(MeasureEvaluationContext context, String message, Throwable cause) {
        return errorResult(context, message, cause, null, null);
    }

    private MeasureEvaluationResult errorResult(MeasureEvaluationContext context, String message,
                                                 Throwable cause, Integer errorCount, Integer evaluatedPatients) {
        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .status("error")
                .errorMessage(message)
                .errorInfo(cause != null ? com.cqlplatform.util.ExecutionErrorClassifier.buildErrorInfo(cause) : null)
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
                .errorCount(errorCount)
                .evaluatedPatientCount(evaluatedPatients)
                .build();
    }

    private void autoSaveReport(MeasureEvaluationResult result, Long measureDefinitionId,
                                String fhirServerUrl, long durationMs) {
        if (measureReportService == null) return;
        try {
            measureReportService.saveReport(result, measureDefinitionId, fhirServerUrl, null, durationMs);
        } catch (Exception e) {
            log.warn("Failed to auto-save measure report, evaluation result is still valid", e);
        }
    }

    private void stopTimer(Timer.Sample sample) {
        if (sample != null && measureEvaluationTimer != null) sample.stop(measureEvaluationTimer);
    }

    private void incrementErrorCounter() {
        if (measureEvaluationErrorCounter != null) measureEvaluationErrorCounter.increment();
    }

    /**
     * Internal state holder for the aggregation loop.
     *
     * <p>BUG #474: {@code populationCounts} is now keyed by groupId so multi-population-group
     * measures (eCQM with stratified clinical pathways) can track per-group counts independently.
     * For single-group measures the outer map has exactly one entry whose key is either the
     * measure's group's groupId or {@link #DEFAULT_GROUP_ID} when no GroupDefinition is available.
     * Inner map remains keyed by canonical population display name ("Initial Population", etc.)
     * — {@link PopulationEvaluator#buildExpressionMap} bridges the suffixed CQL define names
     * (e.g. "Initial Population 1" / "Initial Population 2") to canonical keys.
     */
    private record AggregationState(
            Map<String, Map<String, Integer>> populationCounts,
            Map<String, Object> customExpressions,
            /** BUG #474 follow-up: per-group stratification data so multi-group stratifiers
             *  don't cross-contaminate. Outer key is groupId, then the standard
             *  stratifierId → strataValue → populationName → count nesting. */
            Map<String, Map<String, Map<String, Map<String, Integer>>>> stratificationData,
            int errorCount,
            List<Double> observationValues,
            /** Issue #539: per-group CV observation values. Keyed by groupId so multi-group
             *  CV measures with suffixed observation defines (e.g. "Measure Observation Value 1"
             *  for group 1, "... 2" for group 2) report each group's score independently.
             *  Single-group / legacy paths populate the DEFAULT_GROUP_ID entry, which mirrors
             *  the global {@link #observationValues} list. */
            Map<String, List<Double>> observationValuesByGroup,
            /** PAT-112a: non-null when any patient evaluation threw a FhirServerUnavailableException
             *  (in its cause chain). The caller aborts the whole measure evaluation rather than
             *  returning a partial denominator. */
            Throwable fhirOutageError
    ) {}

    /** Synthetic group id used when a measure has no GroupDefinition wired (legacy path). */
    private static final String DEFAULT_GROUP_ID = "group-1";

    private static final int OUTAGE_CAUSE_CHAIN_MAX_DEPTH = 20;

    /**
     * PAT-112a: walk a Throwable's cause chain and return the first
     * {@link FhirServerUnavailableException}, or null if none. CQL engine and
     * CqlExecutionService wrap underlying retrieve failures in their own exception
     * types, so the outage marker is typically buried a few causes deep.
     *
     * <p>PAT-140 raised the limit from 10 to 20 to absorb the extra wrapping
     * layers introduced by {@code CompletableFuture.supplyAsync} +
     * {@code ExecutionException} in {@link #executeAndAggregate}. If the cause
     * chain is somehow deeper than that, we now log a WARN so the edge case
     * surfaces in production logs (previously it silently returned null —
     * misclassifying an outage as a generic per-patient error).
     */
    private static Throwable findFhirOutageCause(Throwable t) {
        int depth = 0;
        while (t != null && depth < OUTAGE_CAUSE_CHAIN_MAX_DEPTH) {
            if (t instanceof FhirServerUnavailableException) return t;
            t = t.getCause();
            depth++;
        }
        if (t != null && t.getCause() != null) {
            log.warn("findFhirOutageCause: cause chain exceeded {} hops without finding outage marker; "
                    + "an outage may have been misclassified as generic patient-level error", OUTAGE_CAUSE_CHAIN_MAX_DEPTH);
        }
        return null;
    }
}
