package com.cqlplatform.service.cql;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class CqlFormatterService {

    private static final Set<String> SECTION_KEYWORDS = Set.of(
            "define", "valueset", "codesystem", "code", "concept", "parameter", "context"
    );

    public String format(String cql) {
        if (cql == null) return null;
        if (cql.isBlank()) return cql;

        // Step 1: Normalize line endings
        String result = cql.replace("\r\n", "\n").replace("\r", "\n");

        // Step 2: Collapse consecutive blank lines → max 1
        result = result.replaceAll("\\n{3,}", "\n\n");

        // Step 3: Ensure blank line before section keywords (but not at start of file)
        result = ensureBlankLineBeforeSections(result);

        // Step 4: Indent continuation lines inside define blocks
        result = indentDefineBlocks(result);

        // Step 5: Trim trailing whitespace on each line
        result = trimTrailingWhitespace(result);

        // Step 6: Ensure single trailing newline
        result = result.stripTrailing() + "\n";

        return result;
    }

    private String ensureBlankLineBeforeSections(String cql) {
        String[] lines = cql.split("\n", -1);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].stripLeading();

            if (i > 0 && matchesSectionKeyword(trimmed)) {
                // Check if previous non-empty line already has a blank line before
                if (i >= 1 && !lines[i - 1].isBlank()) {
                    sb.append("\n");
                }
            }
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private boolean matchesSectionKeyword(String trimmedLine) {
        for (String kw : SECTION_KEYWORDS) {
            if (trimmedLine.startsWith(kw + " ") || trimmedLine.startsWith(kw + "\t")
                    || trimmedLine.equals(kw)) {
                return true;
            }
        }
        return false;
    }

    private String indentDefineBlocks(String cql) {
        String[] lines = cql.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inDefine = false;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].stripLeading();

            if (trimmed.startsWith("define ")) {
                inDefine = trimmed.endsWith(":");
                sb.append(trimmed);
            } else if (inDefine) {
                if (trimmed.isEmpty()) {
                    // Blank line ends the define block
                    inDefine = false;
                    sb.append(trimmed);
                } else if (matchesSectionKeyword(trimmed) || trimmed.startsWith("//") && !lines[i].startsWith(" ")) {
                    // New section keyword — exit define block
                    inDefine = false;
                    sb.append(trimmed);
                } else {
                    // Continuation line — ensure 2-space indent
                    if (!lines[i].startsWith("  ") && !lines[i].startsWith("\t")) {
                        sb.append("  ").append(trimmed);
                    } else {
                        sb.append(lines[i]);
                    }
                }
            } else {
                sb.append(lines[i]);
            }
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private String trimTrailingWhitespace(String cql) {
        String[] lines = cql.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i].stripTrailing());
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }
}
