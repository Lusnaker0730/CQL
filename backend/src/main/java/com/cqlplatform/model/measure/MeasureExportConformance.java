package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * What an exported measure package conforms to, and what keeps it from conforming to more
 * (PAT-229). Produced together with the export so the two can never disagree: a profile is
 * listed in {@link #profiles} — and written to {@code Measure.meta.profile} — only when the
 * elements it requires are really there. Nothing is invented to satisfy a profile; a gap
 * becomes an issue the author can act on.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureExportConformance {

    public static final String ERROR = "error";
    public static final String WARNING = "warning";
    public static final String INFO = "info";

    /** Profiles the exported Measure claims (same list as {@code Measure.meta.profile}). */
    @Builder.Default
    private List<String> profiles = new ArrayList<>();

    /** Profiles the primary Library claims. */
    @Builder.Default
    private List<String> libraryProfiles = new ArrayList<>();

    @Builder.Default
    private List<Issue> issues = new ArrayList<>();

    /** Value sets the package references, with whether the full definition could be included. */
    @Builder.Default
    private List<ValueSetStatus> valueSets = new ArrayList<>();

    /** False when FHIR_CANONICAL_BASE is not configured and canonical URLs fall back to a placeholder. */
    private boolean canonicalBaseConfigured;

    /** True when nothing of severity error was found. */
    public boolean isExchangeReady() {
        return issues.stream().noneMatch(i -> ERROR.equals(i.getSeverity()));
    }

    public void add(String severity, String element, String message) {
        issues.add(new Issue(severity, element, message));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Issue {
        /** error | warning | info */
        private String severity;
        /** FHIR element path or measure field the issue is about. */
        private String element;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueSetStatus {
        private String url;
        private String name;
        /** True when the bundle carries the full definition (compose / expansion), not a stub. */
        private boolean included;
        /** Where the definition came from: ig | vsac | none. */
        private String source;
    }
}
