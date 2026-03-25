package com.cqlplatform.util;

/**
 * Shared string utility methods.
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * Truncate a string to the given maximum length.
     * Returns null if the input is null.
     */
    public static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
