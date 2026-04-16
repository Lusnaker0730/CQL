package com.cqlplatform.service.ai;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory knowledge base loaded from {@code classpath:ai/cql-knowledge/*.yaml}.
 * Provides keyword-based retrieval of the most relevant entries for a given
 * CQL compilation error, used to enrich the AI fix-suggestion system prompt.
 *
 * <p>Loading is best-effort: if a single YAML file is malformed, it is logged
 * and skipped so the app can still start. An empty knowledge base degrades
 * gracefully to the base system prompt.
 */
@Component
@Slf4j
public class CqlKnowledgeBase {

    private static final String KNOWLEDGE_PATTERN = "classpath:ai/cql-knowledge/*.yaml";

    private volatile List<KnowledgeEntry> entries = List.of();

    @PostConstruct
    void load() {
        List<KnowledgeEntry> loaded = new ArrayList<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(KNOWLEDGE_PATTERN);
            for (Resource resource : resources) {
                loaded.addAll(parseResource(resource));
            }
            this.entries = Collections.unmodifiableList(loaded);
            log.info("Loaded {} CQL AI knowledge entries from {} files",
                    loaded.size(), resources.length);
        } catch (Exception e) {
            log.error("Failed to load CQL AI knowledge base — AI fix will use base prompt only", e);
            this.entries = List.of();
        }
    }

    /**
     * Returns up to {@code topK} entries whose keywords appear in the query
     * (error message + CQL snippet). Scoring is a simple hit count, tied on
     * severity (high > medium > low).
     */
    public List<KnowledgeEntry> findRelevant(String errorMessage, String cqlSnippet, int topK) {
        if (entries.isEmpty() || topK <= 0) return List.of();

        String query = ((errorMessage != null ? errorMessage : "") + " "
                + (cqlSnippet != null ? cqlSnippet : ""))
                .toLowerCase(Locale.ROOT);

        record Scored(KnowledgeEntry entry, int score) {}
        return entries.stream()
                .map(e -> new Scored(e, scoreKeywords(e.keywords(), query)))
                .filter(s -> s.score > 0)
                .sorted(Comparator
                        .comparingInt((Scored s) -> s.score).reversed()
                        .thenComparingInt(s -> severityRank(s.entry.severity())))
                .limit(topK)
                .map(Scored::entry)
                .toList();
    }

    /** Visible for tests: returns all loaded entries. */
    public List<KnowledgeEntry> getEntries() {
        return entries;
    }

    // --- Internals ---

    @SuppressWarnings("unchecked")
    private List<KnowledgeEntry> parseResource(Resource resource) {
        String filename = resource.getFilename();
        try (InputStream in = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Object root = yaml.load(in);
            if (!(root instanceof List<?> list)) {
                log.warn("Knowledge file {} is not a YAML list — skipping", filename);
                return List.of();
            }
            List<KnowledgeEntry> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                KnowledgeEntry entry = toEntry((Map<String, Object>) map);
                if (entry != null) out.add(entry);
            }
            return out;
        } catch (Exception e) {
            log.error("Failed to parse knowledge file {} — skipping", filename, e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private KnowledgeEntry toEntry(Map<String, Object> raw) {
        String id = asString(raw.get("id"));
        String topic = asString(raw.get("topic"));
        String explanation = asString(raw.get("explanation"));
        if (id == null || topic == null || explanation == null) return null;

        String severity = asString(raw.get("severity"));
        List<String> keywords = asStringList(raw.get("keywords"));

        List<KnowledgeEntry.Example> examples = new ArrayList<>();
        Object exObj = raw.get("examples");
        if (exObj instanceof List<?> exList) {
            for (Object ex : exList) {
                if (ex instanceof Map<?, ?> exMap) {
                    Map<String, Object> m = (Map<String, Object>) exMap;
                    examples.add(new KnowledgeEntry.Example(
                            asString(m.get("title")),
                            asString(m.get("bad")),
                            asString(m.get("good"))));
                }
            }
        }

        return new KnowledgeEntry(id, topic, severity == null ? "medium" : severity,
                keywords, explanation.trim(), examples);
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) out.add(item.toString());
            }
            return out;
        }
        return List.of();
    }

    private static int scoreKeywords(List<String> keywords, String query) {
        int score = 0;
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && query.contains(kw.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        return score;
    }

    /** Lower rank = higher priority. */
    private static int severityRank(String severity) {
        if (severity == null) return 2;
        return switch (severity.toLowerCase(Locale.ROOT)) {
            case "high" -> 0;
            case "medium" -> 1;
            case "low" -> 2;
            default -> 3;
        };
    }
}
