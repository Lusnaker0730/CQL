package com.cqlplatform.service.cql;

import com.cqlplatform.exception.CqlExecutionException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.DebugTrace;
import com.cqlplatform.model.CqlExecutionResponse.ExecutionMetadata;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionTrace;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.debug.Location;
import org.opencds.cqf.cql.engine.debug.SourceLocator;
import org.opencds.cqf.cql.engine.exception.CqlException;
import org.opencds.cqf.cql.engine.execution.CqlEngine;
import org.opencds.cqf.cql.engine.execution.Environment;
import org.opencds.cqf.cql.engine.execution.EvaluationExpressionRef;
import org.opencds.cqf.cql.engine.execution.EvaluationParams;
import org.opencds.cqf.cql.engine.execution.EvaluationResult;
import org.opencds.cqf.cql.engine.execution.EvaluationResults;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;
import kotlinx.io.CoreKt;
import kotlinx.io.JvmCoreKt;
import kotlinx.io.Source;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class CqlExecutionService {

    private static final com.fasterxml.jackson.databind.ObjectMapper ELM_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .configure(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);

    private final FhirDataProviderService dataProviderService;
    private final FhirTerminologyService terminologyService;
    private final ExecutorService executorService;
    private final CqlLibraryRepository libraryRepository;

    /** Shared model resolver — expensive to create (~2.5s), thread-safe after init. */
    private static final ComparableR4FhirModelResolver SHARED_MODEL_RESOLVER = new ComparableR4FhirModelResolver();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Timer cqlExecutionTimer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cqlExecutionCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cqlExecutionErrorCounter;

    @Value("${fhir.server.url:http://hapi-fhir:8080/fhir}")
    private String defaultFhirServerUrl;

    @Value("${cql.execution.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${cql.execution.max-retrieve-count:10000}")
    private int maxRetrieveCount;

    @Value("${cql.execution.max-collection-size:1000}")
    private int maxCollectionSize;

    public CqlExecutionService(
            FhirDataProviderService dataProviderService,
            FhirTerminologyService terminologyService,
            @Qualifier("cqlExecutionExecutor") ExecutorService executorService,
            CqlLibraryRepository libraryRepository) {
        this.dataProviderService = dataProviderService;
        this.terminologyService = terminologyService;
        this.executorService = executorService;
        this.libraryRepository = libraryRepository;
    }

    /**
     * Pre-translated CQL context — holds the Java objects from a single CQL translation
     * so they can be reused across multiple patient executions without re-translating.
     */
    public record PreTranslatedContext(
            org.hl7.elm.r1.Library elmLibrary,
            LibraryManager libraryManager,
            String cql
    ) {}

    /**
     * Translate CQL once and return the context for reuse.
     * Call this once before a batch of patient evaluations.
     */
    public PreTranslatedContext translateOnce(String cql) {
        LibraryManager libraryManager = LibraryManagerFactory.create(libraryRepository);
        CqlTranslator translator = CqlTranslator.fromText(cql, libraryManager);
        org.hl7.elm.r1.Library elmLibrary = translator.toELM();

        // Check for translation errors
        if (translator.getExceptions() != null) {
            List<CqlCompilerException> errors = translator.getExceptions().stream()
                    .filter(e -> e.getSeverity() == CqlCompilerException.ErrorSeverity.Error)
                    .toList();
            if (!errors.isEmpty()) {
                String errorSummary = errors.stream()
                        .map(CqlCompilerException::getMessage)
                        .limit(5)
                        .collect(java.util.stream.Collectors.joining("; "));
                throw new CqlExecutionException("CQL translation failed with " + errors.size()
                        + " error(s): " + errorSummary);
            }
        }

        org.hl7.elm.r1.VersionedIdentifier libraryId = elmLibrary.getIdentifier();
        if (libraryId != null) {
            libraryManager.getLibrarySourceLoader().registerProvider(
                    new InMemoryLibrarySourceProvider(libraryId.getId(), libraryId.getVersion(), cql));
            if (translator.getTranslatedLibrary() != null) {
                seedCompiledLibrary(libraryManager, libraryId, translator.getTranslatedLibrary());
            }
        }

        return new PreTranslatedContext(elmLibrary, libraryManager, cql);
    }

    /**
     * Execute CQL using a pre-translated context (skips CQL→ELM translation).
     */
    public CqlExecutionResponse executeWithPreTranslated(
            CqlExecutionRequest request, PreTranslatedContext preTranslated) {
        return executeWithPreTranslated(request, preTranslated, null);
    }

    public CqlExecutionResponse executeWithPreTranslated(
            CqlExecutionRequest request, PreTranslatedContext preTranslated, RetrieveProvider prefetchProvider) {
        // Execute directly on the caller's thread (no executor submit).
        // This avoids deadlock when the caller is already on a thread pool,
        // and allows MeasureEvaluationService to control parallelism.
        if (cqlExecutionCounter != null) cqlExecutionCounter.increment();
        long startTime = System.currentTimeMillis();
        try {
            return doExecutePreTranslated(request, preTranslated, prefetchProvider, startTime);
        } catch (CqlExecutionException e) {
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            throw e;
        } catch (Exception e) {
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            throw new CqlExecutionException("Execution failed: " + e.getMessage(), e);
        }
    }

    public CqlExecutionResponse execute(CqlExecutionRequest request) {
        return executeWithProvider(request, null);
    }

    public CqlExecutionResponse executeWithProvider(CqlExecutionRequest request, RetrieveProvider prefetchProvider) {
        log.debug("Executing CQL for patient: {}", request.getPatientId());
        if (cqlExecutionCounter != null) cqlExecutionCounter.increment();
        Timer.Sample sample = cqlExecutionTimer != null ? Timer.start() : null;
        long startTime = System.currentTimeMillis();

        Future<CqlExecutionResponse> future;
        try {
            future = executorService.submit(
                    () -> doExecute(request, prefetchProvider, startTime));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            throw new CqlExecutionException("CQL execution pool exhausted — please retry later");
        }

        try {
            CqlExecutionResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            return response;

        } catch (TimeoutException e) {
            future.cancel(true);
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            throw new CqlExecutionException("CQL execution timed out after " + timeoutSeconds + "s");
        } catch (ExecutionException e) {
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            Throwable cause = e.getCause();
            if (cause instanceof CallNotPermittedException) {
                throw new CqlExecutionException(
                        "FHIR server circuit breaker is open — server temporarily unavailable");
            }
            if (cause instanceof CqlExecutionException) {
                throw (CqlExecutionException) cause;
            }
            throw new CqlExecutionException("Execution failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            throw new CqlExecutionException("CQL execution was interrupted", e);
        }
    }

    private CqlExecutionResponse doExecute(CqlExecutionRequest request, RetrieveProvider prefetchProvider, long startTime) {
        List<String> warnings = new ArrayList<>();
        List<String> runtimeErrors = new ArrayList<>();
        try {
            LibraryManager libraryManager = LibraryManagerFactory.create(libraryRepository);

            // Use pre-compiled ELM if available, otherwise translate at runtime
            org.hl7.elm.r1.Library elmLibrary;
            CqlTranslator translator = null;
            if (request.getElmJson() != null && !request.getElmJson().isBlank()) {
                try {
                    String elmJson = request.getElmJson().strip();
                    // Translation API returns {"library": {...}} wrapper — unwrap if present
                    com.fasterxml.jackson.databind.JsonNode root = ELM_MAPPER.readTree(elmJson);
                    com.fasterxml.jackson.databind.JsonNode libraryNode = root.has("library") ? root.get("library") : root;
                    // Recursively remove all "annotation" fields — they contain abstract
                    // CqlToElmBase types that Jackson cannot deserialize
                    stripAnnotations(libraryNode);
                    elmLibrary = ELM_MAPPER.treeToValue(libraryNode, org.hl7.elm.r1.Library.class);
                    log.debug("Using pre-compiled ELM, skipped CQL translation");
                } catch (Exception e) {
                    log.warn("Pre-compiled ELM deserialization failed, falling back to CQL translation: {}", e.getMessage());
                    translator = CqlTranslator.fromText(request.getCql(), libraryManager);
                    elmLibrary = translator.toELM();
                }
            } else {
                translator = CqlTranslator.fromText(request.getCql(), libraryManager);
                elmLibrary = translator.toELM();
            }

            // Critical: register the freshly-translated library in libraryManager's cache.
            // Otherwise engine.evaluate() looks up the library by VersionedIdentifier via
            // DatabaseLibrarySourceProvider and re-compiles whatever is stored in the DB —
            // which may be a stale version (e.g. with old sort clauses) that contradicts
            // the editor's current text. The engine executes the cached compiled library,
            // so without this seeding the fresh translation is silently ignored.
            if (translator != null && translator.getTranslatedLibrary() != null
                    && elmLibrary != null && elmLibrary.getIdentifier() != null) {
                seedCompiledLibrary(libraryManager, elmLibrary.getIdentifier(), translator.getTranslatedLibrary());
            }

            // Check for translation errors — previously these were silently swallowed,
            // causing null EvaluationResult downstream
            if (translator != null && translator.getExceptions() != null) {
                List<CqlCompilerException> errors = translator.getExceptions().stream()
                        .filter(e -> e.getSeverity() == CqlCompilerException.ErrorSeverity.Error)
                        .toList();
                if (!errors.isEmpty()) {
                    String errorSummary = errors.stream()
                            .map(CqlCompilerException::getMessage)
                            .limit(5)
                            .collect(java.util.stream.Collectors.joining("; "));
                    log.error("CQL translation produced {} error(s): {}", errors.size(), errorSummary);
                    throw new CqlExecutionException("CQL translation failed with " + errors.size()
                            + " error(s): " + errorSummary);
                }
                translator.getExceptions().stream()
                        .filter(e -> e.getSeverity() == CqlCompilerException.ErrorSeverity.Warning)
                        .map(CqlCompilerException::getMessage)
                        .forEach(warnings::add);
                if (!warnings.isEmpty()) {
                    log.warn("CQL translation produced {} warning(s)", warnings.size());
                }
            }

            org.hl7.elm.r1.VersionedIdentifier libraryId = elmLibrary.getIdentifier();

            // Extract source locators and dependencies for debug mode
            Map<String, String> sourceLocators = new HashMap<>();
            Map<String, List<String>> expressionDependencies = new HashMap<>();
            String elmJson = request.getElmJson();
            if (request.isDebugMode()) {
                if (elmLibrary.getStatements() != null && elmLibrary.getStatements().getDef() != null) {
                    for (org.hl7.elm.r1.ExpressionDef def : elmLibrary.getStatements().getDef()) {
                        if (def.getLocator() != null) {
                            sourceLocators.put(def.getName(), def.getLocator());
                        }
                    }
                }
                if (elmJson == null && translator != null) {
                    elmJson = translator.toJson();
                }
                expressionDependencies = extractExpressionDependencies(elmJson);
            }

            // Register the source of the translated library so the engine can find it
            if (libraryId != null) {
                libraryManager.getLibrarySourceLoader().registerProvider(
                        new InMemoryLibrarySourceProvider(
                                libraryId.getId(),
                                libraryId.getVersion(),
                                request.getCql()));
            }

            String fhirServerUrl = request.getFhirServerUrl() != null
                    ? request.getFhirServerUrl() : defaultFhirServerUrl;
            log.debug("Using FHIR server URL: {}", fhirServerUrl);

            // Setup terminology provider
            TerminologyProvider terminologyProvider = terminologyService.createTerminologyProvider(fhirServerUrl);

            // Setup data provider - use prefetch if available, auto-prefetch for patient context, otherwise REST
            ComparableR4FhirModelResolver modelResolver = SHARED_MODEL_RESOLVER;
            RetrieveProvider retrieveProvider;
            if (prefetchProvider != null) {
                log.info("Using prefetch data provider for CQL execution");
                // Wire up TerminologyProvider so PrefetchRetrieveProvider can expand ValueSets
                if (prefetchProvider instanceof com.cqlplatform.service.cds.PrefetchRetrieveProvider pfp) {
                    pfp.setTerminologyProvider(terminologyProvider);
                }
                retrieveProvider = prefetchProvider;
            } else if (request.getPatientId() != null) {
                // Auto-prefetch: batch-fetch all needed resource types in one FHIR request
                retrieveProvider = tryAutoPrefetch(request, fhirServerUrl, terminologyProvider, translator, elmJson);
                if (retrieveProvider == null) {
                    retrieveProvider = dataProviderService.createDataProvider(fhirServerUrl, terminologyProvider);
                }
            } else {
                retrieveProvider = dataProviderService.createDataProvider(fhirServerUrl, terminologyProvider);
            }

            // Wrap in tracing provider when debug mode is enabled
            TracingRetrieveProvider tracingProvider = null;
            if (request.isDebugMode()) {
                tracingProvider = new TracingRetrieveProvider(retrieveProvider);
                retrieveProvider = tracingProvider;
            }

            // Outermost wrapper: check interrupt flag + cap result size before every retrieve()
            retrieveProvider = new InterruptAwareRetrieveProvider(retrieveProvider, maxRetrieveCount);

            CompositeDataProvider compositeProvider = new CompositeDataProvider(modelResolver, retrieveProvider);

            // Create data providers map
            Map<String, org.opencds.cqf.cql.engine.data.DataProvider> dataProviders = new HashMap<>();
            dataProviders.put("http://hl7.org/fhir", compositeProvider);

            // Create environment using libraryManager
            Environment environment = new Environment(libraryManager, dataProviders, terminologyProvider);

            // Create CQL Engine with a DebugMap so the engine records per-expression
            // exceptions into State.debugResult (otherwise shouldDebug(Exception) returns
            // NONE and errors are silently swallowed — they only appear in engine logs).
            CqlEngine engine = new CqlEngine(environment);
            org.opencds.cqf.cql.engine.debug.DebugMap debugMap =
                    new org.opencds.cqf.cql.engine.debug.DebugMap();
            debugMap.setLoggingEnabled(true);
            engine.getState().setDebugMap(debugMap);

            Set<String> expressions = determineExpressions(request, elmLibrary);

            Map<String, ExpressionResult> results = new LinkedHashMap<>();
            List<ExpressionTrace> expressionTraces = new ArrayList<>();
            int traceOrder = 0;

            if (request.isDebugMode()) {
                // Debug mode: evaluate each expression individually for per-expression timing
                for (String expressionName : expressions) {
                    long exprStart = System.currentTimeMillis();
                    try {
                        Set<String> singleExpr = Set.of(expressionName);
                        String pid = null;
                        if (request.getPatientId() != null) {
                            pid = request.getPatientId();
                            if (!pid.startsWith("Patient/")) pid = "Patient/" + pid;
                        }
                        EvaluationResult evalResult = evaluateWithEngine(engine,
                                elmLibrary.getIdentifier(), singleExpr,
                                request.getContextType(), pid, request.getParameters());
                        long exprTime = System.currentTimeMillis() - exprStart;

                        Object value = null;
                        if (evalResult != null && evalResult.getExpressionResults() != null) {
                            org.opencds.cqf.cql.engine.execution.ExpressionResult exprResult =
                                    evalResult.getExpressionResults().get(expressionName);
                            value = exprResult != null ? exprResult.getValue() : null;
                        }
                        String valueType = value != null ? value.getClass().getSimpleName() : "null";

                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName)
                                .value(toSerializable(value))
                                .valueType(valueType)
                                .displayValue(formatDisplayValue(value))
                                .build());

                        expressionTraces.add(ExpressionTrace.builder()
                                .name(expressionName)
                                .resultType(valueType)
                                .resultDisplay(formatDisplayValue(value))
                                .evaluationTimeMs(exprTime)
                                .order(traceOrder++)
                                .sourceLocator(sourceLocators.get(expressionName))
                                .dependencies(expressionDependencies.getOrDefault(expressionName, List.of()))
                                .build());
                    } catch (Exception e) {
                        long exprTime = System.currentTimeMillis() - exprStart;
                        log.warn("Failed to evaluate expression in debug mode: {}", expressionName, e);
                        // Try to extract runtime source locator from CqlException
                        String runtimeLocator = extractRuntimeLocator(e);
                        String errorLocator = runtimeLocator != null
                                ? runtimeLocator : sourceLocators.get(expressionName);
                        String errorDisplay = runtimeLocator != null
                                ? "Error at " + runtimeLocator + ": " + e.getMessage()
                                : "Error: " + e.getMessage();
                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName)
                                .value(null)
                                .valueType("Error")
                                .displayValue(errorDisplay)
                                .build());
                        runtimeErrors.add(expressionName + ": " + e.getMessage());
                        expressionTraces.add(ExpressionTrace.builder()
                                .name(expressionName)
                                .resultType("Error")
                                .resultDisplay(errorDisplay)
                                .evaluationTimeMs(exprTime)
                                .order(traceOrder++)
                                .sourceLocator(errorLocator)
                                .dependencies(expressionDependencies.getOrDefault(expressionName, List.of()))
                                .build());
                    }
                }
            } else {
                // Normal mode: evaluate all expressions at once
                EvaluationResult evaluationResult = null;
                boolean batchFailed = false;

                try {
                    String patientId = null;
                    if (request.getPatientId() != null) {
                        patientId = request.getPatientId();
                        if (!patientId.startsWith("Patient/")) {
                            patientId = "Patient/" + patientId;
                        }
                    }
                    evaluationResult = evaluateWithEngine(engine,
                            elmLibrary.getIdentifier(), expressions,
                            request.getContextType(), patientId, request.getParameters());
                } catch (Exception batchEx) {
                    // Batch evaluation failed (e.g. ambiguous overload in FHIRHelpers).
                    // Fall back to per-expression evaluation so only the failing
                    // expression(s) return errors while the rest succeed.
                    log.warn("Batch CQL evaluation failed, falling back to per-expression evaluation: {}",
                            batchEx.getMessage());
                    batchFailed = true;
                }

                if (batchFailed || evaluationResult == null) {
                    if (evaluationResult == null && !batchFailed) {
                        log.warn("CQL engine returned null EvaluationResult for library, falling back to per-expression evaluation");
                    }
                    for (String expressionName : expressions) {
                        try {
                            Set<String> singleExpr = Set.of(expressionName);
                            String pid = null;
                            if (request.getPatientId() != null) {
                                pid = request.getPatientId();
                                if (!pid.startsWith("Patient/")) pid = "Patient/" + pid;
                            }
                            EvaluationResult singleResult = evaluateWithEngine(engine,
                                    elmLibrary.getIdentifier(), singleExpr,
                                    request.getContextType(), pid, request.getParameters());
                            Object value = null;
                            if (singleResult != null && singleResult.getExpressionResults() != null) {
                                org.opencds.cqf.cql.engine.execution.ExpressionResult exprResult =
                                        singleResult.getExpressionResults().get(expressionName);
                                value = exprResult != null ? exprResult.getValue() : null;
                            }
                            results.put(expressionName, ExpressionResult.builder()
                                    .name(expressionName)
                                    .value(toSerializable(value))
                                    .valueType(value != null ? value.getClass().getSimpleName() : "null")
                                    .displayValue(formatDisplayValue(value))
                                    .build());
                        } catch (Exception e) {
                            log.warn("Expression evaluation failed: {}", expressionName, e);
                            String runtimeLocator = extractRuntimeLocator(e);
                            String errorDisplay = runtimeLocator != null
                                    ? "Error at " + runtimeLocator + ": " + e.getMessage()
                                    : "Error: " + e.getMessage();
                            results.put(expressionName, ExpressionResult.builder()
                                    .name(expressionName)
                                    .value(null)
                                    .valueType("Error")
                                    .displayValue(errorDisplay)
                                    .build());
                            runtimeErrors.add(expressionName + ": " + e.getMessage());
                        }
                    }
                } else {
                    for (String expressionName : expressions) {
                        try {
                            Object value = null;
                            if (evaluationResult.getExpressionResults() != null) {
                                org.opencds.cqf.cql.engine.execution.ExpressionResult exprResult =
                                        evaluationResult.getExpressionResults().get(expressionName);
                                value = exprResult != null ? exprResult.getValue() : null;
                            }
                            results.put(expressionName, ExpressionResult.builder()
                                    .name(expressionName)
                                    .value(toSerializable(value))
                                    .valueType(value != null ? value.getClass().getSimpleName() : "null")
                                    .displayValue(formatDisplayValue(value))
                                    .build());
                        } catch (Exception e) {
                            log.warn("Failed to get result for expression: {}", expressionName, e);
                            String runtimeLocator = extractRuntimeLocator(e);
                            String errorDisplay = runtimeLocator != null
                                    ? "Error at " + runtimeLocator + ": " + e.getMessage()
                                    : "Error: " + e.getMessage();
                            results.put(expressionName, ExpressionResult.builder()
                                    .name(expressionName)
                                    .value(null)
                                    .valueType("Error")
                                    .displayValue(errorDisplay)
                                    .build());
                            runtimeErrors.add(expressionName + ": " + e.getMessage());
                        }
                    }
                }
            }

            // Harvest any engine-captured exceptions that didn't surface via direct
            // throw (e.g. when the engine's batch evaluator swallows per-expression
            // failures and returns null/partial results). Attribute each message to
            // its source locator so users can pinpoint the failing expression.
            org.opencds.cqf.cql.engine.debug.DebugResult engineDebug = engine.getState().getDebugResult();
            if (engineDebug != null && engineDebug.getMessages() != null) {
                for (org.opencds.cqf.cql.engine.exception.CqlException ce : engineDebug.getMessages()) {
                    String msg = ce.getMessage();
                    if (msg == null) continue;
                    org.opencds.cqf.cql.engine.debug.SourceLocator loc = ce.getSourceLocator();
                    String formatted = (loc != null) ? ("[" + loc + "] " + msg) : msg;
                    if (runtimeErrors.stream().noneMatch(e -> e.endsWith(msg))) {
                        runtimeErrors.add(formatted);
                    }
                    log.warn("Engine CqlException at [{}]: {}", loc, msg);
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;

            // Build debug trace if enabled
            DebugTrace debugTrace = null;
            if (request.isDebugMode()) {
                debugTrace = DebugTrace.builder()
                        .expressionTraces(expressionTraces)
                        .retrieveTraces(tracingProvider != null ? tracingProvider.getTraces() : List.of())
                        .totalTimeMs(executionTime)
                        .sourceLocators(sourceLocators)
                        .elmJson(elmJson)
                        .build();
            }

            return CqlExecutionResponse.builder()
                    .success(true)
                    .patientId(request.getPatientId())
                    .results(results)
                    .warnings(warnings.isEmpty() ? null : warnings)
                    .errors(runtimeErrors.isEmpty() ? null : runtimeErrors)
                    .debugTrace(debugTrace)
                    .metadata(ExecutionMetadata.builder()
                            .executionTimeMs(executionTime)
                            .libraryId(libraryId != null ? libraryId.getId() : null)
                            .libraryVersion(libraryId != null ? libraryId.getVersion() : null)
                            .fhirServerUrl(fhirServerUrl)
                            .resourcesRetrieved(dataProviderService.getAndResetRetrieveCount())
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("CQL execution failed", e);
            throw new CqlExecutionException("Execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build EvaluationParams and call engine.evaluate(), returning the single EvaluationResult.
     */
    private EvaluationResult evaluateWithEngine(CqlEngine engine, VersionedIdentifier libraryId,
            Set<String> expressions, String contextType, String contextValue,
            Map<String, Object> parameters) {
        List<EvaluationExpressionRef> exprRefs = expressions.stream()
                .map(EvaluationExpressionRef::new)
                .toList();

        Map<VersionedIdentifier, List<EvaluationExpressionRef>> exprMap = new HashMap<>();
        exprMap.put(libraryId, exprRefs);

        kotlin.Pair<String, Object> ctxParam = contextValue != null
                ? new kotlin.Pair<>(contextType, contextValue)
                : null;

        EvaluationParams params = new EvaluationParams(exprMap, ctxParam, parameters, null, null);
        EvaluationResults results = engine.evaluate(params);
        RuntimeException engineException = results.getExceptionFor(libraryId);
        if (engineException != null) {
            throw engineException;
        }
        return results.getResultFor(libraryId);
    }

    /**
     * Extract FHIR retrieve types directly from a pre-translated ELM Library object.
     * Same-library ExpressionRefs are covered by the outer loop over {@code statements.def};
     * cross-library ExpressionRefs require resolving the included library via {@code libraryManager}
     * so we can walk its own ExpressionDefs (otherwise bulk-fetch misses resource types that are
     * only retrieved inside included libraries — e.g. a main measure that delegates its Initial
     * Population to an external library containing the actual [Encounter]/[Condition] retrieves).
     */
    public Set<String> extractRetrieveTypesFromLibrary(org.hl7.elm.r1.Library library) {
        return extractRetrieveTypesFromLibrary(library, null);
    }

    /**
     * Variant that follows cross-library ExpressionRefs through the given LibraryManager.
     * Passing {@code null} reverts to the legacy behavior (skips cross-lib refs).
     */
    public Set<String> extractRetrieveTypesFromLibrary(org.hl7.elm.r1.Library library, LibraryManager libraryManager) {
        Set<String> types = new HashSet<>();
        Set<String> visited = new HashSet<>();
        if (library.getStatements() != null && library.getStatements().getDef() != null) {
            for (org.hl7.elm.r1.ExpressionDef def : library.getStatements().getDef()) {
                collectRetrieveTypes(def.getExpression(), types, library, libraryManager, visited);
            }
        }
        return types;
    }

    private void collectRetrieveTypes(org.hl7.elm.r1.Element element, Set<String> types) {
        collectRetrieveTypes(element, types, null, null, new HashSet<>());
    }

    private void collectRetrieveTypes(org.hl7.elm.r1.Element element,
                                      Set<String> types,
                                      org.hl7.elm.r1.Library currentLibrary,
                                      LibraryManager libraryManager,
                                      Set<String> visitedCrossLibRefs) {
        if (element == null) return;
        if (element instanceof org.hl7.elm.r1.Retrieve retrieve) {
            javax.xml.namespace.QName dt = retrieve.getDataType();
            if (dt != null) {
                types.add(dt.getLocalPart());
            }
        }
        // Recurse into child expressions using reflection-free approach
        if (element instanceof org.hl7.elm.r1.Query query) {
            if (query.getSource() != null) {
                for (var src : query.getSource()) collectRetrieveTypes(src.getExpression(), types, currentLibrary, libraryManager, visitedCrossLibRefs);
            }
            if (query.getWhere() != null) collectRetrieveTypes(query.getWhere(), types, currentLibrary, libraryManager, visitedCrossLibRefs);
        } else if (element instanceof org.hl7.elm.r1.FunctionRef funcRef) {
            // FunctionRef extends ExpressionRef but carries operands from THIS library
            // (e.g. C3F.Verified([Observation: ...]) — the [Observation] Retrieve is our operand)
            if (funcRef.getOperand() != null) {
                for (var op : funcRef.getOperand()) collectRetrieveTypes(op, types, currentLibrary, libraryManager, visitedCrossLibRefs);
            }
            // Cross-library function calls also need to walk into the included library body
            // in case the function references retrieves internally (e.g. C3F.ObservationLookBack
            // which wraps [Observation]). Only follow when we have libraryManager context.
            if (funcRef.getLibraryName() != null && libraryManager != null && currentLibrary != null) {
                followCrossLibraryRef(funcRef.getLibraryName(), funcRef.getName(),
                        types, currentLibrary, libraryManager, visitedCrossLibRefs);
            }
        } else if (element instanceof org.hl7.elm.r1.ExpressionRef ref) {
            // Same-library refs are covered by the outer loop; cross-library refs must be followed
            if (ref.getLibraryName() != null && libraryManager != null && currentLibrary != null) {
                followCrossLibraryRef(ref.getLibraryName(), ref.getName(),
                        types, currentLibrary, libraryManager, visitedCrossLibRefs);
            }
        } else if (element instanceof org.hl7.elm.r1.UnaryExpression ue) {
            collectRetrieveTypes(ue.getOperand(), types, currentLibrary, libraryManager, visitedCrossLibRefs);
        } else if (element instanceof org.hl7.elm.r1.BinaryExpression be) {
            for (var op : be.getOperand()) collectRetrieveTypes(op, types, currentLibrary, libraryManager, visitedCrossLibRefs);
        } else if (element instanceof org.hl7.elm.r1.NaryExpression ne) {
            for (var op : ne.getOperand()) collectRetrieveTypes(op, types, currentLibrary, libraryManager, visitedCrossLibRefs);
        }
    }

    /**
     * Resolve {@code alias.defName} via the current library's IncludeDefs, then walk the referenced
     * define in the included library. Guards against recursion cycles via {@code visited}.
     */
    private void followCrossLibraryRef(String libraryAlias,
                                       String defName,
                                       Set<String> types,
                                       org.hl7.elm.r1.Library currentLibrary,
                                       LibraryManager libraryManager,
                                       Set<String> visited) {
        if (libraryAlias == null || defName == null) return;
        // Find the include def that maps this alias to a library path/version
        if (currentLibrary.getIncludes() == null || currentLibrary.getIncludes().getDef() == null) return;
        org.hl7.elm.r1.IncludeDef includeDef = null;
        for (var inc : currentLibrary.getIncludes().getDef()) {
            if (libraryAlias.equals(inc.getLocalIdentifier())) { includeDef = inc; break; }
        }
        if (includeDef == null) return;

        String includedPath = includeDef.getPath();
        String includedVersion = includeDef.getVersion();
        String visitKey = includedPath + "|" + includedVersion + "|" + defName;
        if (visited.contains(visitKey)) return;
        visited.add(visitKey);

        // Locate the compiled library in the libraryManager cache
        org.hl7.elm.r1.Library includedLibrary = null;
        for (var entry : libraryManager.getCompiledLibraries().entrySet()) {
            org.hl7.elm.r1.VersionedIdentifier vid = entry.getKey();
            if (vid == null || vid.getId() == null) continue;
            if (vid.getId().equals(includedPath)
                    && (includedVersion == null || includedVersion.equals(vid.getVersion()))) {
                includedLibrary = entry.getValue().getLibrary();
                break;
            }
        }
        if (includedLibrary == null || includedLibrary.getStatements() == null) return;

        // Find the ExpressionDef by name and recurse
        for (org.hl7.elm.r1.ExpressionDef def : includedLibrary.getStatements().getDef()) {
            if (defName.equals(def.getName())) {
                collectRetrieveTypes(def.getExpression(), types, includedLibrary, libraryManager, visited);
                break;
            }
        }
    }

    /**
     * Recursively removes all "annotation" fields from a Jackson JSON tree.
     * ELM annotation nodes contain abstract CqlToElmBase types that cannot be deserialized.
     */
    private static void stripAnnotations(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            var obj = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            obj.remove("annotation");
            obj.fields().forEachRemaining(entry -> stripAnnotations(entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(CqlExecutionService::stripAnnotations);
        }
    }

    /**
     * Execute CQL using pre-translated Library + LibraryManager (skips CQL→ELM entirely).
     */
    private CqlExecutionResponse doExecutePreTranslated(
            CqlExecutionRequest request, PreTranslatedContext ctx,
            RetrieveProvider prefetchProvider, long startTime) {
        try {
            long t0 = System.currentTimeMillis();
            org.hl7.elm.r1.Library elmLibrary = ctx.elmLibrary();
            org.hl7.elm.r1.VersionedIdentifier libraryId = elmLibrary.getIdentifier();

            String fhirServerUrl = request.getFhirServerUrl() != null
                    ? request.getFhirServerUrl() : defaultFhirServerUrl;

            long t1 = System.currentTimeMillis();
            TerminologyProvider terminologyProvider = terminologyService.createTerminologyProvider(fhirServerUrl);
            long t2 = System.currentTimeMillis();
            ComparableR4FhirModelResolver modelResolver = SHARED_MODEL_RESOLVER;
            long t3 = System.currentTimeMillis();
            RetrieveProvider retrieveProvider;
            if (prefetchProvider != null) {
                if (prefetchProvider instanceof com.cqlplatform.service.cds.PrefetchRetrieveProvider pfp) {
                    pfp.setTerminologyProvider(terminologyProvider);
                }
                retrieveProvider = prefetchProvider;
            } else if (request.getPatientId() != null) {
                // Extract retrieve types directly from pre-translated ELM Library object
                Set<String> retrieveTypes = extractRetrieveTypesFromLibrary(ctx.elmLibrary(), ctx.libraryManager());
                retrieveTypes.add("Patient");
                String pid = request.getPatientId();
                if (pid.startsWith("Patient/")) pid = pid.substring("Patient/".length());
                try {
                    com.cqlplatform.security.InputValidator.requireValidResourceId(pid);
                    java.util.List<org.hl7.fhir.r4.model.Resource> resources =
                            dataProviderService.batchFetchPatientResources(fhirServerUrl, pid, retrieveTypes);
                    log.info("Batch prefetch result: {} resources for {} types", resources.size(), retrieveTypes.size());
                    var provider = new com.cqlplatform.service.cds.PrefetchRetrieveProvider(resources, pid);
                    provider.setTerminologyProvider(terminologyProvider);
                    retrieveProvider = provider;
                } catch (Exception e) {
                    log.warn("Batch prefetch failed, falling back to REST: {}", e.getMessage());
                    retrieveProvider = dataProviderService.createDataProvider(fhirServerUrl, terminologyProvider);
                }
            } else {
                retrieveProvider = dataProviderService.createDataProvider(fhirServerUrl, terminologyProvider);
            }
            retrieveProvider = new InterruptAwareRetrieveProvider(retrieveProvider, maxRetrieveCount);
            CompositeDataProvider compositeProvider = new CompositeDataProvider(modelResolver, retrieveProvider);

            long t4 = System.currentTimeMillis();
            Map<String, org.opencds.cqf.cql.engine.data.DataProvider> dataProviders = new HashMap<>();
            dataProviders.put("http://hl7.org/fhir", compositeProvider);

            Environment environment = new Environment(ctx.libraryManager(), dataProviders, terminologyProvider);
            long t5 = System.currentTimeMillis();
            CqlEngine engine = new CqlEngine(environment);
            long t6 = System.currentTimeMillis();

            Set<String> expressions = determineExpressions(request, elmLibrary);
            Map<String, ExpressionResult> results = new LinkedHashMap<>();

            // Normal mode: evaluate all expressions at once
            EvaluationResult evaluationResult = null;
            boolean batchFailed = false;
            try {
                String patientId = request.getPatientId();
                if (patientId != null && !patientId.startsWith("Patient/")) {
                    patientId = "Patient/" + patientId;
                }
                long t7 = System.currentTimeMillis();
                evaluationResult = evaluateWithEngine(engine, libraryId, expressions,
                        request.getContextType(), patientId, request.getParameters());
                long t8 = System.currentTimeMillis();
                log.info("PROFILE patient={} | terminology={}ms modelResolver={}ms dataProvider={}ms env={}ms engine={}ms evaluate={}ms | total={}ms",
                        request.getPatientId(),
                        t2-t1, t3-t2, t4-t3, t5-t4, t6-t5, t8-t7,
                        t8-t0);
            } catch (Exception batchEx) {
                log.warn("Batch CQL evaluation failed, falling back to per-expression: {}", batchEx.getMessage());
                batchFailed = true;
            }

            if (batchFailed || evaluationResult == null) {
                for (String expressionName : expressions) {
                    try {
                        Set<String> singleExpr = Set.of(expressionName);
                        String pid = request.getPatientId();
                        if (pid != null && !pid.startsWith("Patient/")) pid = "Patient/" + pid;
                        EvaluationResult singleResult = evaluateWithEngine(engine, libraryId, singleExpr,
                                request.getContextType(), pid, request.getParameters());
                        Object value = null;
                        if (singleResult != null && singleResult.getExpressionResults() != null) {
                            var exprResult = singleResult.getExpressionResults().get(expressionName);
                            value = exprResult != null ? exprResult.getValue() : null;
                        }
                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName).value(toSerializable(value))
                                .valueType(value != null ? value.getClass().getSimpleName() : "null")
                                .displayValue(formatDisplayValue(value)).build());
                    } catch (Exception e) {
                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName).value(null).valueType("Error")
                                .displayValue("Error: " + e.getMessage()).build());
                    }
                }
            } else {
                for (String expressionName : expressions) {
                    try {
                        Object value = null;
                        if (evaluationResult.getExpressionResults() != null) {
                            var exprResult = evaluationResult.getExpressionResults().get(expressionName);
                            value = exprResult != null ? exprResult.getValue() : null;
                        }
                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName).value(toSerializable(value))
                                .valueType(value != null ? value.getClass().getSimpleName() : "null")
                                .displayValue(formatDisplayValue(value)).build());
                    } catch (Exception e) {
                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName).value(null).valueType("Error")
                                .displayValue("Error: " + e.getMessage()).build());
                    }
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            return CqlExecutionResponse.builder()
                    .results(results)
                    .metadata(ExecutionMetadata.builder()
                            .executionTimeMs(executionTime)
                            .fhirServerUrl(fhirServerUrl)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Pre-translated CQL execution failed", e);
            throw new CqlExecutionException("Execution failed: " + e.getMessage(), e);
        }
    }

    private Set<String> determineExpressions(CqlExecutionRequest request, org.hl7.elm.r1.Library library) {
        if (request.getExpressionNames() != null && request.getExpressionNames().length > 0) {
            return new HashSet<>(Arrays.asList(request.getExpressionNames()));
        }

        Set<String> expressions = new HashSet<>();
        if (library.getStatements() != null && library.getStatements().getDef() != null) {
            library.getStatements().getDef().forEach(stmt -> {
                if (!"Patient".equals(stmt.getName())) {
                    expressions.add(stmt.getName());
                }
            });
        }
        return expressions;
    }

    /**
     * Convert CQL engine result objects to JSON-safe types.
     * Raw CQL objects (Tuple, Code, etc.) contain internal engine state that
     * Jackson cannot serialize, so we convert them to primitives/Maps/Lists.
     */
    private Object toSerializable(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean || value instanceof Number || value instanceof String) return value;
        if (value instanceof java.time.ZonedDateTime) return value.toString();
        if (value instanceof java.time.LocalDate) return value.toString();
        if (value instanceof java.time.LocalDateTime) return value.toString();
        if (value instanceof org.opencds.cqf.cql.engine.runtime.Quantity q) {
            return q.getValue() + (q.getUnit() != null ? " '" + q.getUnit() + "'" : "");
        }
        // HAPI FHIR PrimitiveType (DecimalType, IntegerType, BooleanType, StringType, etc.)
        if (value instanceof org.hl7.fhir.r4.model.PrimitiveType<?> pt) {
            Object primitiveValue = pt.getValue();
            if (primitiveValue instanceof Number || primitiveValue instanceof Boolean || primitiveValue instanceof String) {
                return primitiveValue;
            }
            return pt.getValueAsString();
        }
        if (value instanceof org.opencds.cqf.cql.engine.runtime.Tuple t) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (String key : t.getElements().keySet()) {
                map.put(key, toSerializable(t.getElements().get(key)));
            }
            return map;
        }
        if (value instanceof Iterable<?> iter) {
            List<Object> list = new ArrayList<>();
            for (Object item : iter) {
                if (list.size() >= maxCollectionSize) {
                    list.add("[... truncated at " + maxCollectionSize + " items]");
                    break;
                }
                list.add(toSerializable(item));
            }
            return list;
        }
        // Fallback: use display string
        return formatDisplayValue(value);
    }

    private String formatDisplayValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Iterable) {
            List<String> items = new ArrayList<>();
            ((Iterable<?>) value).forEach(item -> items.add(formatSingleValue(item)));
            return "[" + String.join(", ", items) + "]";
        }
        return formatSingleValue(value);
    }

    private String formatSingleValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number || value instanceof String) {
            return value.toString();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toString();
        }
        return value.getClass().getSimpleName() + ": " + value.toString();
    }

    private Map<String, List<String>> extractExpressionDependencies(String elmJson) {
        Map<String, List<String>> result = new HashMap<>();
        if (elmJson == null) return result;
        try {
            com.fasterxml.jackson.databind.JsonNode root = ELM_MAPPER.readTree(elmJson);
            com.fasterxml.jackson.databind.JsonNode statements = root.path("library").path("statements").path("def");
            if (statements.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode def : statements) {
                    String name = def.path("name").asText(null);
                    if (name == null) continue;
                    List<String> deps = new ArrayList<>();
                    collectExpressionRefNodes(def, deps);
                    deps.remove(name);
                    if (!deps.isEmpty()) {
                        result.put(name, deps);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract expression dependencies: {}", e.getMessage());
        }
        return result;
    }

    private void collectExpressionRefNodes(com.fasterxml.jackson.databind.JsonNode node, List<String> refs) {
        if (node == null) return;
        if (node.isObject()) {
            if ("ExpressionRef".equals(node.path("type").asText(""))) {
                String refName = node.path("name").asText(null);
                if (refName != null && !refs.contains(refName)) {
                    refs.add(refName);
                }
            }
            for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = node.elements(); it.hasNext(); ) {
                collectExpressionRefNodes(it.next(), refs);
            }
        } else if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode child : node) {
                collectExpressionRefNodes(child, refs);
            }
        }
    }

    /**
     * Walk the exception cause chain looking for a CqlException with a SourceLocator.
     * Returns a locator string like "5:1-5:42" or null if none found.
     */
    /**
     * Register a freshly-translated library in the LibraryManager's compiled-library cache
     * so the engine picks it up instead of re-compiling from a stored (possibly stale) DB copy
     * via {@code DatabaseLibrarySourceProvider}.
     *
     * <p>Also sorts {@code statements.def} by name to match the invariant that
     * {@link org.opencds.cqf.cql.engine.execution.CqlEngine} assumes when doing
     * {@code binarySearch} in {@code Libraries.resolveExpressionRef}. The translator produces
     * defs in source order; only {@code LibraryManager.compileLibrary} sorts. Seeding without
     * sorting causes "Could not resolve expression reference" at runtime.</p>
     *
     * <p>This is the single entry point for all "translate + evaluate with fresh CQL"
     * flows — do not seed the cache manually elsewhere.</p>
     */
    private static void seedCompiledLibrary(
            LibraryManager libraryManager,
            VersionedIdentifier libraryId,
            org.cqframework.cql.cql2elm.model.CompiledLibrary compiled) {
        if (compiled == null || libraryId == null || libraryManager == null) return;
        if (compiled.getLibrary() != null
                && compiled.getLibrary().getStatements() != null
                && compiled.getLibrary().getStatements().getDef() != null) {
            compiled.getLibrary().getStatements().getDef().sort(
                    Comparator.comparing(
                            org.hl7.elm.r1.ExpressionDef::getName,
                            Comparator.nullsFirst(String::compareTo)));
        }
        libraryManager.getCompiledLibraries().put(libraryId, compiled);
    }

    private String extractRuntimeLocator(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof CqlException cqlEx) {
                SourceLocator sl = cqlEx.getSourceLocator();
                if (sl != null && sl.getSourceLocation() != null) {
                    Location loc = sl.getSourceLocation();
                    return loc.toLocator();
                }
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * Attempt to auto-prefetch all needed FHIR resources for the patient in a single batch request.
     * Parses ELM to find Retrieve data types, then batch-fetches them.
     * Returns null if prefetch fails (caller should fall back to REST provider).
     */
    private RetrieveProvider tryAutoPrefetch(CqlExecutionRequest request, String fhirServerUrl,
            TerminologyProvider terminologyProvider, CqlTranslator translator, String elmJson) {
        try {
            if (elmJson == null) {
                elmJson = translator.toJson();
            }
            Set<String> retrieveTypes = extractRetrieveTypes(elmJson);
            retrieveTypes.add("Patient");

            String patientId = request.getPatientId();
            if (patientId.startsWith("Patient/")) {
                patientId = patientId.substring("Patient/".length());
            }
            com.cqlplatform.security.InputValidator.requireValidResourceId(patientId);

            log.debug("Auto-prefetch: batch-fetching resource types {} for patient {}", retrieveTypes, patientId);
            long prefetchStart = System.currentTimeMillis();
            java.util.List<org.hl7.fhir.r4.model.Resource> resources =
                    dataProviderService.batchFetchPatientResources(fhirServerUrl, patientId, retrieveTypes);
            long prefetchTime = System.currentTimeMillis() - prefetchStart;
            log.info("Auto-prefetch: {} resources fetched in {}ms", resources.size(), prefetchTime);

            com.cqlplatform.service.cds.PrefetchRetrieveProvider provider =
                    new com.cqlplatform.service.cds.PrefetchRetrieveProvider(resources, patientId);
            provider.setTerminologyProvider(terminologyProvider);
            return provider;
        } catch (Exception e) {
            log.warn("Auto-prefetch failed, falling back to REST provider: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract FHIR resource types from ELM Retrieve nodes.
     * E.g. finds "Observation", "Condition" from Retrieve dataType="{http://hl7.org/fhir}Observation".
     */
    private Set<String> extractRetrieveTypes(String elmJson) {
        Set<String> types = new HashSet<>();
        if (elmJson == null) return types;
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    ELM_MAPPER.readTree(elmJson);
            collectRetrieveTypeNodes(root, types);
        } catch (Exception e) {
            log.warn("Failed to extract retrieve types from ELM: {}", e.getMessage());
        }
        return types;
    }

    private void collectRetrieveTypeNodes(com.fasterxml.jackson.databind.JsonNode node, Set<String> types) {
        if (node == null) return;
        if (node.isObject()) {
            if ("Retrieve".equals(node.path("type").asText(""))) {
                String dataType = node.path("dataType").asText("");
                int braceIdx = dataType.lastIndexOf('}');
                if (braceIdx >= 0) {
                    String type = dataType.substring(braceIdx + 1);
                    if (com.cqlplatform.security.InputValidator.isValidFhirResourceType(type)) {
                        types.add(type);
                    }
                }
            }
            for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = node.elements(); it.hasNext(); ) {
                collectRetrieveTypeNodes(it.next(), types);
            }
        } else if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode child : node) {
                collectRetrieveTypeNodes(child, types);
            }
        }
    }

    private static class InMemoryLibrarySourceProvider implements LibrarySourceProvider {
        private final String libraryName;
        private final String libraryVersion;
        private final String cqlContent;

        public InMemoryLibrarySourceProvider(String name, String version, String content) {
            this.libraryName = name;
            this.libraryVersion = version;
            this.cqlContent = content;
        }

        @Override
        public Source getLibrarySource(VersionedIdentifier libraryIdentifier) {
            if (libraryName.equals(libraryIdentifier.getId()) &&
                    (libraryVersion == null || libraryVersion.equals(libraryIdentifier.getVersion()))) {
                ByteArrayInputStream bais = new ByteArrayInputStream(cqlContent.getBytes(StandardCharsets.UTF_8));
                return CoreKt.buffered(JvmCoreKt.asSource(bais));
            }
            return null;
        }
    }
}
