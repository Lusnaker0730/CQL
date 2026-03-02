package com.cqlplatform.service.ai;

import com.cqlplatform.model.CqlTranslationResponse.CqlError;

public final class CqlFixPromptHelper {

    private CqlFixPromptHelper() {}

    public static final String SYSTEM_PROMPT = """
            You are a CQL (Clinical Quality Language) compiler assistant for HL7 FHIR R4.
            Your job: fix exactly ONE compilation error with the smallest possible code change.

            CQL REQUIRED declaration order (must appear in this sequence):
            1. library MyLib version '1.0.0'
            2. using FHIR version '4.0.1'
            3. include FHIRHelpers version '4.0.1' called FHIRHelpers
            4. codesystem "LOINC": 'http://loinc.org'
            5. valueset "VS Name": 'http://example.org/fhir/ValueSet/123'
            6. code "My Code": '12345-6' from "LOINC" display 'Display Text'
            7. parameter "P" default 1
            8. context Patient
            9. define "Expression Name": [expression]

            Key syntax rules:
            - code declarations REQUIRE "from" clause: code "X": '123' from "CodeSystem" display 'Text'
              If "Syntax error at display" appears, the "from" clause is likely missing.
            - include REQUIRES "called" keyword: include Lib version '1.0' called Alias
            - String/URI/version values: single quotes 'text'
            - Identifiers: double quotes "Name"
            - singleton from (list-expr) — input must be a list, not a single value
            - exists(expr) — not exist(expr)
            - Property names are case-sensitive: O.status not O.Status

            If the error is caused by wrong declaration order (e.g. context before codesystem), \
            fix it by moving the misplaced line to the correct position.

            STRICT RULES:
            - Make the SMALLEST change that fixes the error.
            - Do NOT add comments. Do NOT modify lines unrelated to the error.
            - Preserve ALL single quotes, double quotes, whitespace, and formatting exactly.
            - Output the COMPLETE code with your fix applied.""";

    public static String buildPrompt(String cql, CqlError error) {
        String truncatedCql = truncateCql(cql, error.getStartLine());
        String numberedCql = addLineNumbers(truncatedCql);

        return """
                CQL compilation error at line %d, column %d:
                Error: %s
                Type: %s

                ```cql
                %s
                ```

                Fix this error with the smallest change. Do NOT duplicate any lines.

                EXPLANATION:
                [1-2 sentences]

                FIXED_CQL:
                ```cql
                [complete fixed code, no line numbers]
                ```
                """.formatted(
                error.getStartLine() != null ? error.getStartLine() : 0,
                error.getStartColumn() != null ? error.getStartColumn() : 0,
                error.getMessage(),
                error.getErrorType() != null ? error.getErrorType() : "unknown",
                numberedCql
        );
    }

    public static String addLineNumbers(String cql) {
        String[] lines = cql.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%3d| %s\n", i + 1, lines[i]));
        }
        return sb.toString().stripTrailing();
    }

    public static String truncateCql(String cql, Integer errorLine) {
        if (cql.length() <= 8000) {
            return cql;
        }

        String[] lines = cql.split("\n");
        int eLine = (errorLine != null ? errorLine : 1) - 1;

        StringBuilder sb = new StringBuilder();
        int headerEnd = Math.min(30, lines.length);
        for (int i = 0; i < headerEnd; i++) {
            sb.append(lines[i]).append('\n');
        }

        int contextStart = Math.max(headerEnd, eLine - 20);
        int contextEnd = Math.min(lines.length, eLine + 21);

        if (contextStart > headerEnd) {
            sb.append("\n// ... (lines ").append(headerEnd + 1)
              .append("-").append(contextStart).append(" omitted) ...\n\n");
        }

        for (int i = contextStart; i < contextEnd; i++) {
            sb.append(lines[i]).append('\n');
        }

        if (contextEnd < lines.length) {
            sb.append("\n// ... (remaining lines omitted) ...\n");
        }

        return sb.toString();
    }

    public static String extractExplanation(String responseText) {
        int explStart = responseText.indexOf("EXPLANATION:");
        int fixStart = responseText.indexOf("FIXED_CQL:");

        if (explStart == -1) {
            return null;
        }

        int start = explStart + "EXPLANATION:".length();
        int end = fixStart != -1 ? fixStart : responseText.length();
        return responseText.substring(start, end).trim();
    }

    public static String extractCodeBlock(String responseText) {
        String raw = extractRawCodeBlock(responseText);
        if (raw == null) return null;
        return stripLineNumbers(raw);
    }

    private static String extractRawCodeBlock(String responseText) {
        int start = responseText.indexOf("```cql");
        if (start != -1) {
            start = responseText.indexOf('\n', start);
            if (start != -1) {
                int end = responseText.indexOf("```", start + 1);
                if (end != -1) {
                    return responseText.substring(start + 1, end).trim();
                }
            }
        }

        int fixedStart = responseText.indexOf("FIXED_CQL:");
        if (fixedStart != -1) {
            start = responseText.indexOf("```", fixedStart);
            if (start != -1) {
                start = responseText.indexOf('\n', start);
                if (start != -1) {
                    int end = responseText.indexOf("```", start + 1);
                    if (end != -1) {
                        return responseText.substring(start + 1, end).trim();
                    }
                }
            }
        }

        return null;
    }

    private static String stripLineNumbers(String code) {
        String[] lines = code.split("\n", -1);
        boolean hasLineNumbers = lines.length > 1
                && lines[0].matches("^\\s*\\d+\\|.*")
                && lines[1].matches("^\\s*\\d+\\|.*");
        if (!hasLineNumbers) return code;

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.replaceFirst("^\\s*\\d+\\|\\s?", "")).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
