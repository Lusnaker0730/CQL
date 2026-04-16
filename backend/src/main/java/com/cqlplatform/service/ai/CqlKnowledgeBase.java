package com.cqlplatform.service.ai;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
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

    /** Default number of top entries returned by {@link #findRelevant}. */
    public static final int DEFAULT_TOP_K = 3;

    private List<KnowledgeEntry> entries = List.of();

    @PostConstruct
    void load() {
        List<KnowledgeEntry> loaded = new ArrayList<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Yaml yaml = new Yaml();
        try {
            Resource[] resources = resolver.getResources(KNOWLEDGE_PATTERN);
            for (Resource resource : resources) {
                loaded.addAll(parseResource(yaml, resource));
            }
            this.entries = Collections.unmodifiableList(loaded);
            log.info("Loaded {} CQL AI knowledge entries from {} files",
                    loaded.size(), resources.length);
        } catch (IOException e) {
            log.error("Failed to scan CQL AI knowledge base — AI fix will use base prompt only", e);
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
                        .thenComparingInt(s -> s.entry.severity().rank()))
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
    private List<KnowledgeEntry> parseResource(Yaml yaml, Resource resource) {
        String filename = resource.getFilename();
        try (InputStream in = resource.getInputStream()) {
            Object root = yaml.load(in);
            if (!(root instanceof List<?> list)) {
                log.warn("Knowledge file {} is not a YAML list — skipping", filename);
                return List.of();
            }
            List<KnowledgeEntry> out = new ArrayList<>(list.size());
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

        // Pre-normalize keywords once at load: lowercase, trim, drop blanks.
        // Stored in this form so per-request scoring is allocation-free.
        List<String> keywords = asStringList(raw.get("keywords")).stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.toLowerCase(Locale.ROOT))
                .toList();

        KnowledgeEntry.Severity severity = KnowledgeEntry.Severity.parse(asString(raw.get("severity")));

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

        return new KnowledgeEntry(id, topic, severity, keywords, explanation.trim(), examples);
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

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
            if (query.contains(kw)) score++;
        }
        return score;
    }
}
