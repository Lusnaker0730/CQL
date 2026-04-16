package com.cqlplatform.service.ai;

import java.util.List;

/**
 * A single entry in the CQL AI knowledge base.
 * Used to enrich the AI fix-suggestion system prompt with targeted
 * explanations + before/after examples matched via keyword scoring.
 *
 * <p>Source: {@code backend/src/main/resources/ai/cql-knowledge/*.yaml}
 *
 * <p>Keywords are stored already lowercased (normalized at load time by
 * {@link CqlKnowledgeBase}) so per-request scoring is an O(n) substring scan
 * with zero string allocations.
 */
public record KnowledgeEntry(
        String id,
        String topic,
        Severity severity,
        List<String> keywords,
        String explanation,
        List<Example> examples
) {

    public record Example(String title, String bad, String good) {}

    public enum Severity {
        HIGH(0), MEDIUM(1), LOW(2);

        private final int rank;

        Severity(int rank) {
            this.rank = rank;
        }

        /** Lower rank = higher priority (used as tiebreaker in KB ranking). */
        public int rank() {
            return rank;
        }

        public static Severity parse(String raw) {
            if (raw == null) return MEDIUM;
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "high" -> HIGH;
                case "low" -> LOW;
                default -> MEDIUM;
            };
        }
    }
}
