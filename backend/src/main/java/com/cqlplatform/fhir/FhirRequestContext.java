package com.cqlplatform.fhir;

/**
 * PAT-110: ThreadLocal context that carries the active EHR connection identity
 * for the duration of a request so that downstream {@code FhirServerUnavailableException}
 * throws can attach {@code connectionId} + {@code connectionName} to the error
 * envelope without threading these values through 20+ service-method signatures.
 *
 * <p>Convention: entry-point controllers / services that operate on a specific
 * EHR connection (FHIR browser, measure evaluation, CDS invocation, EHR import)
 * call {@link #set(Long, String)} before invoking downstream FHIR-client code
 * and {@link #clear()} in a finally block. Code paths that do not target a
 * specific connection (translation-time VSAC lookups, scheduled health checks)
 * leave the context null — the exception envelope will report {@code connectionId=null}
 * which the frontend renders as a generic "EHR temporarily unavailable" banner.
 *
 * <p>Not propagated across {@code @Async} boundaries by design; bulk import
 * runs on its own executor and should set context explicitly if needed.
 */
public final class FhirRequestContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private FhirRequestContext() {}

    public static void set(Long connectionId, String connectionName) {
        CURRENT.set(new Context(connectionId, connectionName));
    }

    public static Long getConnectionId() {
        Context c = CURRENT.get();
        return c == null ? null : c.connectionId;
    }

    public static String getConnectionName() {
        Context c = CURRENT.get();
        return c == null ? null : c.connectionName;
    }

    public static void clear() {
        CURRENT.remove();
    }

    private record Context(Long connectionId, String connectionName) {}
}
