package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.CqlBuildResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class CqlArtifactBuilderTest {

    private final ExpressionCqlEngine engine = new ExpressionCqlEngine();
    private final CqlArtifactBuilder builder = new CqlArtifactBuilder(engine);

    private Map<String, Object> emptyTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", "And");
        tree.put("name", "And");
        tree.put("conjunction", true);
        tree.put("returnType", "boolean");
        tree.put("childInstances", new ArrayList<>());
        return tree;
    }

    @Test
    void buildCql_emptyTree_shouldProduceValidLibraryHeader() {
        CqlBuildResult result = builder.buildCql(
                "TestLib", "1.0.0",
                emptyTree(), emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        assertThat(result.cql()).contains("library TestLib version '1.0.0'");
        assertThat(result.cql()).contains("using FHIR");
        assertThat(result.cql()).contains("context Patient");
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void buildCql_emptyExclusion_shouldProduceFalseNotNull() {
        // Empty exclusion → "false" so InPopulation = inclusion and not false = inclusion.
        // Using "null" would break CQL three-valued logic: true and not null = null.
        CqlBuildResult result = builder.buildCql(
                "TestLib", "1.0.0",
                emptyTree(), emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        // %n produces \r\n on Windows, so use regex to match any line separator
        assertThat(result.cql()).containsPattern("define \"MeetsExclusionCriteria\":\\R  false");
        assertThat(result.cql()).doesNotContainPattern("define \"MeetsExclusionCriteria\":\\R  null");
    }

    @Test
    void buildCql_unknownElementType_shouldProduceWarning() {
        Map<String, Object> tree = emptyTree();
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "SomeFutureType");
        child.put("name", "MyElement");
        child.put("fields", List.of(Map.of("id", "element_name", "value", "MyElement")));
        List<Map<String, Object>> children = new ArrayList<>();
        children.add(child);
        tree.put("childInstances", children);

        CqlBuildResult result = builder.buildCql(
                "TestLib", "1.0.0",
                tree, emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Unknown element type 'SomeFutureType'"));
    }

    @Test
    void buildCql_lookBackModifier_shouldGenerateC3FLookBackWith6Months() {
        // Build a GenericObservation element with a LookBack 6 months modifier
        Map<String, Object> lookBackModifier = new LinkedHashMap<>();
        lookBackModifier.put("id", "LookBackObservation");
        lookBackModifier.put("cqlTemplate", "LookBackModifier");
        lookBackModifier.put("cqlLibraryFunction", "C3F.ObservationLookBack");
        lookBackModifier.put("values", Map.of("value", "6", "unit", "months"));

        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "GenericObservation_vsac");
        child.put("name", "LDL Cholesterol");
        child.put("returnType", "list_of_observations");
        child.put("fields", List.of(
                Map.of("id", "element_name", "value", "LDL Cholesterol",
                        "valueSets", List.of(Map.of("name", "LDL Cholesterol")))
        ));
        child.put("modifiers", List.of(lookBackModifier));

        Map<String, Object> includeTree = emptyTree();
        ((List<Map<String, Object>>) includeTree.get("childInstances")).add(child);

        CqlBuildResult result = builder.buildCql(
                "LookBackTest", "1.0.0",
                includeTree, emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        // LookBack should delegate to C3F library function with quantity literal
        assertThat(result.cql()).contains("C3F.ObservationLookBack(");
        assertThat(result.cql()).contains("6 months");
        // Full pattern: C3F.ObservationLookBack([Observation: "LDL Cholesterol"], 6 months)
        assertThat(result.cql()).matches("(?s).*C3F\\.ObservationLookBack\\(\\[Observation: \"LDL Cholesterol\"\\], 6 months\\).*");
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void buildCql_lookBackModifier_emptyValue_shouldOmitQuantity() {
        // When value is empty, LookBack should fall back to single-arg form
        Map<String, Object> lookBackModifier = new LinkedHashMap<>();
        lookBackModifier.put("id", "LookBackObservation");
        lookBackModifier.put("cqlTemplate", "LookBackModifier");
        lookBackModifier.put("cqlLibraryFunction", "C3F.ObservationLookBack");
        lookBackModifier.put("values", Map.of("value", "", "unit", "months"));

        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "GenericObservation_vsac");
        child.put("name", "BP");
        child.put("fields", List.of(
                Map.of("id", "element_name", "value", "BP",
                        "valueSets", List.of(Map.of("name", "Blood Pressure")))
        ));
        child.put("modifiers", List.of(lookBackModifier));

        Map<String, Object> includeTree = emptyTree();
        ((List<Map<String, Object>>) includeTree.get("childInstances")).add(child);

        CqlBuildResult result = builder.buildCql(
                "LookBackEmpty", "1.0.0",
                includeTree, emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        // Should fall back to single-arg: C3F.ObservationLookBack(expr)
        assertThat(result.cql()).contains("C3F.ObservationLookBack(");
        assertThat(result.cql()).doesNotContain("6 months");
    }

    @Test
    void buildCql_ageRangeOnlyMin_shouldNotProduceNullReference() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "AgeRange");
        child.put("name", "Age");
        child.put("fields", List.of(
                Map.of("id", "element_name", "value", "Age"),
                Map.of("id", "min_age", "value", "18"),
                Map.of("id", "unit_of_time", "value", "years")
                // max_age intentionally omitted
        ));

        Map<String, Object> includeTree = emptyTree();
        ((List<Map<String, Object>>) includeTree.get("childInstances")).add(child);

        CqlBuildResult result = builder.buildCql(
                "AgeMinOnly", "1.0.0",
                includeTree, emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        assertThat(result.cql()).contains("AgeInYears() >= 18");
        assertThat(result.cql()).doesNotContain("null");
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void buildCql_ageRangeBothBounds_shouldWrapInParentheses() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "AgeRange");
        child.put("name", "Age");
        child.put("fields", List.of(
                Map.of("id", "element_name", "value", "Age"),
                Map.of("id", "min_age", "value", "18"),
                Map.of("id", "max_age", "value", "65"),
                Map.of("id", "unit_of_time", "value", "years")
        ));

        Map<String, Object> includeTree = emptyTree();
        ((List<Map<String, Object>>) includeTree.get("childInstances")).add(child);

        CqlBuildResult result = builder.buildCql(
                "AgeBoth", "1.0.0",
                includeTree, emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        // Both bounds → should be parenthesized
        assertThat(result.cql()).contains("(AgeInYears() >= 18 and AgeInYears() <= 65)");
    }

    @Test
    void currentBaseElements_instanceField_shouldNotExist() {
        // Structural assertion: the mutable instance field has been removed
        List<String> instanceFieldNames = new ArrayList<>();
        for (Field f : CqlArtifactBuilder.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                instanceFieldNames.add(f.getName());
            }
        }
        assertThat(instanceFieldNames).doesNotContain("currentBaseElements");
    }
}
