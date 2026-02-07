package com.cqlplatform.service.cql;

import com.cqlplatform.exception.CqlExecutionException;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExecutionMetadata;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class CqlExecutionService {

    private final FhirDataProviderService dataProviderService;
    private final FhirTerminologyService terminologyService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Timer cqlExecutionTimer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cqlExecutionCounter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private Counter cqlExecutionErrorCounter;

    @Value("${fhir.server.url:http://hapi.fhir.org/baseR4}")
    private String defaultFhirServerUrl;

    public CqlExecutionResponse execute(CqlExecutionRequest request) {
        return executeWithProvider(request, null);
    }

    public CqlExecutionResponse executeWithProvider(CqlExecutionRequest request, RetrieveProvider prefetchProvider) {
        log.debug("Executing CQL for patient: {}", request.getPatientId());
        if (cqlExecutionCounter != null) cqlExecutionCounter.increment();
        Timer.Sample sample = cqlExecutionTimer != null ? Timer.start() : null;
        long startTime = System.currentTimeMillis();

        try {
            // Translate CQL to ELM
            ModelManager modelManager = new ModelManager();
            LibraryManager libraryManager = new LibraryManager(modelManager);

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
            CompositeDataProvider compositeProvider = new CompositeDataProvider(modelResolver, retrieveProvider);

            // Create data providers map
            Map<String, org.opencds.cqf.cql.engine.data.DataProvider> dataProviders = new HashMap<>();
            dataProviders.put("http://hl7.org/fhir", compositeProvider);

            // Create environment using libraryManager
            Environment environment = new Environment(libraryManager, dataProviders, terminologyProvider);

            // Create CQL Engine
            CqlEngine engine = new CqlEngine(environment);

            Set<String> expressions = determineExpressions(request, elmLibrary);

            // Evaluate
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

            Map<String, ExpressionResult> results = new LinkedHashMap<>();
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

            long executionTime = System.currentTimeMillis() - startTime;

            CqlExecutionResponse response = CqlExecutionResponse.builder()
                    .success(true)
                    .patientId(request.getPatientId())
                    .results(results)
                    .metadata(ExecutionMetadata.builder()
                            .executionTimeMs(executionTime)
                            .libraryId(libraryId != null ? libraryId.getId() : null)
                            .libraryVersion(libraryId != null ? libraryId.getVersion() : null)
                            .fhirServerUrl(fhirServerUrl)
                            .resourcesRetrieved(dataProviderService.getAndResetRetrieveCount())
                            .build())
                    .build();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
            return response;

        } catch (Exception e) {
            if (cqlExecutionErrorCounter != null) cqlExecutionErrorCounter.increment();
            if (sample != null && cqlExecutionTimer != null) sample.stop(cqlExecutionTimer);
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
