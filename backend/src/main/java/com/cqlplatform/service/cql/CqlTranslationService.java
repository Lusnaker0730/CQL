package com.cqlplatform.service.cql;

import com.cqlplatform.exception.CqlTranslationException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.CqlTranslationResponse.*;
import com.cqlplatform.repository.CqlLibraryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.hl7.elm.r1.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

    public CqlTranslationResponse translate(CqlTranslationRequest request) {
        if (request.getCql() == null || request.getCql().isBlank()) {
            throw new IllegalArgumentException("CQL content must not be null or empty");
        }
        log.debug("Translating CQL: {}", request.getCql().substring(0, Math.min(100, request.getCql().length())));
        if (cqlTranslationCounter != null) cqlTranslationCounter.increment();
        Timer.Sample sample = cqlTranslationTimer != null ? Timer.start() : null;

        try {
            LibraryManager libraryManager = LibraryManagerFactory.create(libraryRepository);

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

            // Extract partial metadata even when there are errors,
            // so the CQL Builder can display already-parsed elements
            TranslationMetadata metadata = extractMetadata(library);

            if (!errors.isEmpty()) {
                if (sample != null && cqlTranslationTimer != null) sample.stop(cqlTranslationTimer);
                return CqlTranslationResponse.builder()
                        .success(false)
                        .errors(errors)
                        .warnings(warnings)
                        .metadata(metadata)
                        .build();
            }

            String elmJson = translator.toJson();

            CqlTranslationResponse response = CqlTranslationResponse.builder()
                    .success(true)
                    .elmJson(elmJson)
                    .errors(errors)
                    .warnings(warnings)
                    .metadata(metadata)
                    .build();
            if (sample != null && cqlTranslationTimer != null) sample.stop(cqlTranslationTimer);
            return response;

        } catch (Exception e) {
            if (cqlTranslationErrorCounter != null) cqlTranslationErrorCounter.increment();
            if (sample != null && cqlTranslationTimer != null) sample.stop(cqlTranslationTimer);
            log.error("Translation failed", e);
            throw new CqlTranslationException("Translation failed: " + e.getMessage());
        }
    }

    @Cacheable(value = "cqlValidation", key = "#cql.hashCode()")
    public CqlTranslationResponse validate(String cql) {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cql);
        return translate(request);
    }

    public CompiledLibrary compile(String cql) {
        try {
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
        return CqlError.builder()
                .severity(exception.getSeverity().toString())
                .message(exception.getMessage())
                .startLine(exception.getLocator() != null ? exception.getLocator().getStartLine() : null)
                .startColumn(exception.getLocator() != null ? exception.getLocator().getStartChar() : null)
                .endLine(exception.getLocator() != null ? exception.getLocator().getEndLine() : null)
                .endColumn(exception.getLocator() != null ? exception.getLocator().getEndChar() : null)
                .errorType(exception.getClass().getSimpleName())
                .build();
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
                            .resultType(stmt.getResultType() != null ? stmt.getResultType().toString() : null)
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
