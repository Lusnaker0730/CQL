package com.cqlplatform.service.measure;

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
                    preTranslated = cqlExecutionService.translateOnce(context.getMeasureCql());
                    log.info("Pre-translated CQL in {}ms (will reuse for all patients)",
                            System.currentTimeMillis() - translateStart);
                } catch (Exception e) {
                    log.warn("CQL pre-translation failed: {}, will translate per patient", e.getMessage());
                }
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
                            preTranslated.elmLibrary());
                    retrieveTypes.add("Patient");
                    bulkData = fhirDataProviderService.bulkFetchAllPatients(
                            context.getFhirServerUrl(), patients, retrieveTypes);
                    log.info("Bulk data fetch: {} patients in {}ms",
                            patients.size(), System.currentTimeMillis() - bulkStart);
                } catch (Exception e) {
                    log.warn("Bulk fetch failed, will fall back to per-patient fetch: {}", e.getMessage());
                }
            }

            // 4. Execute CQL per patient and aggregate
            final CqlExecutionService.PreTranslatedContext pt = preTranslated;
            final java.util.Map<String, java.util.List<org.hl7.fhir.r4.model.Resource>> bd = bulkData;
            AggregationState state = executeAndAggregate(context, patients, pt, bd);

            // 3. Check if all patients failed
            if (state.errorCount == patients.size()) {
                stopTimer(sample);
                incrementErrorCounter();
                return errorResult(context,
                        "All " + state.errorCount + " patient evaluations failed. Check server logs for details.");
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
            return errorResult(context, e.getMessage());
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
        Map<String, Integer> populationCounts = populationEvaluator.initializePopulationCounts();
        Set<String> standardNames = new HashSet<>(populationCounts.keySet());
        Map<String, Object> customExpressions = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Integer>>> stratificationData = new HashMap<>();
        List<StratifierDefinition> stratifiers = stratifierEvaluator.getStratifiers(context.getMeasureDefinition());

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
        for (CompletableFuture<PatientResult> future : futures) {
            PatientResult pr;
            try {
                pr = future.getNow(null);
            } catch (Exception e) {
                errorCount++;
                continue;
            }
            if (pr == null || pr.error() != null) {
                errorCount++;
                if (pr != null) {
                    log.error("Failed evaluation for patient {}", pr.patientId(), pr.error());
                }
                continue;
            }
            Map<String, CqlExecutionResponse.ExpressionResult> results = pr.response().getResults();
            populationEvaluator.aggregatePatientResults(populationCounts, results);
            populationEvaluator.aggregateCustomExpressions(customExpressions, results, standardNames);

            if (!stratifiers.isEmpty()) {
                stratifierEvaluator.evaluatePatientStratifiers(stratifiers, results, stratificationData);
            }
        }

        return new AggregationState(populationCounts, customExpressions, stratificationData, errorCount);
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

    private MeasureEvaluationResult buildResult(MeasureEvaluationContext context,
                                                 AggregationState state,
                                                 int totalPatients) {
        Map<String, Integer> counts = state.populationCounts;

        // Check if measure has multiple group definitions
        MeasureDefinition def = context.getMeasureDefinition();
        if (def != null && def.getGroupDefinitions() != null && def.getGroupDefinitions().size() > 1) {
            return buildMultiGroupResult(context, state, totalPatients);
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

        Double measureScore = scoreCalculator.calculateProportionScore(
                counts.get("Denominator"), counts.get("Denominator Exclusions"), counts.get("Numerator"));

        List<StratifierResult> stratifierResults = stratifierEvaluator.buildStratifierResults(state.stratificationData);

        GroupResult groupResult = GroupResult.builder()
                .groupId("group-1")
                .description("Primary measure group")
                .populations(populations)
                .measureScore(measureScore)
                .measureScoreUnit("percentage")
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
                .build();
    }

    private MeasureEvaluationResult buildMultiGroupResult(MeasureEvaluationContext context,
                                                           AggregationState state,
                                                           int totalPatients) {
        MeasureDefinition def = context.getMeasureDefinition();
        Map<String, Integer> counts = state.populationCounts;
        List<GroupResult> groups = new ArrayList<>();

        for (var groupDef : def.getGroupDefinitions()) {
            List<PopulationResult> populations = new ArrayList<>();

            if (groupDef.getPopulations() != null) {
                for (var popDef : groupDef.getPopulations()) {
                    // Map population expression name to aggregated count
                    String exprName = popDef.getCriteriaExpression();
                    Integer count = counts.getOrDefault(exprName, 0);
                    populations.add(populationResult(popDef.getPopulationType(), count));
                }
            }

            // Compute score per group
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

            Double score = scoreCalculator.calculateProportionScore(denom, denomEx, numer);

            String desc = groupDef.getDescription() != null ? groupDef.getDescription() : "";
            if (groupDef.getRateDescription() != null) {
                desc = groupDef.getRateDescription() + (desc.isEmpty() ? "" : " - " + desc);
            }

            groups.add(GroupResult.builder()
                    .groupId(groupDef.getGroupId())
                    .description(desc)
                    .populations(populations)
                    .measureScore(score)
                    .measureScoreUnit("percentage")
                    .totalPatients(totalPatients)
                    .build());
        }

        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .measureName(context.getMeasureId())
                .status(EvaluationStatusConstants.COMPLETE)
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
                .reportType(context.getReportType())
                .groups(groups)
                .supplementalData(state.customExpressions.isEmpty() ? null : state.customExpressions)
                .build();
    }

    private static PopulationResult populationResult(String type, Integer count) {
        return PopulationResult.builder()
                .populationType(type)
                .populationId(type)
                .count(count)
                .build();
    }

    private MeasureEvaluationResult errorResult(MeasureEvaluationContext context, String message) {
        return MeasureEvaluationResult.builder()
                .measureId(context.getMeasureId())
                .status("error")
                .errorMessage(message)
                .periodStart(context.getPeriodStart())
                .periodEnd(context.getPeriodEnd())
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

    /** Internal state holder for the aggregation loop. */
    private record AggregationState(
            Map<String, Integer> populationCounts,
            Map<String, Object> customExpressions,
            Map<String, Map<String, Map<String, Integer>>> stratificationData,
            int errorCount
    ) {}
}
