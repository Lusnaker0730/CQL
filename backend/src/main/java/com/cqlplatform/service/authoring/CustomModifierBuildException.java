package com.cqlplatform.service.authoring;

/**
 * Raised when a custom modifier's structured rules tree fails server-side reconstruction.
 *
 * <p>Surfaced by {@link CustomModifierCqlBuilder}. Validators in the request layer
 * translate this into a 400 {@code ValidationException}; the CQL engine treats it as
 * a generation failure (the modifier is skipped with a warning).
 */
public class CustomModifierBuildException extends RuntimeException {
    public CustomModifierBuildException(String message) {
        super(message);
    }
}
