package com.cqlplatform.util;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Shared security utility methods.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Read the current authenticated username from the SecurityContext.
     * Returns {@code defaultValue} if the authentication is unavailable.
     */
    public static String getCurrentUsername(String defaultValue) {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // SecurityContext not available (e.g. async thread)
        }
        return defaultValue;
    }
}
