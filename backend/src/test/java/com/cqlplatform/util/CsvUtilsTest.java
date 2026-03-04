package com.cqlplatform.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUtilsTest {

    @Test
    void null_shouldReturnEmpty() {
        assertThat(CsvUtils.escapeCsv(null)).isEmpty();
    }

    @Test
    void empty_shouldReturnEmpty() {
        assertThat(CsvUtils.escapeCsv("")).isEmpty();
    }

    @Test
    void plainValue_shouldPassThrough() {
        assertThat(CsvUtils.escapeCsv("hello")).isEqualTo("hello");
    }

    @Test
    void valueWithComma_shouldBeQuoted() {
        assertThat(CsvUtils.escapeCsv("a,b")).isEqualTo("\"a,b\"");
    }

    @Test
    void valueWithDoubleQuote_shouldBeQuotedAndEscaped() {
        assertThat(CsvUtils.escapeCsv("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    }

    @Test
    void valueWithNewline_shouldBeQuoted() {
        assertThat(CsvUtils.escapeCsv("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    // --- Formula injection prevention ---

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1+1", "-1+1", "@SUM(A1)", "\tcmd", "\rcmd"})
    void formulaTrigger_plain_shouldBePrefixed(String input) {
        String result = CsvUtils.escapeCsv(input);
        // Must not start with the formula trigger character
        assertThat(result.charAt(0)).isNotIn('=', '+', '-', '@', '\t', '\r');
        // Should contain the original text
        assertThat(result).contains(input);
    }

    @Test
    void formulaTrigger_withComma_shouldBeQuotedAndPrefixed() {
        // =cmd|'/C calc'!A0,evil — has both formula prefix and comma
        String input = "=cmd|'/C calc'!A0,evil";
        String result = CsvUtils.escapeCsv(input);
        // Must be double-quoted (contains comma)
        assertThat(result).startsWith("\"");
        assertThat(result).endsWith("\"");
        // The first char inside quotes must NOT be '='
        String insideQuotes = result.substring(1, result.length() - 1);
        assertThat(insideQuotes.charAt(0)).isNotEqualTo('=');
    }

    @Test
    void formulaTrigger_withQuoteAndComma_shouldEscapeAll() {
        // =HYPERLINK("http://evil.com","click"),data
        String input = "=HYPERLINK(\"http://evil.com\",\"click\"),data";
        String result = CsvUtils.escapeCsv(input);
        // Must be quoted and prefix-neutralised
        assertThat(result).startsWith("\"");
        // Internal quotes must be doubled
        assertThat(result).contains("\"\"");
        // The effective first character must not be '='
        String insideQuotes = result.substring(1, result.length() - 1);
        assertThat(insideQuotes.charAt(0)).isNotEqualTo('=');
    }

    @Test
    void negativeNumber_shouldBePrefixed() {
        // Negative numbers start with '-' which is a formula trigger
        String result = CsvUtils.escapeCsv("-42");
        assertThat(result).contains("-42");
        assertThat(result.charAt(0)).isNotEqualTo('-');
    }
}
