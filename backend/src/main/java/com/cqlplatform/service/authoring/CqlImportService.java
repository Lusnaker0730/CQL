package com.cqlplatform.service.authoring;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.service.cql.CqlTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for importing CQL source code back into an artifact structure.
 * Parses CQL text and ELM to extract library name, version, value sets,
 * codes, define statements, and maps them to the artifact expression tree format.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CqlImportService {

    private final CqlTranslationService translationService;

    private static final Pattern LIBRARY_PATTERN =
            Pattern.compile("library\\s+\"?([\\w_]+)\"?(?:\\s+version\\s+'([^']*)')?");
    private static final Pattern USING_PATTERN =
            Pattern.compile("using\\s+FHIR\\s+version\\s+'([^']*)'");
    private static final Pattern VALUESET_PATTERN =
            Pattern.compile("valueset\\s+\"([^\"]+)\"\\s*:\\s*'([^']*)'");
    private static final Pattern PARAMETER_PATTERN =
            Pattern.compile("parameter\\s+\"([^\"]+)\"\\s+(\\w+)(?:\\s+default\\s+(.+))?");
    private static final Pattern DEFINE_PATTERN =
            Pattern.compile("define\\s+\"([^\"]+)\":\\s*\n\\s*(.+?)(?=\ndefine |\\z)", Pattern.DOTALL);

    /**
     * Import CQL source code into a partial artifact structure.
     * Returns a map that can be used to create an artifact.
     */
    public Map<String, Object> importCql(String cqlContent) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Parse library name and version
        Matcher libMatcher = LIBRARY_PATTERN.matcher(cqlContent);
        if (libMatcher.find()) {
            result.put("name", libMatcher.group(1));
            result.put("version", libMatcher.group(2) != null ? libMatcher.group(2) : "1.0.0");
        } else {
            result.put("name", "ImportedArtifact");
            result.put("version", "1.0.0");
        }

        // Parse FHIR version
        Matcher usingMatcher = USING_PATTERN.matcher(cqlContent);
        if (usingMatcher.find()) {
            result.put("fhirVersion", usingMatcher.group(1));
        } else {
            result.put("fhirVersion", "4.0.1");
        }

        // Extract value sets
        List<Map<String, String>> valueSets = new ArrayList<>();
        Matcher vsMatcher = VALUESET_PATTERN.matcher(cqlContent);
        while (vsMatcher.find()) {
            Map<String, String> vs = new LinkedHashMap<>();
            vs.put("name", vsMatcher.group(1));
            vs.put("oid", vsMatcher.group(2));
            valueSets.add(vs);
        }
        result.put("valueSets", valueSets);

        // Extract parameters
        List<Map<String, Object>> parameters = new ArrayList<>();
        Matcher paramMatcher = PARAMETER_PATTERN.matcher(cqlContent);
        while (paramMatcher.find()) {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("uniqueId", UUID.randomUUID().toString());
            param.put("name", paramMatcher.group(1));
            param.put("type", paramMatcher.group(2));
            if (paramMatcher.group(3) != null) {
                param.put("value", paramMatcher.group(3).trim());
            }
            parameters.add(param);
        }
        result.put("parameters", parameters);

        // Extract define statements
        List<Map<String, Object>> definitions = new ArrayList<>();
        Matcher defMatcher = DEFINE_PATTERN.matcher(cqlContent);
        while (defMatcher.find()) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("name", defMatcher.group(1));
            def.put("expression", defMatcher.group(2).trim());
            definitions.add(def);
        }
        result.put("definitions", definitions);

        // Translate to get metadata and validation
        try {
            CqlTranslationRequest request = new CqlTranslationRequest();
            request.setCql(cqlContent);
            CqlTranslationResponse response = translationService.translate(request);
            result.put("valid", response.isSuccess());
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                result.put("errors", response.getErrors());
            }
            if (response.getMetadata() != null) {
                result.put("metadata", Map.of(
                    "libraryId", response.getMetadata().getLibraryId() != null ? response.getMetadata().getLibraryId() : "",
                    "expressions", response.getMetadata().getExpressions() != null ? response.getMetadata().getExpressions() : List.of(),
                    "includes", response.getMetadata().getIncludes() != null ? response.getMetadata().getIncludes() : List.of()
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to translate imported CQL: {}", e.getMessage());
            result.put("valid", false);
            result.put("errors", List.of(Map.of("message", e.getMessage())));
        }

        result.put("cqlContent", cqlContent);
        return result;
    }
}
