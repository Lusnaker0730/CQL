package com.cqlplatform.service.ai;

import java.util.List;

/**
 * A single entry in the CQL AI knowledge base.
 * Used to enrich the AI fix-suggestion system prompt with targeted
 * explanations + before/after examples matched via keyword scoring.
 *
 * <p>Source: {@code backend/src/main/resources/ai/cql-knowledge/*.yaml}
 */
public record KnowledgeEntry(
        String id,
        String topic,
        String severity,
        List<String> keywords,
        String explanation,
        List<Example> examples
) {

    public record Example(String title, String bad, String good) {}
}
