package com.cqlplatform.util;

/**
 * Pure-function escape helpers for CQL string and identifier literals.
 *
 * <p>Extracted from {@code ExpressionCqlEngine} so collaborators that need to emit safe
 * CQL fragments (e.g. {@code CustomModifierCqlBuilder}) can call them without taking an
 * engine dependency — which would create a circular wiring with the engine itself.
 */
public final class CqlEscapeUtil {

    private CqlEscapeUtil() {}

    /**
     * Escape a value for inclusion inside a CQL single-quoted string literal.
     * Strips non-ASCII (engine compatibility) and escapes backslash + apostrophe.
     */
    public static String escapeCqlString(String value) {
        if (value == null) return "";
        String cleaned = stripNonAscii(value);
        return cleaned.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Escape a value for inclusion inside a CQL double-quoted identifier ({@code "name"}).
     * Strips non-ASCII and escapes backslash + double-quote.
     */
    public static String escapeCqlIdentifier(String value) {
        if (value == null) return "";
        String ascii = stripNonAscii(value);
        return ascii.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String stripNonAscii(String value) {
        return value.replaceAll("[^\\x00-\\x7F]", "")
                .replaceAll("\\(\\s*\\)", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}
