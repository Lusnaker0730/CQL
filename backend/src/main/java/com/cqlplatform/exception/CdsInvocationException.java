package com.cqlplatform.exception;

import lombok.Getter;

/**
 * Thrown internally by CdsInvocationService when any phase of CDS invocation fails.
 * Carries the phase label so the caller can surface structured error info to clients.
 * Not handled by GlobalExceptionHandler — CdsInvocationService catches this itself
 * and converts to a CdsResponse with debug info.
 */
@Getter
public class CdsInvocationException extends RuntimeException {

    public enum Phase {
        PREFETCH_RESOLUTION,
        PREFETCH_PROVIDER_BUILD,
        CQL_TRANSLATION,
        CQL_EXECUTION,
        CARD_GENERATION,
        UNKNOWN
    }

    private final Phase phase;

    public CdsInvocationException(Phase phase, String message, Throwable cause) {
        super(message, cause);
        this.phase = phase;
    }

    public CdsInvocationException(Phase phase, Throwable cause) {
        super(cause.getMessage(), cause);
        this.phase = phase;
    }
}
