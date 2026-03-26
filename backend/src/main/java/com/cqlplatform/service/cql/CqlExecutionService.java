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
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final FhirDataProviderService dataProviderService;
    private final FhirTerminologyService terminologyService;
    private final ExecutorService executorService;
    private final CqlLibraryRepository libraryRepository;

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
                long warnCount = translator.getExceptions().stream()
                        .filter(e -> e.getSeverity() == CqlCompilerException.ErrorSeverity.Warning)
                        .count();
                if (warnCount > 0) {
                    log.warn("CQL translation produced {} warning(s)", warnCount);
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
            ComparableR4FhirModelResolver modelResolver = new ComparableR4FhirModelResolver();
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

            // Create CQL Engine
            CqlEngine engine = new CqlEngine(environment);

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
                        }
                    }
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
                        .build();
            }

            return CqlExecutionResponse.builder()
                    .success(true)
                    .patientId(request.getPatientId())
                    .results(results)
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
        return results.getResultFor(libraryId);
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
