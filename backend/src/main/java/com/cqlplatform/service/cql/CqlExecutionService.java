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

    /**
     * Resolves a stored, authenticated EhrConnection when a request carries a
     * {@code connectionId} (Phase 1 — clinic executes against its own secured FHIR).
     * Field-injected (required=false) so the existing integration-test constructors,
     * which don't need it, keep working; it is only used when connectionId is set.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.cqlplatform.service.fhir.EhrConnectionService ehrConnectionService;

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
        // createContext gives us a handle to the DB provider so we can suppress it
        // for the library we're about to translate — the fresh source is the truth,
        // not whatever happens to sit in cql_library with the same name+version.
        LibraryManagerFactory.LibraryContext libCtx = LibraryManagerFactory.createContext(libraryRepository);
        LibraryManager libraryManager = libCtx.libraryManager;
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
            // Defense-in-depth (BUG-107 regression lock): even if the compiled-library
            // cache is cleared or bypassed by a future engine-level lookup, the DB
            // provider will refuse to hand back its stored copy of THIS library id.
            // Other libraries the CQL includes (e.g. DFLR, FHIRHelpers) remain
            // resolvable from DB normally.
            if (libCtx.databaseProvider != null) {
                libCtx.databaseProvider.excludeIdentifier(libraryId);
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

            // Phase 1: resolve an authenticated EhrConnection when the request targets one.
            // When present, the FHIR URL + credentials come from the connection.
            com.cqlplatform.entity.EhrConnectionEntity connection = resolveConnection(request);

            String fhirServerUrl = connection != null ? connection.getFhirServerUrl()
                    : (request.getFhirServerUrl() != null ? request.getFhirServerUrl() : defaultFhirServerUrl);
            log.debug("Using FHIR server URL: {} (authenticated connection={})",
                    fhirServerUrl, connection != null ? connection.getId() : "none");

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
            } else if (connection != null) {
                // Authenticated clinic connection: retrieve directly via the authenticated
                // REST client. The batch auto-prefetch optimisation is not yet wired for
                // connections (Phase 1 follow-up) — correctness is unaffected, only per-
                // retrieve batching.
                retrieveProvider = dataProviderService.createDataProvider(fhirServerUrl, terminologyProvider, connection);
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

            // Per-expression wall-clock timings captured during the per-expression fallback
            // path (if batch eval throws). Empty in the common batch-eval path — there we
            // can't measure individual timings without breaking the retrieve cache, so
            // traces show 0ms per expression and totalTimeMs carries the authoritative total.
            Map<String, Long> perExpressionTimings = new HashMap<>();

            // Batch eval for BOTH normal and debug mode. The previous debug-mode path
            // evaluated each expression in its own engine.evaluate() call so it could
            // measure individual timings — but the CQL engine doesn't preserve its
            // retrieve cache across separate evaluate() calls, so every expression that
            // referenced the same [Observation: valueset] triggered a fresh FHIR fetch.
            // With a BMI CDS hook that has 4 expressions each referencing one retrieve,
            // authors saw the same Observation listed 4-10 times in the debug panel and
            // the origin FHIR server took 4-10× the traffic in debug mode vs prod.
            //
            // The unified batch approach matches prod behavior exactly and dedupes
            // retrieves naturally. Individual expression timing isn't measurable in
            // batch mode (set to 0 below); totalTimeMs captures the wall-clock total.
            // If batch eval fails, the per-expression fallback path runs and restores
            // individual timings — rare case, acceptable trade-off.
            {
                // Normal mode + debug mode: evaluate all expressions at once
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
                    log.warn("Batch CQL evaluation failed ({}), falling back to per-expression evaluation: {}",
                            batchEx.getClass().getSimpleName(), batchEx.getMessage(), batchEx);
                    batchFailed = true;
                }

                if (batchFailed || evaluationResult == null) {
                    if (evaluationResult == null && !batchFailed) {
                        log.warn("CQL engine returned null EvaluationResult for library, falling back to per-expression evaluation");
                    }
                    for (String expressionName : expressions) {
                        long exprStart = System.currentTimeMillis();
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
                        } finally {
                            perExpressionTimings.put(expressionName, System.currentTimeMillis() - exprStart);
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

            // Build expression traces for debug mode. Done post-eval so the same
            // loop serves both the batch-success path and the per-expression-fallback
            // path. Individual timings come from perExpressionTimings (populated only
            // in the fallback path); batch-success path leaves them at 0 to signal
            // "not measured — see totalTimeMs". This is the Option-C unification —
            // previously debug mode ran its own per-expression loop that broke the
            // retrieve cache and produced N× the trace rows + N× the FHIR server hits.
            if (request.isDebugMode()) {
                for (String expressionName : expressions) {
                    ExpressionResult r = results.get(expressionName);
                    String valueType = r != null ? r.getValueType() : "null";
                    String display = r != null ? r.getDisplayValue() : null;
                    long elapsed = perExpressionTimings.getOrDefault(expressionName, 0L);
                    expressionTraces.add(ExpressionTrace.builder()
                            .name(expressionName)
                            .resultType(valueType)
                            .resultDisplay(display)
                            .evaluationTimeMs(elapsed)
                            .order(traceOrder++)
                            .sourceLocator(sourceLocators.get(expressionName))
                            .dependencies(expressionDependencies.getOrDefault(expressionName, List.of()))
                            .build());
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
     * Extract FHIR retrieve types from a pre-translated ELM Library — legacy single-arg
     * variant. Does NOT follow cross-library ExpressionRefs, so libraries that delegate
     * retrieve logic to an included library (e.g. main measure → {@code DFLR."Initial
     * Population"} → {@code [Encounter]}) will report an INCOMPLETE retrieve set,
     * leading to silently-wrong bulk-fetch results (BUG-111).
     *
     * <p>If the library contains any cross-library ExpressionRef / FunctionRef, this
     * method emits a loud WARN to make the inevitable wrong-result visible in
     * operator telemetry. New callers MUST pass a LibraryManager via
     * {@link #extractRetrieveTypesFromLibrary(org.hl7.elm.r1.Library, LibraryManager)}.
     *
     * @deprecated use {@link #extractRetrieveTypesFromLibrary(org.hl7.elm.r1.Library, LibraryManager)}
     * with a non-null {@code LibraryManager}. This overload exists only for test
     * scenarios that deliberately exercise the pre-BUG-111 behavior.
     */
    @Deprecated(forRemoval = true)
    public Set<String> extractRetrieveTypesFromLibrary(org.hl7.elm.r1.Library library) {
        if (hasCrossLibraryReference(library)) {
            log.warn("extractRetrieveTypesFromLibrary(library) called on a library that contains "
                    + "cross-library ExpressionRef/FunctionRef — the returned retrieve-type set is "
                    + "INCOMPLETE and bulk-fetch will silently miss resource types (BUG-111). "
                    + "Call the 2-arg overload with a LibraryManager instead. "
                    + "Library: {}|{}",
                    library.getIdentifier() != null ? library.getIdentifier().getId() : "?",
                    library.getIdentifier() != null ? library.getIdentifier().getVersion() : "?");
        }
        return extractRetrieveTypesFromLibrary(library, null);
    }

    /**
     * Preferred: extract FHIR retrieve types including those reached through cross-library
     * refs. {@code libraryManager} must be the one used to translate {@code library}; its
     * compiled-library cache supplies the included library bodies this walker recurses into.
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

    /**
     * True when the library contains any {@link org.hl7.elm.r1.ExpressionRef} or
     * {@link org.hl7.elm.r1.FunctionRef} whose {@code libraryName} is non-null — i.e. a
     * reference into an included library. Used by the deprecated single-arg
     * {@link #extractRetrieveTypesFromLibrary(org.hl7.elm.r1.Library)} to warn when the
     * result will be silently incomplete.
     */
    private static boolean hasCrossLibraryReference(org.hl7.elm.r1.Library library) {
        if (library == null || library.getStatements() == null
                || library.getStatements().getDef() == null) {
            return false;
        }
        for (org.hl7.elm.r1.ExpressionDef def : library.getStatements().getDef()) {
            if (containsCrossLibRef(def.getExpression())) return true;
        }
        return false;
    }

    private static boolean containsCrossLibRef(org.hl7.elm.r1.Element element) {
        if (element == null) return false;
        if (element instanceof org.hl7.elm.r1.ExpressionRef ref) {
            if (ref.getLibraryName() != null) return true;
            // ExpressionRef without libraryName is same-library — no descent needed.
        }
        if (element instanceof org.hl7.elm.r1.Query query) {
            if (query.getSource() != null) {
                for (var src : query.getSource()) if (containsCrossLibRef(src.getExpression())) return true;
            }
            if (query.getWhere() != null && containsCrossLibRef(query.getWhere())) return true;
        } else if (element instanceof org.hl7.elm.r1.FunctionRef funcRef) {
            if (funcRef.getLibraryName() != null) return true;
            if (funcRef.getOperand() != null) {
                for (var op : funcRef.getOperand()) if (containsCrossLibRef(op)) return true;
            }
        } else if (element instanceof org.hl7.elm.r1.UnaryExpression ue) {
            return containsCrossLibRef(ue.getOperand());
        } else if (element instanceof org.hl7.elm.r1.BinaryExpression be) {
            for (var op : be.getOperand()) if (containsCrossLibRef(op)) return true;
        } else if (element instanceof org.hl7.elm.r1.NaryExpression ne) {
            for (var op : ne.getOperand()) if (containsCrossLibRef(op)) return true;
        }
        return false;
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
            // Mirror doExecute (PAT-066): without a DebugMap the engine's
            // shouldDebug() returns NONE and per-expression runtime exceptions are
            // silently swallowed. The pre-translated path is the one
            // MeasureEvaluationService uses for every patient, so missing this
            // hides per-define errors across the entire measure pipeline (PAT-141).
            org.opencds.cqf.cql.engine.debug.DebugMap debugMap =
                    new org.opencds.cqf.cql.engine.debug.DebugMap();
            debugMap.setLoggingEnabled(true);
            engine.getState().setDebugMap(debugMap);
            long t6 = System.currentTimeMillis();

            Set<String> expressions = determineExpressions(request, elmLibrary);
            Map<String, ExpressionResult> results = new LinkedHashMap<>();
            List<String> runtimeErrors = new ArrayList<>();

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
                log.warn("Batch CQL evaluation failed ({}), falling back to per-expression: {}",
                        batchEx.getClass().getSimpleName(), batchEx.getMessage(), batchEx);
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

            // Harvest engine-captured per-expression exceptions that didn't surface
            // via direct throw (mirrors doExecute line 535-551). Without this, batch
            // evaluation that swallows per-expression failures returns null/partial
            // results and the caller never sees why.
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
            return CqlExecutionResponse.builder()
                    .success(true)
                    .patientId(request.getPatientId())
                    .results(results)
                    .errors(runtimeErrors.isEmpty() ? null : runtimeErrors)
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
            // CqlEngine.Libraries.resolveExpressionRef uses binarySearch — the def
            // list MUST be pre-sorted by name or lookups throw "Could not resolve
            // expression reference". The translator emits defs in source order;
            // only LibraryManager.compileLibrary sorts automatically, so freshly
            // translated libraries we seed manually must sort here.
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
    /**
     * Resolve the request's connectionId to a stored, active EhrConnection, or null when
     * no connectionId is set. Fail-closed: when a connectionId IS given we must never
     * silently fall back to an unauthenticated server, so a missing/inactive connection
     * throws rather than degrading to the default FHIR server.
     */
    private com.cqlplatform.entity.EhrConnectionEntity resolveConnection(CqlExecutionRequest request) {
        if (request.getConnectionId() == null) {
            return null;
        }
        if (ehrConnectionService == null) {
            throw new IllegalStateException("EHR connections are not available in this context");
        }
        com.cqlplatform.entity.EhrConnectionEntity connection =
                ehrConnectionService.getById(request.getConnectionId()); // throws if not found
        if (!connection.isActive()) {
            throw new IllegalArgumentException(
                    "EHR connection " + request.getConnectionId() + " is inactive");
        }
        return connection;
    }

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
