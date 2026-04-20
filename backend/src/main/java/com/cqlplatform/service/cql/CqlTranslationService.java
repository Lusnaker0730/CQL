package com.cqlplatform.service.cql;

import com.cqlplatform.exception.CqlTranslationException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.CqlTranslationResponse.*;
import com.cqlplatform.repository.CqlLibraryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.hl7.elm.r1.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CqlTranslationService {

    @Autowired(required = false)
    private CqlLibraryRepository libraryRepository;

    @Autowired(required = false)
    private Timer cqlTranslationTimer;

    @Autowired(required = false)
    private Counter cqlTranslationCounter;

    @Autowired(required = false)
    private Counter cqlTranslationErrorCounter;

    @org.springframework.beans.factory.annotation.Value("${cql.execution.translation-timeout-seconds:30}")
    private int translationTimeoutSeconds;

    private static final ExecutorService TRANSLATION_EXECUTOR =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "cql-translate");
                t.setDaemon(true);
                return t;
            });

    public CqlTranslationResponse translate(CqlTranslationRequest request) {
        if (request.getCql() == null || request.getCql().isBlank()) {
            throw new IllegalArgumentException("CQL content must not be null or empty");
        }
        log.debug("Translating CQL: {}", request.getCql().substring(0, Math.min(100, request.getCql().length())));
        if (cqlTranslationCounter != null) cqlTranslationCounter.increment();
        Timer.Sample sample = cqlTranslationTimer != null ? Timer.start() : null;

        Future<CqlTranslationResponse> future = TRANSLATION_EXECUTOR.submit(
                () -> doTranslate(request));
        try {
            CqlTranslationResponse response = future.get(translationTimeoutSeconds, TimeUnit.SECONDS);
            if (sample != null && cqlTranslationTimer != null) sample.stop(cqlTranslationTimer);
            return response;
        } catch (TimeoutException e) {
            future.cancel(true);
            if (cqlTranslationErrorCounter != null) cqlTranslationErrorCounter.increment();
            if (sample != null && cqlTranslationTimer != null) sample.stop(cqlTranslationTimer);
            throw new CqlTranslationException("Translation timed out after " + translationTimeoutSeconds + "s");
        } catch (ExecutionException e) {
            if (cqlTranslationErrorCounter != null) cqlTranslationErrorCounter.increment();
            if (sample != null && cqlTranslationTimer != null) sample.stop(cqlTranslationTimer);
            Throwable cause = e.getCause();
            if (cause instanceof CqlTranslationException cte) throw cte;
            throw new CqlTranslationException("Translation failed: " + cause.getMessage());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            if (cqlTranslationErrorCounter != null) cqlTranslationErrorCounter.increment();
            if (sample != null && cqlTranslationTimer != null) sample.stop(cqlTranslationTimer);
            throw new CqlTranslationException("Translation was interrupted");
        }
    }

    private CqlTranslationResponse doTranslate(CqlTranslationRequest request) {
        try {
            CqlCompilerOptions options = LibraryManagerFactory.buildOptions(
                    request.isEnableLocators(),
                    request.isEnableAnnotations(),
                    request.isEnableResultTypes(),
                    request.isValidateUnits());
            LibraryManager libraryManager = LibraryManagerFactory.create(libraryRepository, options);

            CqlTranslator translator = CqlTranslator.fromText(request.getCql(), libraryManager);
            Library library = translator.toELM();

            List<CqlError> errors = new ArrayList<>();
            List<CqlError> warnings = new ArrayList<>();

            for (CqlCompilerException exception : translator.getExceptions()) {
                CqlError error = mapTranslatorException(exception);
                if (exception.getSeverity() == CqlCompilerException.ErrorSeverity.Error) {
                    errors.add(error);
                } else {
                    warnings.add(error);
                }
            }

            TranslationMetadata metadata = extractMetadata(library);

            if (!errors.isEmpty()) {
                return CqlTranslationResponse.builder()
                        .success(false)
                        .errors(errors)
                        .warnings(warnings)
                        .metadata(metadata)
                        .build();
            }

            String elmJson = translator.toJson();
            return CqlTranslationResponse.builder()
                    .success(true)
                    .elmJson(elmJson)
                    .errors(errors)
                    .warnings(warnings)
                    .metadata(metadata)
                    .build();
        } catch (Exception e) {
            log.error("Translation failed", e);
            throw new CqlTranslationException("Translation failed: " + e.getMessage());
        }
    }

    @Cacheable(value = "cqlValidation", key = "T(com.cqlplatform.service.cql.CqlTranslationService).normalizeCacheKey(#cql)")
    public CqlTranslationResponse validate(String cql) {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cql);
        return translate(request);
    }

    /**
     * Normalize CQL content for cache key generation.
     * Collapses whitespace to avoid cache misses on formatting-only changes.
     */
    public static String normalizeCacheKey(String cql) {
        if (cql == null) return "";
        return String.valueOf(cql.replaceAll("\\s+", " ").trim().hashCode());
    }

    public CompiledLibrary compile(String cql) {
        try {
            LibraryManager libraryManager = LibraryManagerFactory.create(libraryRepository);
            CqlTranslator translator = CqlTranslator.fromText(cql, libraryManager);

            if (translator.getExceptions().stream()
                    .anyMatch(e -> e.getSeverity() == CqlCompilerException.ErrorSeverity.Error)) {
                List<CqlError> errors = translator.getExceptions().stream()
                        .filter(e -> e.getSeverity() == CqlCompilerException.ErrorSeverity.Error)
                        .map(this::mapTranslatorException)
                        .collect(Collectors.toList());
                throw new CqlTranslationException("Compilation failed", errors);
            }

            return translator.getTranslatedLibrary();
        } catch (CqlTranslationException e) {
            throw e;
        } catch (Exception e) {
            throw new CqlTranslationException("Compilation failed: " + e.getMessage());
        }
    }

    private CqlError mapTranslatorException(CqlCompilerException exception) {
        String baseMessage = exception.getMessage();
        String hint = hintForError(baseMessage);
        String finalMessage = hint != null ? baseMessage + "\n\nHint: " + hint : baseMessage;
        return CqlError.builder()
                .severity(exception.getSeverity().toString())
                .message(finalMessage)
                .startLine(exception.getLocator() != null ? exception.getLocator().getStartLine() : null)
                .startColumn(exception.getLocator() != null ? exception.getLocator().getStartChar() : null)
                .endLine(exception.getLocator() != null ? exception.getLocator().getEndLine() : null)
                .endColumn(exception.getLocator() != null ? exception.getLocator().getEndChar() : null)
                .errorType(exception.getClass().getSimpleName())
                .build();
    }

    /**
     * Generates a human-friendly hint for common CQL translator error patterns.
     * The translator's own messages are spec-precise but often opaque to a CDS
     * author (e.g. "Could not resolve call to operator Equivalent with signature
     * (FHIR.CodeableConcept, System.String)" — technically correct, but the author
     * just wanted to compare a condition's clinicalStatus to 'active'). Returning
     * a hint alongside saves them from googling the operator overload rules.
     *
     * <p>Pattern-match narrowly — only add hints for errors we've seen users actually
     * hit. Over-eager hinting on rare patterns would flood output. Returns null when
     * no hint applies (most errors).
     *
     * <p>Package-visible for unit testing.
     */
    static String hintForError(String errorMessage) {
        if (errorMessage == null) return null;

        // Pattern: X.clinicalStatus ~ 'active' — comparing CodeableConcept directly to
        // a string literal. CDS smoke scenario 11 hit this. Translator rejects the overload;
        // user wanted code-level comparison.
        if (errorMessage.contains("Could not resolve call to operator Equivalent")
                && errorMessage.contains("FHIR.CodeableConcept")
                && errorMessage.contains("System.String")) {
            return "CodeableConcept can't be compared to a string with '~' directly. "
                    + "Extract a code first: e.g. `X.clinicalStatus.coding[0].code ~ 'active'`, "
                    + "or compare against a declared Code (preferred): "
                    + "`X.clinicalStatus ~ Code 'active' from \"SomeSystem\"`.";
        }

        // Same pattern on Coding (less common but analogous). Coding has .code and .system.
        if (errorMessage.contains("Could not resolve call to operator Equivalent")
                && errorMessage.contains("FHIR.Coding")
                && errorMessage.contains("System.String")) {
            return "Coding can't be compared to a string with '~' directly. "
                    + "Compare the embedded code: `X.code ~ 'value'` (where X is a Coding).";
        }

        return null;
    }

    private TranslationMetadata extractMetadata(Library library) {
        List<String> usings = new ArrayList<>();
        if (library.getUsings() != null && library.getUsings().getDef() != null) {
            usings = library.getUsings().getDef().stream()
                    .map(u -> u.getLocalIdentifier() + " version " + u.getVersion())
                    .collect(Collectors.toList());
        }

        List<String> includes = new ArrayList<>();
        if (library.getIncludes() != null && library.getIncludes().getDef() != null) {
            includes = library.getIncludes().getDef().stream()
                    .map(i -> i.getLocalIdentifier() + " (" + i.getPath() + " version " + i.getVersion() + ")")
                    .collect(Collectors.toList());
        }

        List<String> parameters = new ArrayList<>();
        if (library.getParameters() != null && library.getParameters().getDef() != null) {
            parameters = library.getParameters().getDef().stream()
                    .map(ParameterDef::getName)
                    .collect(Collectors.toList());
        }

        List<String> valueSets = new ArrayList<>();
        if (library.getValueSets() != null && library.getValueSets().getDef() != null) {
            valueSets = library.getValueSets().getDef().stream()
                    .map(ValueSetDef::getName)
                    .collect(Collectors.toList());
        }

        List<String> codes = new ArrayList<>();
        if (library.getCodes() != null && library.getCodes().getDef() != null) {
            codes = library.getCodes().getDef().stream()
                    .map(CodeDef::getName)
                    .collect(Collectors.toList());
        }

        List<String> concepts = new ArrayList<>();
        if (library.getConcepts() != null && library.getConcepts().getDef() != null) {
            concepts = library.getConcepts().getDef().stream()
                    .map(ConceptDef::getName)
                    .collect(Collectors.toList());
        }

        List<ExpressionInfo> expressions = new ArrayList<>();
        if (library.getStatements() != null && library.getStatements().getDef() != null) {
            expressions = library.getStatements().getDef().stream()
                    .map(stmt -> ExpressionInfo.builder()
                            .name(stmt.getName())
                            .context(stmt.getContext())
                            .accessLevel(stmt.getAccessLevel() != null ? stmt.getAccessLevel().value() : "Public")
                            .resultType(stmt.getResultTypeName() != null ? stmt.getResultTypeName().getLocalPart() : null)
                            .build())
                    .collect(Collectors.toList());
        }

        return TranslationMetadata.builder()
                .libraryId(library.getIdentifier() != null ? library.getIdentifier().getId() : null)
                .libraryVersion(library.getIdentifier() != null ? library.getIdentifier().getVersion() : null)
                .usings(usings)
                .includes(includes)
                .parameters(parameters)
                .valueSets(valueSets)
                .codes(codes)
                .concepts(concepts)
                .expressions(expressions)
                .build();
    }
}
