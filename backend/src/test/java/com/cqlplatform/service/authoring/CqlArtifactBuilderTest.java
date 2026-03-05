package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.CqlBuildResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class CqlArtifactBuilderTest {

    private final CqlTemplateEngine templateEngine;
    private final ExpressionCqlEngine engine;
    private final CqlArtifactBuilder builder;

    CqlArtifactBuilderTest() {
        templateEngine = new CqlTemplateEngine();
        engine = new ExpressionCqlEngine(templateEngine);
        builder = new CqlArtifactBuilder(engine, templateEngine);
    }

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
                Map.of("id", "element_name", "value", "LDL Cholesterol"),
                Map.of("id", "observation", "type", "observation_vsac",
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
                Map.of("id", "element_name", "value", "BP"),
                Map.of("id", "observation", "type", "observation_vsac",
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
    void buildCql_withSimpleRecommendation_shouldProduceDefine() {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("text", "Consider aspirin therapy");

        CqlBuildResult result = builder.buildCql(
                "RecTest", "1.0.0",
                emptyTree(), emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(rec), "R4"
        );

        assertThat(result.cql()).contains("define \"Recommendation\":");
        assertThat(result.cql()).contains("'Consider aspirin therapy'");
        assertThat(result.cql()).contains("else null");
    }

    @Test
    void buildCql_withCdsCardRecommendation_shouldProduceTuple() {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("text", "Start statin");
        rec.put("detail", "Patient meets criteria");
        rec.put("indicator", "warning");
        rec.put("cdsCardMode", true);

        CqlBuildResult result = builder.buildCql(
                "CardTest", "1.0.0",
                emptyTree(), emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(rec), "R4"
        );

        assertThat(result.cql()).contains("Tuple {");
        assertThat(result.cql()).contains("summary: 'Start statin'");
        assertThat(result.cql()).contains("detail: 'Patient meets criteria'");
        assertThat(result.cql()).contains("indicator: 'warning'");
    }

    @Test
    void buildCql_withErrorStatement_shouldProduceIfThenElse() {
        Map<String, Object> errorStmt = new LinkedHashMap<>();
        List<Map<String, Object>> clauses = new ArrayList<>();
        Map<String, Object> clause1 = new LinkedHashMap<>();
        clause1.put("ifCondition", Map.of("value", "doesnt_meet_inclusion"));
        clause1.put("thenClause", "Patient does not meet inclusion criteria");
        clauses.add(clause1);
        errorStmt.put("ifThenClauses", clauses);
        errorStmt.put("elseClause", "No errors");

        CqlBuildResult result = builder.buildCql(
                "ErrorTest", "1.0.0",
                emptyTree(), emptyTree(),
                List.of(), List.of(), List.of(),
                errorStmt, List.of(), "R4"
        );

        assertThat(result.cql()).contains("define \"Errors\":");
        assertThat(result.cql()).contains("if not \"MeetsInclusionCriteria\"");
        assertThat(result.cql()).contains("then 'Patient does not meet inclusion criteria'");
        assertThat(result.cql()).contains("else 'No errors'");
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
