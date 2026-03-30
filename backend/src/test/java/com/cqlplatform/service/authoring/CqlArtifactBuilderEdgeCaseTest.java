package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.CqlBuildResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-case tests for CqlArtifactBuilder — IEC 62304 critical path coverage.
 * Targets untested branches in recommendation conditions, error statements,
 * parameter handling, subpopulations, base element ordering, and CDS card tuples.
 */
class CqlArtifactBuilderEdgeCaseTest {

    private final CqlTemplateEngine templateEngine = new CqlTemplateEngine();
    private final ExpressionCqlEngine engine = new ExpressionCqlEngine(templateEngine);
    private final CqlArtifactBuilder builder = new CqlArtifactBuilder(engine, templateEngine);

    private Map<String, Object> emptyTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", "And");
        tree.put("name", "And");
        tree.put("conjunction", true);
        tree.put("returnType", "boolean");
        tree.put("childInstances", new ArrayList<>());
        return tree;
    }

    private CqlBuildResult build(Map<String, Object> include, Map<String, Object> exclude,
            List<Map<String, Object>> subpops, List<Map<String, Object>> baseElems,
            List<Map<String, Object>> params, Map<String, Object> errorStmt,
            List<Map<String, Object>> recs) {
        return builder.buildCql("Test", "1.0.0", include, exclude,
                subpops, baseElems, params, errorStmt, recs, "R4");
    }

    // ========================================================================
    // Name sanitization
    // ========================================================================

    @Nested
    class NameSanitizationTests {

        @Test
        void specialCharacters_shouldBeReplacedWithUnderscore() {
            CqlBuildResult result = builder.buildCql(
                    "My Artifact (v2.1)", "1.0.0",
                    emptyTree(), emptyTree(),
                    List.of(), List.of(), List.of(),
                    null, List.of(), "R4");

            assertThat(result.cql()).contains("library My_Artifact__v2_1_");
        }

        @Test
        void nullVersion_shouldUseDefault() {
            CqlBuildResult result = builder.buildCql(
                    "Test", null,
                    emptyTree(), emptyTree(),
                    List.of(), List.of(), List.of(),
                    null, List.of(), "R4");

            assertThat(result.cql()).contains("version '");
        }
    }

    // ========================================================================
    // Parameters — all types
    // ========================================================================

    @Nested
    class ParameterTests {

        @Test
        void booleanParameter_shouldDeclare() {
            Map<String, Object> param = Map.of("name", "Is Active", "type", "boolean", "value", true);

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(),
                    List.of(param), null, List.of());

            assertThat(result.cql()).contains("parameter \"Is Active\" Boolean default true");
        }

        @Test
        void integerParameter_shouldDeclare() {
            Map<String, Object> param = Map.of("name", "Max Age", "type", "integer", "value", 65);

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(),
                    List.of(param), null, List.of());

            assertThat(result.cql()).contains("parameter \"Max Age\" Integer default 65");
        }

        @Test
        void stringParameter_shouldDeclare() {
            Map<String, Object> param = Map.of("name", "Status Filter", "type", "string", "value", "active");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(),
                    List.of(param), null, List.of());

            assertThat(result.cql()).contains("parameter \"Status Filter\" String default 'active'");
        }

        @Test
        void parameterWithoutValue_shouldDeclareWithoutDefault() {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", "Start Date");
            param.put("type", "datetime");
            param.put("value", null);

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(),
                    List.of(param), null, List.of());

            assertThat(result.cql()).contains("parameter \"Start Date\" DateTime");
        }

        @Test
        void multipleParameters_shouldAllAppear() {
            List<Map<String, Object>> params = List.of(
                    Map.of("name", "A", "type", "boolean", "value", true),
                    Map.of("name", "B", "type", "integer", "value", 10));

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(),
                    params, null, List.of());

            assertThat(result.cql()).contains("parameter \"A\" Boolean");
            assertThat(result.cql()).contains("parameter \"B\" Integer");
        }

        @Test
        void parameterWithUniqueId_shouldBeResolvableByRef() {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("uniqueId", "p-1");
            param.put("name", "Measurement Period");
            param.put("type", "interval<datetime>");
            param.put("value", Map.of("low", "2024-01-01", "high", "2024-12-31"));

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(),
                    List.of(param), null, List.of());

            assertThat(result.cql()).contains("parameter \"Measurement Period\"");
        }
    }

    // ========================================================================
    // Base elements — ordering & arithmetic
    // ========================================================================

    @Nested
    class BaseElementTests {

        @Test
        void nonArithmetic_shouldAppearBeforeArithmetic() {
            Map<String, Object> nonArith = new LinkedHashMap<>();
            nonArith.put("uniqueId", "be-1");
            nonArith.put("type", "Gender");
            nonArith.put("name", "Is Female");
            nonArith.put("fields", List.of(Map.of("id", "gender", "value", "Female")));

            Map<String, Object> arith = new LinkedHashMap<>();
            arith.put("uniqueId", "be-2");
            arith.put("type", "arithmeticExpression");
            arith.put("name", "Score");
            arith.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Score"),
                    Map.of("id", "operator", "value", "+"),
                    Map.of("id", "left_mode", "value", "literal"),
                    Map.of("id", "left_literal", "value", "10"),
                    Map.of("id", "right_mode", "value", "literal"),
                    Map.of("id", "right_literal", "value", "20")));

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(),
                    List.of(nonArith, arith), List.of(), null, List.of());

            // Both should appear
            assertThat(result.cql()).contains("define \"Is Female\":");
            assertThat(result.cql()).contains("define \"Score\":");

            // Non-arithmetic should appear first
            int posFemale = result.cql().indexOf("define \"Is Female\":");
            int posScore = result.cql().indexOf("define \"Score\":");
            assertThat(posFemale).isLessThan(posScore);
        }

        @Test
        void conjunctionBaseElement_shouldBuildAsConjunction() {
            Map<String, Object> be = new LinkedHashMap<>();
            be.put("uniqueId", "be-conj");
            be.put("name", "Combined Criteria");
            be.put("conjunction", true);
            be.put("id", "And");
            be.put("childInstances", new ArrayList<>());

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(),
                    List.of(be), List.of(), null, List.of());

            assertThat(result.cql()).contains("define \"Combined Criteria\":");
        }
    }

    // ========================================================================
    // Subpopulations
    // ========================================================================

    @Nested
    class SubpopulationTests {

        @Test
        void shouldGenerateSubpopulationDefine() {
            Map<String, Object> sp = new LinkedHashMap<>();
            sp.put("subpopulationName", "Age 65+");
            sp.put("id", "And");
            sp.put("conjunction", true);
            sp.put("childInstances", new ArrayList<>());

            CqlBuildResult result = build(emptyTree(), emptyTree(),
                    List.of(sp), List.of(), List.of(), null, List.of());

            assertThat(result.cql()).contains("define \"Age 65+\":");
        }

        @Test
        void specialSubpopulation_shouldBeSkipped() {
            Map<String, Object> sp = new LinkedHashMap<>();
            sp.put("subpopulationName", "Doesn't Meet Inclusion Criteria");
            sp.put("special", true);
            sp.put("id", "And");
            sp.put("conjunction", true);
            sp.put("childInstances", new ArrayList<>());

            CqlBuildResult result = build(emptyTree(), emptyTree(),
                    List.of(sp), List.of(), List.of(), null, List.of());

            assertThat(result.cql()).doesNotContain("define \"Doesn't Meet Inclusion Criteria\":");
        }
    }

    // ========================================================================
    // Recommendations — multiple and condition mapping
    // ========================================================================

    @Nested
    class RecommendationTests {

        @Test
        void multipleRecommendations_shouldBeNumbered() {
            Map<String, Object> rec1 = Map.of("text", "Recommend A");
            Map<String, Object> rec2 = Map.of("text", "Recommend B");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec1, rec2));

            assertThat(result.cql()).contains("define \"Recommendation 1\":");
            assertThat(result.cql()).contains("define \"Recommendation 2\":");
        }

        @Test
        void singleRecommendation_shouldNotBeNumbered() {
            Map<String, Object> rec = Map.of("text", "Recommend A");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("define \"Recommendation\":");
            assertThat(result.cql()).doesNotContain("define \"Recommendation 1\":");
        }

        @Test
        void recommendation_withDoesntMeetInclusionSubpop_shouldNegateInclusion() {
            Map<String, Object> spRef = new LinkedHashMap<>();
            spRef.put("uniqueId", "__doesnt_meet_inclusion__");
            spRef.put("subpopulationName", "Doesn't Meet Inclusion Criteria");

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("text", "Cannot evaluate");
            rec.put("subpopulations", List.of(spRef));

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("not \"MeetsInclusionCriteria\"");
        }

        @Test
        void recommendation_withMeetsExclusionSubpop_shouldReferenceExclusion() {
            Map<String, Object> spRef = new LinkedHashMap<>();
            spRef.put("uniqueId", "__meets_exclusion__");
            spRef.put("subpopulationName", "Meets Exclusion Criteria");

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("text", "Excluded");
            rec.put("subpopulations", List.of(spRef));

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("\"MeetsExclusionCriteria\"");
        }

        @Test
        void recommendation_withCustomSubpop_shouldCombineInPopulationAndSubpop() {
            Map<String, Object> spRef = new LinkedHashMap<>();
            spRef.put("uniqueId", "custom-sp-1");
            spRef.put("subpopulationName", "High Risk Patients");

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("text", "Intensive treatment");
            rec.put("subpopulations", List.of(spRef));

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("\"InPopulation\" and \"High Risk Patients\"");
        }

        @Test
        void recommendation_noSubpopulations_shouldUseInPopulation() {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("text", "Default");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("\"InPopulation\"");
        }
    }

    // ========================================================================
    // CDS Card Tuple — suggestions & actions
    // ========================================================================

    @Nested
    class CdsCardTests {

        @Test
        void withSuggestions_shouldProduceTupleWithSuggestions() {
            Map<String, Object> action = Map.of("type", "create", "description", "Order statin");
            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("label", "Start Statin");
            suggestion.put("isRecommended", true);
            suggestion.put("actions", List.of(action));

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("text", "Statin therapy");
            rec.put("indicator", "warning");
            rec.put("cdsCardMode", true);
            rec.put("suggestions", List.of(suggestion));

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("summary: 'Statin therapy'");
            assertThat(result.cql()).contains("indicator: 'warning'");
            assertThat(result.cql()).contains("label: 'Start Statin'");
        }

        @Test
        void withSourceLabel_shouldIncludeSource() {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("text", "Summary");
            rec.put("indicator", "info");
            rec.put("cdsCardMode", true);
            rec.put("sourceLabel", "AHA Guidelines 2024");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("AHA Guidelines 2024");
        }

        @Test
        void withSelectionBehavior_shouldInclude() {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("text", "Summary");
            rec.put("indicator", "info");
            rec.put("cdsCardMode", true);
            rec.put("selectionBehavior", "at-most-one");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("at-most-one");
        }

        @Test
        void cardText_withSpecialChars_shouldBeEscaped() {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("text", "Patient's HbA1c > 7%");
            rec.put("indicator", "warning");
            rec.put("cdsCardMode", true);

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of(rec));

            assertThat(result.cql()).contains("Patient\\'s HbA1c > 7%");
        }
    }

    // ========================================================================
    // Error statements — multiple clauses and condition mapping
    // ========================================================================

    @Nested
    class ErrorStatementTests {

        @Test
        void multipleClauses_shouldChainIfElseIf() {
            List<Map<String, Object>> clauses = new ArrayList<>();
            clauses.add(Map.of(
                    "ifCondition", Map.of("value", "doesnt_meet_inclusion"),
                    "thenClause", "Does not meet inclusion"));
            clauses.add(Map.of(
                    "ifCondition", Map.of("value", "meets_exclusion"),
                    "thenClause", "Meets exclusion criteria"));

            Map<String, Object> errorStmt = new LinkedHashMap<>();
            errorStmt.put("ifThenClauses", clauses);
            errorStmt.put("elseClause", "No errors found");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    errorStmt, List.of());

            assertThat(result.cql()).contains("define \"Errors\":");
            assertThat(result.cql()).contains("not \"MeetsInclusionCriteria\"");
            assertThat(result.cql()).contains("\"MeetsExclusionCriteria\"");
            assertThat(result.cql()).contains("'Does not meet inclusion'");
            assertThat(result.cql()).contains("'Meets exclusion criteria'");
            assertThat(result.cql()).contains("'No errors found'");
        }

        @Test
        void nullCondition_shouldMapToRecommendationIsNull() {
            List<Map<String, Object>> clauses = new ArrayList<>();
            clauses.add(Map.of(
                    "ifCondition", Map.of("value", "null"),
                    "thenClause", "Recommendation is null"));

            Map<String, Object> errorStmt = new LinkedHashMap<>();
            errorStmt.put("ifThenClauses", clauses);
            errorStmt.put("elseClause", "");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    errorStmt, List.of());

            assertThat(result.cql()).contains("\"Recommendation\" is null");
        }

        @Test
        void errorsCondition_shouldMapToErrorsNotNull() {
            List<Map<String, Object>> clauses = new ArrayList<>();
            clauses.add(Map.of(
                    "ifCondition", Map.of("value", "errors"),
                    "thenClause", "Has errors"));

            Map<String, Object> errorStmt = new LinkedHashMap<>();
            errorStmt.put("ifThenClauses", clauses);
            errorStmt.put("elseClause", "");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    errorStmt, List.of());

            assertThat(result.cql()).contains("\"Errors\" is not null");
        }

        @Test
        void customCondition_shouldPassThrough() {
            List<Map<String, Object>> clauses = new ArrayList<>();
            clauses.add(Map.of(
                    "ifCondition", Map.of("value", "AgeInYears() < 18"),
                    "thenClause", "Patient is underage"));

            Map<String, Object> errorStmt = new LinkedHashMap<>();
            errorStmt.put("ifThenClauses", clauses);
            errorStmt.put("elseClause", "");

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    errorStmt, List.of());

            assertThat(result.cql()).contains("AgeInYears() < 18");
        }

        @Test
        void emptyErrorStatement_shouldNotProduceErrorsDefine() {
            Map<String, Object> errorStmt = new LinkedHashMap<>();
            errorStmt.put("ifThenClauses", List.of());

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    errorStmt, List.of());

            // Empty ifThenClauses → buildErrorStatement returns null → no Errors define
            assertThat(result.cql()).doesNotContain("define \"Errors\":");
        }

        @Test
        void nullErrorStatement_shouldNotProduceErrorsDefine() {
            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of());

            assertThat(result.cql()).doesNotContain("define \"Errors\":");
        }
    }

    // ========================================================================
    // Declaration collection through buildCql
    // ========================================================================

    @Nested
    class DeclarationCollectionTests {

        @Test
        void valueSetsInTree_shouldAppearInHeader() {
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("type", "GenericCondition_vsac");
            child.put("name", "Diabetes");
            child.put("returnType", "list_of_conditions");
            child.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Diabetes"),
                    new LinkedHashMap<>(Map.of("id", "condition", "type", "condition_vsac",
                            "valueSets", List.of(Map.of("name", "Diabetes Mellitus"))))));

            Map<String, Object> existsMod = new LinkedHashMap<>();
            existsMod.put("id", "BooleanExists");
            existsMod.put("cqlTemplate", "BooleanExists");
            existsMod.put("returnType", "boolean");
            child.put("modifiers", List.of(existsMod));

            Map<String, Object> tree = emptyTree();
            ((List<Object>) tree.get("childInstances")).add(child);

            CqlBuildResult result = build(tree, emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of());

            assertThat(result.cql()).contains("valueset \"Diabetes Mellitus\"");
        }

        @Test
        void codeSystemsInTree_shouldAppearInHeader() {
            Map<String, Object> codeSystem = Map.of("id", "http://snomed.info/sct", "name", "SNOMED CT");
            Map<String, Object> code = new LinkedHashMap<>();
            code.put("code", "73211009");
            code.put("display", "Diabetes mellitus");
            code.put("codeSystem", codeSystem);

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("id", "condition");
            field.put("codes", List.of(code));

            Map<String, Object> child = new LinkedHashMap<>();
            child.put("type", "GenericCondition_vsac");
            child.put("name", "DM");
            child.put("returnType", "list_of_conditions");
            child.put("fields", List.of(Map.of("id", "element_name", "value", "DM"), field));
            child.put("modifiers", List.of(Map.of("id", "BooleanExists", "cqlTemplate", "BooleanExists", "returnType", "boolean")));

            Map<String, Object> tree = emptyTree();
            ((List<Object>) tree.get("childInstances")).add(child);

            CqlBuildResult result = build(tree, emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of());

            assertThat(result.cql()).contains("codesystem \"SNOMED CT\"");
            assertThat(result.cql()).contains("code \"Diabetes mellitus\"");
        }

        @Test
        void externalCqlInBaseElements_shouldAddInclude() {
            Map<String, Object> be = new LinkedHashMap<>();
            be.put("uniqueId", "be-ext");
            be.put("type", "externalCqlRef");
            be.put("name", "Has Diabetes");
            be.put("fields", List.of(
                    Map.of("id", "library_name", "value", "SharedLogic"),
                    Map.of("id", "library_version", "value", "1.2.0"),
                    Map.of("id", "reference_id", "value", "SharedLogic:Has Diabetes")));

            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(),
                    List.of(be), List.of(), null, List.of());

            assertThat(result.cql()).contains("include SharedLogic version '1.2.0' called SharedLogic");
        }
    }

    // ========================================================================
    // FHIR version resolution
    // ========================================================================

    @Nested
    class FhirVersionTests {

        @Test
        void defaultR4_shouldIncludeFhir401() {
            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of());

            assertThat(result.cql()).contains("using FHIR version '4.0.1'");
            assertThat(result.cql()).contains("include FHIRHelpers version '4.0.1'");
        }
    }

    // ========================================================================
    // Inclusion / Exclusion expression
    // ========================================================================

    @Nested
    class InclusionExclusionTests {

        @Test
        void nonEmptyExclusion_shouldNotUseFalse() {
            Map<String, Object> excludeTree = emptyTree();
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("type", "Gender");
            child.put("name", "Is Male");
            child.put("fields", List.of(Map.of("id", "gender", "value", "Male")));
            ((List<Object>) excludeTree.get("childInstances")).add(child);

            CqlBuildResult result = build(emptyTree(), excludeTree, List.of(), List.of(), List.of(),
                    null, List.of());

            assertThat(result.cql()).doesNotContainPattern("define \"MeetsExclusionCriteria\":\\R  false");
        }

        @Test
        void emptyInclusion_shouldReturnNull() {
            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of());

            assertThat(result.cql()).containsPattern("define \"MeetsInclusionCriteria\":\\R  null");
        }
    }

    // ========================================================================
    // Warnings collection
    // ========================================================================

    @Nested
    class WarningsTests {

        @Test
        void noWarnings_shouldReturnEmptyList() {
            CqlBuildResult result = build(emptyTree(), emptyTree(), List.of(), List.of(), List.of(),
                    null, List.of());

            assertThat(result.hasWarnings()).isFalse();
            assertThat(result.warnings()).isEmpty();
        }

        @Test
        void warningsFromMultipleSources_shouldBeCollected() {
            // Unknown element types in both include and base elements
            Map<String, Object> unknownChild = new LinkedHashMap<>();
            unknownChild.put("type", "UnknownType");
            unknownChild.put("name", "Bad1");
            unknownChild.put("fields", List.of(Map.of("id", "element_name", "value", "Bad1")));

            Map<String, Object> tree = emptyTree();
            ((List<Object>) tree.get("childInstances")).add(unknownChild);

            Map<String, Object> unknownBe = new LinkedHashMap<>();
            unknownBe.put("uniqueId", "be-bad");
            unknownBe.put("type", "UnknownType2");
            unknownBe.put("name", "Bad2");
            unknownBe.put("fields", List.of(Map.of("id", "element_name", "value", "Bad2")));

            CqlBuildResult result = build(tree, emptyTree(), List.of(),
                    List.of(unknownBe), List.of(), null, List.of());

            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.warnings()).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
