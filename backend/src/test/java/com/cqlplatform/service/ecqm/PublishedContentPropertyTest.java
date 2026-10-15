package com.cqlplatform.service.ecqm;

import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.ObservationDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.StratifierDefinition;
import com.cqlplatform.testsupport.Fuzz;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-265 — the publish fingerprint ({@link PublishedContent#hash}, PAT-238) under random CQL text and
 * group definitions. It must be blind to what a database round trip or a no-op save can change (line
 * endings, trailing whitespace, null vs. empty lists, JSON serialisation) and sensitive to every
 * semantic change (any character of the CQL, any populated field, the order of populations).
 */
@DisplayName("PAT-265 — publish fingerprint properties")
class PublishedContentPropertyTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<GroupDefinition>> GROUPS = new TypeReference<>() {
    };

    record Content(String cql, List<GroupDefinition> groups) {
    }

    static String randomCql(Random r) {
        int lines = Fuzz.between(r, 1, 12);
        StringBuilder sb = new StringBuilder("library Fuzz version '1.0.0'\n");
        for (int i = 0; i < lines; i++) {
            sb.append("  ".repeat(Fuzz.between(r, 0, 3)))
                    .append(Fuzz.pick(r, "define", "where", "exists", "[Condition]", "\"Initial Population\":", "and", "// 註解", "'E11.9'", "Interval[@2025-01-01, @2025-12-31]"))
                    .append(' ')
                    .append(Fuzz.pick(r, "X", "C", "E.status = 'finished'", "true", ""))
                    .append(Fuzz.chance(r, 0.3) ? "  " : "")
                    .append('\n');
        }
        return sb.toString();
    }

    static String maybe(Random r, String... options) {
        return Fuzz.chance(r, 0.3) ? null : Fuzz.pick(r, options);
    }

    static List<GroupDefinition> randomGroups(Random r) {
        List<GroupDefinition> groups = new ArrayList<>();
        int n = Fuzz.between(r, 0, 3);
        for (int g = 0; g < n; g++) {
            List<PopulationDefinition> populations = new ArrayList<>();
            int p = Fuzz.between(r, 0, 4);
            for (int i = 0; i < p; i++) {
                populations.add(PopulationDefinition.builder()
                        .populationType(maybe(r, "initial-population", "denominator", "numerator", "measure-population"))
                        .criteriaExpression(maybe(r, "Initial Population", "Denominator", "Numerator", "IP " + i))
                        .description(maybe(r, "說明", "desc"))
                        .build());
            }
            List<StratifierDefinition> stratifiers = new ArrayList<>();
            int s = Fuzz.between(r, 0, 2);
            for (int i = 0; i < s; i++) {
                stratifiers.add(StratifierDefinition.builder()
                        .stratifierId(maybe(r, "s1", "gender"))
                        .criteriaExpression(maybe(r, "Stratifier 1", "Gender"))
                        .kind(maybe(r, "criteria", "value"))
                        .associations(Fuzz.chance(r, 0.5) ? null : Fuzz.chance(r, 0.5) ? List.of() : List.of("initial-population"))
                        .build());
            }
            List<ObservationDefinition> observations = new ArrayList<>();
            int o = Fuzz.between(r, 0, 2);
            for (int i = 0; i < o; i++) {
                observations.add(ObservationDefinition.builder()
                        .criteriaExpression(maybe(r, "Measure Observation", "Days"))
                        .aggregateMethod(maybe(r, "average", "percentile", "sum"))
                        .percentileValue(Fuzz.chance(r, 0.5) ? null : (double) Fuzz.between(r, 0, 100))
                        .build());
            }
            groups.add(GroupDefinition.builder()
                    .groupId(maybe(r, "group-1", "g" + g))
                    .description(maybe(r, "群組", ""))
                    .populationBasis(maybe(r, "boolean", "Encounter"))
                    .populations(Fuzz.chance(r, 0.2) ? null : populations)
                    .stratifiers(Fuzz.chance(r, 0.3) ? null : stratifiers)
                    .observations(Fuzz.chance(r, 0.3) ? null : observations)
                    .scoringUnit(maybe(r, "days", "%"))
                    .rateIndex(Fuzz.chance(r, 0.7) ? null : Fuzz.between(r, 1, 1000))
                    .build());
        }
        return groups;
    }

    static Content randomContent(Random r) {
        return new Content(Fuzz.chance(r, 0.05) ? null : randomCql(r), randomGroups(r));
    }

    private static List<GroupDefinition> copy(List<GroupDefinition> groups) throws Exception {
        return JSON.readValue(JSON.writeValueAsString(groups), GROUPS);
    }

    @Test
    @DisplayName("the hash survives line endings, trailing whitespace, null-vs-empty lists and a JSON round trip")
    void hash_isStableUnderRepresentationChanges() {
        Fuzz.forAll(20261050L, 300, PublishedContentPropertyTest::randomContent, (content, r) -> {
            String base = PublishedContent.hash(content.cql(), content.groups());
            String cql = content.cql() == null ? "" : content.cql();
            assertThat(PublishedContent.hash(cql, content.groups())).as("null CQL = empty CQL").isEqualTo(base);
            assertThat(PublishedContent.hash(cql.replace("\n", "\r\n"), content.groups())).as("CRLF").isEqualTo(base);
            assertThat(PublishedContent.hash(cql.replace("\n", "\r"), content.groups())).as("CR").isEqualTo(base);
            assertThat(PublishedContent.hash(cql + "\n\n \t ", content.groups())).as("trailing whitespace").isEqualTo(base);
            assertThat(PublishedContent.hash(cql, copy(content.groups()))).as("JSON round trip").isEqualTo(base);
            assertThat(PublishedContent.hash(cql, null)).as("null groups").isEqualTo(PublishedContent.hash(cql, List.of()));

            List<GroupDefinition> emptied = copy(content.groups());
            for (GroupDefinition g : emptied) {
                if (g.getPopulations() == null) g.setPopulations(new ArrayList<>());
                if (g.getStratifiers() == null) g.setStratifiers(new ArrayList<>());
                if (g.getObservations() == null) g.setObservations(new ArrayList<>());
                if (g.getStratifiers() != null) {
                    for (StratifierDefinition s : g.getStratifiers()) if (s.getAssociations() == null) s.setAssociations(new ArrayList<>());
                }
            }
            assertThat(PublishedContent.hash(cql, emptied)).as("null lists = empty lists").isEqualTo(base);
            assertThat(base).hasSize(64).matches("[0-9a-f]{64}");
        });
    }

    @Test
    @DisplayName("the hash changes with any semantic change: a CQL character, a populated field, the population order")
    void hash_changesWithContent() {
        Fuzz.forAll(20261051L, 400, PublishedContentPropertyTest::randomContent, (content, r) -> {
            String cql = content.cql() == null ? "" : content.cql();
            String base = PublishedContent.hash(cql, content.groups());

            // a character inserted into the CQL (never whitespace at the very end, which is normalised away)
            int at = cql.isEmpty() ? 0 : Fuzz.between(r, 0, cql.length() - 1);
            String edited = cql.substring(0, at) + Fuzz.pick(r, "x", "1", "\"", "(") + cql.substring(at);
            assertThat(PublishedContent.hash(edited, content.groups())).as("CQL edit").isNotEqualTo(base);

            List<GroupDefinition> mutated = copy(content.groups());
            if (mutated.isEmpty()) {
                mutated.add(GroupDefinition.builder().groupId("new").build());
                assertThat(PublishedContent.hash(cql, mutated)).as("added group").isNotEqualTo(base);
                return;
            }
            GroupDefinition group = Fuzz.pick(r, mutated);
            switch (Fuzz.between(r, 0, 4)) {
                case 0 -> {
                    group.setGroupId((group.getGroupId() == null ? "" : group.getGroupId()) + "x");
                    assertThat(PublishedContent.hash(cql, mutated)).as("groupId").isNotEqualTo(base);
                }
                case 1 -> {
                    if (group.getPopulations() == null) group.setPopulations(new ArrayList<>());
                    group.getPopulations().add(PopulationDefinition.builder().populationType("numerator-exclusion").criteriaExpression("Extra").build());
                    assertThat(PublishedContent.hash(cql, mutated)).as("added population").isNotEqualTo(base);
                }
                case 2 -> {
                    List<PopulationDefinition> populations = group.getPopulations();
                    if (populations == null || populations.size() < 2) return;
                    PopulationDefinition a = populations.get(0);
                    PopulationDefinition b = populations.get(populations.size() - 1);
                    if (JSON.writeValueAsString(a).equals(JSON.writeValueAsString(b))) return;
                    Collections.swap(populations, 0, populations.size() - 1);
                    assertThat(PublishedContent.hash(cql, mutated)).as("population order").isNotEqualTo(base);
                }
                case 3 -> {
                    if (group.getObservations() == null || group.getObservations().isEmpty()) return;
                    ObservationDefinition o = group.getObservations().get(0);
                    o.setPercentileValue(o.getPercentileValue() == null ? 90.0 : o.getPercentileValue() + 1);
                    assertThat(PublishedContent.hash(cql, mutated)).as("percentile").isNotEqualTo(base);
                }
                default -> {
                    group.setPopulationBasis(group.getPopulationBasis() == null ? "Procedure" : group.getPopulationBasis() + "x");
                    assertThat(PublishedContent.hash(cql, mutated)).as("population basis").isNotEqualTo(base);
                }
            }
        });
    }
}
