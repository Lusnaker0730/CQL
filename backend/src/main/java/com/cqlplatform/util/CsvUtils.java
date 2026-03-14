package com.cqlplatform.util;

/**
 * CSV encoding utilities with formula injection prevention.
 * <p>
 * Spreadsheet applications (Excel, Google Sheets, LibreOffice Calc) interpret
 * cells starting with {@code =}, {@code +}, {@code -}, {@code @}, {@code \t},
 * or {@code \r} as formulas.  Malicious user-controlled data in CSV exports
 * can exploit this to execute arbitrary commands when opened.
 * <p>
 * This utility neutralises dangerous prefixes <b>after</b> applying standard
 * RFC 4180 quoting so the prefix character is never the first character that
 * the spreadsheet parser sees.
 */
public final class CsvUtils {

    private CsvUtils() {
    }

    /**
     * Escape a single value for safe inclusion in a CSV cell.
     * <ol>
     *   <li>Null → empty string.</li>
     *   <li>Values containing {@code ,}, {@code "}, or {@code \n} are
     *       double-quoted per RFC 4180 (internal {@code "} doubled).</li>
     *   <li>If the resulting cell text starts with a formula-trigger character
     *       ({@code = + - @ \t \r}), a tab prefix is prepended <b>inside</b>
     *       the quotes so that the spreadsheet never interprets it as a
     *       formula.</li>
     * </ol>
     */
    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n");

        if (needsQuoting) {
            // RFC 4180 quoting: wrap in double-quotes, escape internal quotes
            String escaped = value.replace("\"", "\"\"");
            // Inject tab prefix inside quotes if the value starts with a formula char
            if (isFormulaTrigger(escaped)) {
                return "\"\\t" + escaped + "\"";
            }
            return "\"" + escaped + "\"";
        }

        // No quoting needed — but still guard against formula injection
        if (isFormulaTrigger(value)) {
            return "\\t" + value;
        }

        return value;
    }

    private static boolean isFormulaTrigger(String s) {
        if (s.isEmpty()) {
            return false;
        }
        char first = s.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
    }
}
