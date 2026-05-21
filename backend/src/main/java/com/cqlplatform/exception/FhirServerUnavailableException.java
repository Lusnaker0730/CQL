package com.cqlplatform.exception;

import com.cqlplatform.fhir.FhirRequestContext;
import lombok.Getter;

/**
 * Thrown when an upstream FHIR server (real EHR) is unreachable, slow, or
 * returning errors. PAT-110: carries structured degradation metadata so the
 * frontend can render a yellow "EHR temporarily offline" banner distinct from
 * generic platform errors.
 *
 * <p>{@code connectionId} and {@code connectionName} are auto-populated from
 * {@link FhirRequestContext} at throw time — callers do not need to pass them
 * explicitly. When the context is unset (background jobs, translation-time
 * VSAC), both are null and the frontend renders a generic outage banner.
 */
@Getter
public class FhirServerUnavailableException extends RuntimeException {

    /**
     * Degradation reason exposed in the error envelope. Drives the frontend's
     * user-facing message selection ({@code fhir.degradation.reason.*} i18n keys).
     */
    public enum Reason {
        /** Upstream took too long to respond (socket timeout / connect timeout). */
        TIMEOUT,
        /** Resilience4j circuit breaker is open (upstream has been failing repeatedly). */
        CIRCUIT_BREAKER_OPEN,
        /** Upstream returned a 5xx status. */
        UPSTREAM_5XX,
        /** TCP connection refused / host unreachable. */
        CONNECTION_REFUSED,
        /** Upstream reachable but returned malformed response / auth failure / unknown cause. */
        OTHER;

        /**
         * Walk the cause chain (max 10 hops) and classify the failure. Keeps
         * classification logic in one place so fallback methods and catch
         * blocks across FHIR services produce consistent {@code Reason} values.
         */
        public static Reason classify(Throwable t) {
            int depth = 0;
            while (t != null && depth++ < 10) {
                String simple = t.getClass().getSimpleName();
                String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
                if ("CallNotPermittedException".equals(simple)) return CIRCUIT_BREAKER_OPEN;
                if ("SocketTimeoutException".equals(simple) || "TimeoutException".equals(simple)
                        || message.contains("timed out") || message.contains("timeout")) return TIMEOUT;
                if ("ConnectException".equals(simple) || "UnknownHostException".equals(simple)
                        || message.contains("connection refused") || message.contains("unreachable")) return CONNECTION_REFUSED;
                // HAPI FHIR throws InternalErrorException for upstream 5xx.
                if ("InternalErrorException".equals(simple) || message.contains("http 5")) return UPSTREAM_5XX;
                t = t.getCause();
            }
            return OTHER;
        }
    }

    private final Reason reason;
    private final Long connectionId;
    private final String connectionName;

    public FhirServerUnavailableException(String message) {
        this(message, Reason.OTHER, null);
    }

    // No-reason + cause: auto-classify the cause chain. Lets existing throw sites
    // (20+ across FhirDataProviderService / FhirTerminologyService / etc) get a
    // meaningful reason for free without call-site changes.
    public FhirServerUnavailableException(String message, Throwable cause) {
        this(message, Reason.classify(cause), cause);
    }

    public FhirServerUnavailableException(String message, Reason reason) {
        this(message, reason, null);
    }

    public FhirServerUnavailableException(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason == null ? Reason.OTHER : reason;
        this.connectionId = FhirRequestContext.getConnectionId();
        this.connectionName = FhirRequestContext.getConnectionName();
    }
}
