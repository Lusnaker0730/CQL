package com.cqlplatform.service.cql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CqlFormatterServiceTest {

    private CqlFormatterService service;

    @BeforeEach
    void setUp() {
        service = new CqlFormatterService();
    }

    @Test
    void format_null_shouldReturnNull() {
        assertThat(service.format(null)).isNull();
    }

    @Test
    void format_blank_shouldReturnAsIs() {
        assertThat(service.format("   ")).isEqualTo("   ");
    }

    @Test
    void format_normalizeLineEndings() {
        String input = "library Test\r\nusing FHIR\r\n";
        String result = service.format(input);
        assertThat(result).doesNotContain("\r");
        assertThat(result).contains("library Test\nusing FHIR\n");
    }

    @Test
    void format_collapseMultipleBlankLines() {
        String input = "library Test\n\n\n\nusing FHIR\n";
        String result = service.format(input);
        // Should collapse to at most one blank line between statements
        assertThat(result).doesNotContain("\n\n\n");
    }

    @Test
    void format_addBlankLineBeforeDefine() {
        String input = "using FHIR version '4.0.1'\ndefine \"InPopulation\":\n  true\n";
        String result = service.format(input);
        // There should be a blank line before the define keyword
        assertThat(result).contains("4.0.1'\n\ndefine");
    }

    @Test
    void format_addBlankLineBeforeValueset() {
        String input = "using FHIR version '4.0.1'\nvalueset \"Diabetes\": 'urn:oid:1.2.3'\n";
        String result = service.format(input);
        assertThat(result).contains("4.0.1'\n\nvalueset");
    }

    @Test
    void format_addBlankLineBeforeContext() {
        String input = "using FHIR version '4.0.1'\ncontext Patient\n";
        String result = service.format(input);
        assertThat(result).contains("4.0.1'\n\ncontext");
    }

    @Test
    void format_indentAfterDefine() {
        String input = "define \"Test\":\ntrue\n";
        String result = service.format(input);
        assertThat(result).contains("define \"Test\":\n  true\n");
    }

    @Test
    void format_preserveExistingIndent() {
        String input = "define \"Test\":\n  true\n";
        String result = service.format(input);
        assertThat(result).contains("define \"Test\":\n  true\n");
    }

    @Test
    void format_preservesCqlContent() {
        String input = "library TestLib version '1.0.0'\n\nusing FHIR version '4.0.1'\n\ndefine \"InPopulation\":\n  true\n";
        String result = service.format(input);
        // Core CQL keywords and content should be preserved
        assertThat(result).contains("library TestLib version '1.0.0'");
        assertThat(result).contains("using FHIR version '4.0.1'");
        assertThat(result).contains("define \"InPopulation\":");
        assertThat(result).contains("true");
    }

    @Test
    void format_trimTrailingWhitespace() {
        String input = "library Test   \nusing FHIR   \n";
        String result = service.format(input);
        assertThat(result).doesNotContain("   \n");
    }

    @Test
    void format_ensureSingleTrailingNewline() {
        String input = "library Test\nusing FHIR\n\n\n";
        String result = service.format(input);
        assertThat(result).endsWith("FHIR\n");
        assertThat(result).doesNotEndWith("FHIR\n\n");
    }
}
