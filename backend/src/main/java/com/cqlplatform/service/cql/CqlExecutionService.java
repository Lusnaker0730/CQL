package com.cqlplatform.service.cql;

import com.cqlplatform.exception.CqlExecutionException;
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
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.opencds.cqf.cql.engine.data.CompositeDataProvider;
import org.opencds.cqf.cql.engine.execution.CqlEngine;
import org.opencds.cqf.cql.engine.execution.Environment;
import org.opencds.cqf.cql.engine.execution.EvaluationResult;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class CqlExecutionService {

    private final FhirDataProviderService dataProviderService;
    private final FhirTerminologyService terminologyService;
    private final Executor cqlExecutionExecutor;
    private final CqlLibraryRepository libraryRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Timer cqlExecutionTimer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cqlExecutionCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cqlExecutionErrorCounter;

    @Value("${fhir.server.url:http://hapi.fhir.org/baseR4}")
    private String defaultFhirServerUrl;

    @Value("${cql.execution.timeout-seconds:30}")
    private int timeoutSeconds;

    public CqlExecutionService(
            FhirDataProviderService dataProviderService,
            FhirTerminologyService terminologyService,
            @Qualifier("cqlExecutionExecutor") Executor cqlExecutionExecutor,
            CqlLibraryRepository libraryRepository) {
        this.dataProviderService = dataProviderService;
        this.terminologyService = terminologyService;
        this.cqlExecutionExecutor = cqlExecutionExecutor;
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

        try {
            CompletableFuture<CqlExecutionResponse> future = CompletableFuture.supplyAsync(
                    () -> doExecute(request, prefetchProvider, startTime), cqlExecutionExecutor);

            CqlExecutionResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            return response;

        } catch (TimeoutException e) {
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            throw new CqlExecutionException("CQL execution timed out after " + timeoutSeconds + "s");
        } catch (ExecutionException e) {
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            Throwable cause = e.getCause();
            if (cause instanceof CqlExecutionException) {
                throw (CqlExecutionException) cause;
            }
            throw new CqlExecutionException("Execution failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            throw new CqlExecutionException("CQL execution was interrupted", e);
        }
    }

    private CqlExecutionResponse doExecute(CqlExecutionRequest request, RetrieveProvider prefetchProvider, long startTime) {
        try {
            // Translate CQL to ELM
            ModelManager modelManager = new ModelManager();
            LibraryManager libraryManager = new LibraryManager(modelManager);

            // Register database provider first so user libraries take precedence
            if (libraryRepository != null) {
                libraryManager.getLibrarySourceLoader()
                        .registerProvider(new DatabaseLibrarySourceProvider(libraryRepository));
            }

            // Register Library Source Provider to load FHIRHelpers from classpath resources
            libraryManager.getLibrarySourceLoader()
                    .registerProvider(new ClasspathLibrarySourceProvider("cql"));

            CqlTranslator translator = CqlTranslator.fromText(request.getCql(), libraryManager);

            org.hl7.elm.r1.Library elmLibrary = translator.toELM();
            org.hl7.elm.r1.VersionedIdentifier libraryId = elmLibrary.getIdentifier();

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

            // Setup data provider - use prefetch if available, otherwise REST
            R4FhirModelResolver modelResolver = new R4FhirModelResolver();
            RetrieveProvider retrieveProvider;
            if (prefetchProvider != null) {
                log.info("Using prefetch data provider for CQL execution");
                retrieveProvider = prefetchProvider;
            } else {
                retrieveProvider = dataProviderService.createDataProvider(fhirServerUrl, terminologyProvider);
            }

            // Wrap in tracing provider when debug mode is enabled
            TracingRetrieveProvider tracingProvider = null;
            if (request.isDebugMode()) {
                tracingProvider = new TracingRetrieveProvider(retrieveProvider);
                retrieveProvider = tracingProvider;
            }

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
                        EvaluationResult evalResult;
                        if (request.getPatientId() != null) {
                            String pid = request.getPatientId();
                            if (!pid.startsWith("Patient/")) pid = "Patient/" + pid;
                            evalResult = engine.evaluate(
                                    elmLibrary.getIdentifier(), singleExpr,
                                    org.apache.commons.lang3.tuple.Pair.of(request.getContextType(), pid),
                                    request.getParameters(), null);
                        } else {
                            evalResult = engine.evaluate(
                                    elmLibrary.getIdentifier(), singleExpr,
                                    null, request.getParameters(), null);
                        }
                        long exprTime = System.currentTimeMillis() - exprStart;

                        org.opencds.cqf.cql.engine.execution.ExpressionResult exprResult =
                                evalResult.expressionResults.get(expressionName);
                        Object value = exprResult != null ? exprResult.value() : null;
                        String valueType = value != null ? value.getClass().getSimpleName() : "null";

                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName)
                                .value(value)
                                .valueType(valueType)
                                .displayValue(formatDisplayValue(value))
                                .build());

                        expressionTraces.add(ExpressionTrace.builder()
                                .name(expressionName)
                                .resultType(valueType)
                                .resultDisplay(formatDisplayValue(value))
                                .evaluationTimeMs(exprTime)
                                .order(traceOrder++)
                                .build());
                    } catch (Exception e) {
                        long exprTime = System.currentTimeMillis() - exprStart;
                        log.warn("Failed to evaluate expression in debug mode: {}", expressionName, e);
                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName)
                                .value(null)
                                .valueType("Error")
                                .displayValue("Error: " + e.getMessage())
                                .build());
                        expressionTraces.add(ExpressionTrace.builder()
                                .name(expressionName)
                                .resultType("Error")
                                .resultDisplay("Error: " + e.getMessage())
                                .evaluationTimeMs(exprTime)
                                .order(traceOrder++)
                                .build());
                    }
                }
            } else {
                // Normal mode: evaluate all expressions at once
                EvaluationResult evaluationResult;
                if (request.getPatientId() != null) {
                    String patientId = request.getPatientId();
                    if (!patientId.startsWith("Patient/")) {
                        patientId = "Patient/" + patientId;
                    }
                    evaluationResult = engine.evaluate(
                            elmLibrary.getIdentifier(),
                            expressions,
                            org.apache.commons.lang3.tuple.Pair.of(request.getContextType(), patientId),
                            request.getParameters(),
                            null);
                } else {
                    evaluationResult = engine.evaluate(
                            elmLibrary.getIdentifier(),
                            expressions,
                            null,
                            request.getParameters(),
                            null);
                }

                for (String expressionName : expressions) {
                    try {
                        org.opencds.cqf.cql.engine.execution.ExpressionResult exprResult = evaluationResult.expressionResults
                                .get(expressionName);
                        Object value = exprResult != null ? exprResult.value() : null;
                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName)
                                .value(value)
                                .valueType(value != null ? value.getClass().getSimpleName() : "null")
                                .displayValue(formatDisplayValue(value))
                                .build());
                    } catch (Exception e) {
                        log.warn("Failed to get result for expression: {}", expressionName, e);
                        results.put(expressionName, ExpressionResult.builder()
                                .name(expressionName)
                                .value(null)
                                .valueType("Error")
                                .displayValue("Error: " + e.getMessage())
                                .build());
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
        public InputStream getLibrarySource(VersionedIdentifier libraryIdentifier) {
            if (libraryName.equals(libraryIdentifier.getId()) &&
                    (libraryVersion == null || libraryVersion.equals(libraryIdentifier.getVersion()))) {
                return new ByteArrayInputStream(cqlContent.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }
    }
}
