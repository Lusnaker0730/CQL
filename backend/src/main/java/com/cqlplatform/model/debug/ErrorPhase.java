package com.cqlplatform.model.debug;

/**
 * Canonical phase labels shared across CDS invocation, CQL editor execute,
 * and eCQM measure evaluation debug responses. Wire format is the lowercased
 * enum name (e.g. "cql_translation") — changing a value breaks the API
 * contract for every consumer.
 *
 * <p>Added phases compared to the original CDS-only enum:
 * <ul>
 *   <li>{@link #FHIR_RETRIEVAL} — FHIR server fetch failed (distinct from prefetch which is CDS-only).
 *   <li>{@link #POPULATION_EVALUATION} — eCQM aggregation / population membership failure.
 * </ul>
 */
public enum ErrorPhase {
    PREFETCH_RESOLUTION,
    PREFETCH_PROVIDER_BUILD,
    FHIR_RETRIEVAL,
    CQL_TRANSLATION,
    CQL_EXECUTION,
    POPULATION_EVALUATION,
    CARD_GENERATION,
    UNKNOWN;

    /** Lowercased enum name — the wire format used in JSON responses. */
    public String wireName() {
        return name().toLowerCase();
    }
}
