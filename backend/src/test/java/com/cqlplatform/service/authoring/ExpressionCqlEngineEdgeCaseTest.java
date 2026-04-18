package com.cqlplatform.service.authoring;

import com.cqlplatform.service.authoring.ExpressionCqlEngine.BuildContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-case tests for ExpressionCqlEngine — IEC 62304 critical path coverage.
 * Targets untested branches in expression building, escaping, modifier application,
 * parameter formatting, and declaration collection.
 */
class ExpressionCqlEngineEdgeCaseTest {

    private final CqlTemplateEngine templateEngine = new CqlTemplateEngine();
    private final ExpressionCqlEngine engine = new ExpressionCqlEngine(templateEngine, null);

    private Map<String, Object> emptyTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", "And");
        tree.put("name", "And");
        tree.put("conjunction", true);
        tree.put("returnType", "boolean");
        tree.put("childInstances", new ArrayList<>());
        return tree;
    }

    private Map<String, Object> modifier(String cqlTemplate, String cqlLibFunc, Map<String, Object> values) {
        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("id", cqlTemplate);
        mod.put("cqlTemplate", cqlTemplate);
        if (cqlLibFunc != null) mod.put("cqlLibraryFunction", cqlLibFunc);
        if (values != null) mod.put("values", values);
        return mod;
    }

    // ========================================================================
    // escapeCqlIdentifier
    // ========================================================================

    @Nested
    class EscapeCqlIdentifierTests {
        @Test
        void null_shouldReturnEmpty() {
            assertThat(engine.escapeCqlIdentifier(null)).isEmpty();
        }

        @Test
        void withDoubleQuotes_shouldEscape() {
            assertThat(engine.escapeCqlIdentifier("Blood \"Test\" Panel"))
                    .isEqualTo("Blood \\\"Test\\\" Panel");
        }

        @Test
        void withBackslash_shouldEscape() {
            assertThat(engine.escapeCqlIdentifier("path\\value"))
                    .isEqualTo("path\\\\value");
        }

        @Test
        void withNonAscii_shouldStrip() {
            assertThat(engine.escapeCqlIdentifier("血壓 Blood Pressure"))
                    .isEqualTo("Blood Pressure");
        }

        @Test
        void withEmptyParentheses_shouldStrip() {
            assertThat(engine.escapeCqlIdentifier("FunctionName()"))
                    .isEqualTo("FunctionName");
        }

        @Test
        void withMultipleSpaces_shouldCollapse() {
            assertThat(engine.escapeCqlIdentifier("Blood   Pressure   Panel"))
                    .isEqualTo("Blood Pressure Panel");
        }

        @Test
        void withBackslashAndQuotes_shouldEscapeBothCorrectOrder() {
            // Backslash must be escaped first, then quotes
            assertThat(engine.escapeCqlIdentifier("a\\\"b"))
                    .isEqualTo("a\\\\\\\"b");
        }
    }

    // ========================================================================
    // escapeCqlString — additional edge cases
    // ========================================================================

    @Nested
    class EscapeCqlStringTests {
        @Test
        void withNonAscii_shouldStrip() {
            assertThat(engine.escapeCqlString("糖尿病 Diabetes"))
                    .isEqualTo("Diabetes");
        }

        @Test
        void withMixedEscapeChars_shouldEscapeCorrectly() {
            assertThat(engine.escapeCqlString("it's a \\path"))
                    .isEqualTo("it\\'s a \\\\path");
        }

        @Test
        void emptyString_shouldReturnEmpty() {
            assertThat(engine.escapeCqlString("")).isEmpty();
        }
    }

    // ========================================================================
    // formatDateTimeValue
    // ========================================================================

    @Nested
    class FormatDateTimeValueTests {
        @Test
        void null_shouldReturnNull() {
            assertThat(engine.formatDateTimeValue(null)).isEqualTo("null");
        }

        @Test
        void empty_shouldReturnNull() {
            assertThat(engine.formatDateTimeValue("")).isEqualTo("null");
        }

        @Test
        void dateOnly_shouldPrefixAt() {
            assertThat(engine.formatDateTimeValue("2024-01-15")).isEqualTo("@2024-01-15");
        }

        @Test
        void dateTime_shouldPrefixAt() {
            assertThat(engine.formatDateTimeValue("2024-01-15T10:30:00")).isEqualTo("@2024-01-15T10:30:00");
        }
    }

    // ========================================================================
    // mapUnitToAgeFunction — all unit variants
    // ========================================================================

    @Nested
    class MapUnitToAgeFunctionTests {
        @ParameterizedTest
        @CsvSource({"year,AgeInYears()", "years,AgeInYears()", "month,AgeInMonths()", "months,AgeInMonths()",
                "week,AgeInWeeks()", "weeks,AgeInWeeks()", "day,AgeInDays()", "days,AgeInDays()",
                "hour,AgeInHours()", "hours,AgeInHours()"})
        void knownUnits_shouldMapCorrectly(String unit, String expected) {
            assertThat(engine.mapUnitToAgeFunction(unit)).isEqualTo(expected);
        }

        @Test
        void null_shouldDefaultToYears() {
            assertThat(engine.mapUnitToAgeFunction(null)).isEqualTo("AgeInYears()");
        }

        @Test
        void unknownUnit_shouldDefaultToYears() {
            assertThat(engine.mapUnitToAgeFunction("centuries")).isEqualTo("AgeInYears()");
        }

        @Test
        void mixedCase_shouldMatch() {
            assertThat(engine.mapUnitToAgeFunction("MONTHS")).isEqualTo("AgeInMonths()");
        }
    }

    // ========================================================================
    // buildExpression — element type branches
    // ========================================================================

    @Nested
    class BuildExpressionTypeTests {

        @Test
        void baseElementRef_shouldResolveByUniqueId() {
            List<Map<String, Object>> baseElements = List.of(
                    Map.of("uniqueId", "be-123", "name", "Blood Pressure Check"));
            BuildContext ctx = new BuildContext(baseElements, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "baseElementRef");
            element.put("name", "fallback name");
            element.put("fields", List.of(Map.of("id", "reference_id", "value", "be-123")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("\"Blood Pressure Check\"");
        }

        @Test
        void baseElementRef_unresolvedRef_shouldUseFallbackName() {
            BuildContext ctx = new BuildContext(null, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "baseElementRef");
            element.put("name", "Fallback");
            element.put("fields", List.of(Map.of("id", "reference_id", "value", "nonexistent")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("\"Fallback\"");
        }

        @Test
        void parameterRef_shouldResolveByUniqueId() {
            List<Map<String, Object>> params = List.of(
                    Map.of("uniqueId", "p-1", "name", "Measurement Period"));
            BuildContext ctx = new BuildContext(null, params);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "parameterRef");
            element.put("name", "fallback");
            element.put("fields", List.of(Map.of("id", "reference_id", "value", "p-1")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("\"Measurement Period\"");
        }

        @Test
        void externalCqlRef_withLibraryName_shouldQualify() {
            BuildContext ctx = new BuildContext(null, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "externalCqlRef");
            element.put("name", "MyDef");
            element.put("fields", List.of(
                    Map.of("id", "library_name", "value", "SharedLib"),
                    Map.of("id", "reference_id", "value", "SharedLib:Has Diabetes")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("\"SharedLib\".\"Has Diabetes\"");
        }

        @Test
        void externalCqlRef_withoutLibraryName_shouldNotQualify() {
            BuildContext ctx = new BuildContext(null, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "externalCqlRef");
            element.put("name", "SomeDef");
            element.put("fields", List.of(
                    Map.of("id", "library_name", "value", ""),
                    Map.of("id", "reference_id", "value", "SomeDef")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("\"SomeDef\"");
        }

        @Test
        void arithmeticExpression_literalOperands_shouldProduceFormula() {
            List<Map<String, Object>> baseElements = List.of(
                    Map.of("uniqueId", "be-a", "name", "Systolic BP"));
            BuildContext ctx = new BuildContext(baseElements, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "arithmeticExpression");
            element.put("name", "BP Diff");
            element.put("fields", List.of(
                    Map.of("id", "element_name", "value", "BP Diff"),
                    Map.of("id", "operator", "value", "-"),
                    Map.of("id", "left_mode", "value", "element"),
                    Map.of("id", "left_operand_id", "value", "be-a"),
                    Map.of("id", "right_mode", "value", "literal"),
                    Map.of("id", "right_literal", "value", "120")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("\"Systolic BP\" - 120");
        }

        @Test
        void arithmeticExpression_invalidOperator_shouldDefaultToPlus() {
            List<Map<String, Object>> baseElements = List.of(
                    Map.of("uniqueId", "be-x", "name", "A"),
                    Map.of("uniqueId", "be-y", "name", "B"));
            BuildContext ctx = new BuildContext(baseElements, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "arithmeticExpression");
            element.put("name", "Sum");
            element.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Sum"),
                    Map.of("id", "operator", "value", "DROP TABLE;--"),
                    Map.of("id", "left_mode", "value", "element"),
                    Map.of("id", "left_operand_id", "value", "be-x"),
                    Map.of("id", "right_mode", "value", "element"),
                    Map.of("id", "right_operand_id", "value", "be-y")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("\"A\" + \"B\"");
        }

        @Test
        void arithmeticExpression_invalidLiteral_shouldRejectNonNumeric() {
            BuildContext ctx = new BuildContext(null, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "arithmeticExpression");
            element.put("name", "Injection");
            element.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Injection"),
                    Map.of("id", "operator", "value", "+"),
                    Map.of("id", "left_mode", "value", "literal"),
                    Map.of("id", "left_literal", "value", "1; DROP TABLE"),
                    Map.of("id", "right_mode", "value", "literal"),
                    Map.of("id", "right_literal", "value", "100")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).contains("unresolved arithmetic operands");
        }

        @Test
        void arithmeticExpression_validQuantityLiteral_shouldPass() {
            BuildContext ctx = new BuildContext(null, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "arithmeticExpression");
            element.put("name", "Qty");
            element.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Qty"),
                    Map.of("id", "operator", "value", "+"),
                    Map.of("id", "left_mode", "value", "literal"),
                    Map.of("id", "left_literal", "value", "100 'mg'"),
                    Map.of("id", "right_mode", "value", "literal"),
                    Map.of("id", "right_literal", "value", "50 'mg'")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("100 'mg' + 50 'mg'");
        }

        @Test
        void arithmeticExpression_unresolvedOperands_shouldWarn() {
            BuildContext ctx = new BuildContext(null, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "arithmeticExpression");
            element.put("name", "Bad");
            element.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Bad"),
                    Map.of("id", "operator", "value", "+"),
                    Map.of("id", "left_mode", "value", "element"),
                    Map.of("id", "left_operand_id", "value", ""),
                    Map.of("id", "right_mode", "value", "element"),
                    Map.of("id", "right_operand_id", "value", "")));

            engine.buildExpression(element, ctx);
            assertThat(ctx.warnings).anyMatch(w -> w.contains("unresolved operand"));
        }

        @Test
        void unknownType_shouldDefaultToTrueWithWarning() {
            BuildContext ctx = new BuildContext(null, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "FutureType");
            element.put("name", "MyElement");
            element.put("fields", List.of(Map.of("id", "element_name", "value", "MyElement")));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).isEqualTo("true /* MyElement */");
            assertThat(ctx.warnings).anyMatch(w -> w.contains("Unknown element type 'FutureType'"));
        }

        @Test
        void listReturnType_withoutPreserve_shouldWrapInExists() {
            BuildContext ctx = new BuildContext(null, null);

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "GenericCondition_vsac");
            element.put("name", "Diabetes");
            element.put("returnType", "list_of_conditions");
            element.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Diabetes"),
                    Map.of("id", "condition", "type", "condition_vsac",
                            "valueSets", List.of(Map.of("name", "Diabetes")))));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).startsWith("exists(");
        }

        @Test
        void listReturnType_withPreserve_shouldNotWrapInExists() {
            BuildContext ctx = new BuildContext(null, null);
            ctx.preserveListReturn = true;

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "GenericEncounter_vsac");
            element.put("name", "Encounters");
            element.put("returnType", "list_of_encounters");
            element.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Encounters"),
                    Map.of("id", "encounter", "type", "encounter_vsac",
                            "valueSets", List.of(Map.of("name", "Inpatient")))));

            String result = engine.buildExpression(element, ctx);
            assertThat(result).doesNotStartWith("exists(");
        }
    }

    // ========================================================================
    // buildConjunctionExpression — operators & nesting
    // ========================================================================

    @Nested
    class ConjunctionTests {

        @Test
        void nullGroup_shouldReturnNull() {
            BuildContext ctx = new BuildContext(null, null);
            assertThat(engine.buildConjunctionExpression(null, ctx)).isEqualTo("null");
        }

        @Test
        void orConjunction_shouldUseOrOperator() {
            Map<String, Object> tree = emptyTree();
            tree.put("id", "Or");
            addGenderChild(tree, "Male");
            addGenderChild(tree, "Female");

            BuildContext ctx = new BuildContext(null, null);
            String result = engine.buildConjunctionExpression(tree, ctx);
            assertThat(result).contains(" or ");
        }

        @Test
        void nestedConjunction_shouldWrapInParentheses() {
            Map<String, Object> outer = emptyTree();
            Map<String, Object> inner = emptyTree();
            inner.put("conjunction", true);
            addGenderChild(inner, "Female");
            ((List<Object>) outer.get("childInstances")).add(inner);

            BuildContext ctx = new BuildContext(null, null);
            String result = engine.buildConjunctionExpression(outer, ctx);
            assertThat(result).contains("(");
        }

        @SuppressWarnings("unchecked")
        private void addGenderChild(Map<String, Object> tree, String gender) {
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("type", "Gender");
            child.put("name", "Gender");
            child.put("fields", List.of(Map.of("id", "gender", "value", gender)));
            ((List<Object>) tree.get("childInstances")).add(child);
        }
    }

    // ========================================================================
    // buildGenericResourceExpression — code refs, value-as-Map, value-as-String
    // ========================================================================

    @Nested
    class GenericResourceTests {

        @Test
        void withCodeReferences_shouldUseCodeDisplay() {
            Map<String, Object> code = new LinkedHashMap<>();
            code.put("code", "E11.65");
            code.put("display", "Type 2 DM with hyperglycemia");

            List<Map<String, Object>> fields = List.of(
                    Map.of("id", "element_name", "value", "DM"),
                    new LinkedHashMap<>(Map.of("id", "condition", "type", "condition_vsac",
                            "codes", List.of(code))));

            String result = engine.buildGenericResourceExpression("GenericCondition_vsac", fields);
            assertThat(result).contains("[Condition: \"Type 2 DM with hyperglycemia\"]");
        }

        @Test
        void withValueAsMap_shouldExtractName() {
            Map<String, Object> vsMap = new LinkedHashMap<>();
            vsMap.put("name", "Hypertension");
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("id", "condition");
            field.put("value", vsMap);

            List<Map<String, Object>> fields = List.of(
                    Map.of("id", "element_name", "value", "HTN"),
                    field);

            String result = engine.buildGenericResourceExpression("GenericCondition_vsac", fields);
            assertThat(result).contains("[Condition: \"Hypertension\"]");
        }

        @Test
        void withValueAsString_shouldUseStringValue() {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("id", "observation");
            field.put("value", "Blood Glucose");

            List<Map<String, Object>> fields = List.of(
                    Map.of("id", "element_name", "value", "BG"),
                    field);

            String result = engine.buildGenericResourceExpression("GenericObservation_vsac", fields);
            assertThat(result).contains("[Observation: \"Blood Glucose\"]");
        }

        @Test
        void nullFields_shouldReturnBareResource() {
            String result = engine.buildGenericResourceExpression("GenericProcedure_vsac", null);
            assertThat(result).contains("[Procedure]");
        }

        @Test
        void multipleValueSets_shouldProduceUnion() {
            List<Map<String, Object>> fields = List.of(
                    Map.of("id", "element_name", "value", "Obs"),
                    new LinkedHashMap<>(Map.of("id", "observation", "type", "observation_vsac",
                            "valueSets", List.of(
                                    Map.of("name", "BP Panel"),
                                    Map.of("name", "Heart Rate")))));

            String result = engine.buildGenericResourceExpression("GenericObservation_vsac", fields);
            assertThat(result).contains("[Observation: \"BP Panel\"]");
            assertThat(result).contains("[Observation: \"Heart Rate\"]");
        }

        @Test
        void resourceTypeExtraction_shouldStripPrefixAndSuffix() {
            String result = engine.buildGenericResourceExpression("GenericMedicationRequest_vsac", null);
            assertThat(result).contains("[MedicationRequest]");
        }
    }

    // ========================================================================
    // applyModifier — untested modifier templates
    // ========================================================================

    @Nested
    class ModifierEdgeCaseTests {

        @Test
        void valueComparisonNumber_withRange_shouldProduceCompoundCondition() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = new LinkedHashMap<>();
            vals.put("minOperator", ">=");
            vals.put("minValue", "100");
            vals.put("maxOperator", "<=");
            vals.put("maxValue", "200");

            String result = engine.applyModifier("val", modifier("ValueComparisonNumber", null, vals), ctx);
            assertThat(result).isEqualTo("((val) >= 100 and (val) <= 200)");
        }

        @Test
        void valueComparisonNumber_withUnit_shouldIncludeUnit() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = new LinkedHashMap<>();
            vals.put("minOperator", ">=");
            vals.put("minValue", "90");
            vals.put("unit", "mm[Hg]");

            String result = engine.applyModifier("bp",
                    modifier("ValueComparisonNumber", null, vals), ctx);
            assertThat(result).isEqualTo("(bp) >= 90 'mm[Hg]'");
        }

        @Test
        void valueComparisonNumber_onlyMax_shouldProduceSingleCondition() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = new LinkedHashMap<>();
            vals.put("maxOperator", "<");
            vals.put("maxValue", "140");

            String result = engine.applyModifier("bp", modifier("ValueComparisonNumber", null, vals), ctx);
            assertThat(result).isEqualTo("(bp) < 140");
        }

        @Test
        void valueComparisonNumber_emptyValues_shouldReturnExprUnchanged() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = new LinkedHashMap<>();
            vals.put("minOperator", ">=");
            vals.put("minValue", "");

            String result = engine.applyModifier("bp", modifier("ValueComparisonNumber", null, vals), ctx);
            assertThat(result).isEqualTo("bp");
        }

        @Test
        void valueComparisonNumber_nullValues_shouldReturnExprUnchanged() {
            BuildContext ctx = new BuildContext(null, null);
            String result = engine.applyModifier("expr", modifier("ValueComparisonNumber", null, null), ctx);
            assertThat(result).isEqualTo("expr");
        }

        @Test
        void valueComparisonObservation_shouldWorkSameAsNumber() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = new LinkedHashMap<>();
            vals.put("minOperator", ">");
            vals.put("minValue", "7");

            String result = engine.applyModifier("hba1c",
                    modifier("ValueComparisonObservation", null, vals), ctx);
            assertThat(result).isEqualTo("(hba1c) > 7");
        }

        @Test
        void convertUnits_emptyUnit_shouldReturnExprUnchanged() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = new LinkedHashMap<>();
            vals.put("unit", "");

            String result = engine.applyModifier("val", modifier("ConvertUnits", null, vals), ctx);
            assertThat(result).isEqualTo("val");
        }

        @Test
        void withUnit_nullCqlLibFunc_shouldReturnExprUnchanged() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = new LinkedHashMap<>();
            vals.put("unit", "mg/dL");

            String result = engine.applyModifier("val", modifier("WithUnit", null, vals), ctx);
            assertThat(result).isEqualTo("val");
        }

        @Test
        void containsDateTime_shouldFormatValue() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = Map.of("value", "2024-06-15");

            String result = engine.applyModifier("dates",
                    modifier("ContainsDateTime", null, vals), ctx);
            assertThat(result).isEqualTo("(dates) contains @2024-06-15");
        }

        @Test
        void containsDecimal_shouldFormatValue() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = Map.of("value", "3.14");

            String result = engine.applyModifier("nums",
                    modifier("ContainsDecimal", null, vals), ctx);
            assertThat(result).isEqualTo("(nums) contains 3.14");
        }

        @Test
        void cqlTemplateWithPlaceholder_shouldInterpolate() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> mod = new LinkedHashMap<>();
            mod.put("id", "CustomMod");
            mod.put("cqlTemplate", "CustomFunction({expression})");

            String result = engine.applyModifier("[Condition]", mod, ctx);
            assertThat(result).isEqualTo("CustomFunction([Condition])");
        }

        @Test
        void unknownModifier_shouldWarnAndSkip() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> mod = new LinkedHashMap<>();
            mod.put("id", "TotallyUnknown");
            mod.put("cqlTemplate", "nope");

            String result = engine.applyModifier("expr", mod, ctx);
            assertThat(result).isEqualTo("expr");
            assertThat(ctx.warnings).anyMatch(w -> w.contains("Unknown modifier template 'nope'"));
        }

        @Test
        void booleanComparison_nullValues_shouldReturnExprUnchanged() {
            BuildContext ctx = new BuildContext(null, null);
            String result = engine.applyModifier("expr",
                    modifier("BooleanComparison", null, null), ctx);
            assertThat(result).isEqualTo("expr");
        }

        @Test
        void qualifier_unknownQualifierType_shouldStillRender() {
            BuildContext ctx = new BuildContext(null, null);
            Map<String, Object> vals = new LinkedHashMap<>();
            vals.put("qualifier", "unknown_type");
            vals.put("valueSet", "SomeVS");

            String result = engine.applyModifier("[Obs]",
                    modifier("Qualifier", null, vals), ctx);
            // The Qualifier.ftl template handles the qualifier type; should not crash
            assertThat(result).isNotEmpty();
        }

        @Test
        void beforeInterval_emptyValue_shouldReturnExprUnchanged() {
            BuildContext ctx = new BuildContext(null, null);
            String result = engine.applyModifier("expr",
                    modifier("BeforeInterval", null, Map.of("value", "")), ctx);
            assertThat(result).isEqualTo("expr");
        }

        @Test
        void afterInterval_emptyValue_shouldReturnExprUnchanged() {
            BuildContext ctx = new BuildContext(null, null);
            String result = engine.applyModifier("expr",
                    modifier("AfterInterval", null, Map.of("value", "")), ctx);
            assertThat(result).isEqualTo("expr");
        }

        @Test
        void listCollapsingModifier_shouldBeSkippedWhenPreserveListReturn() {
            BuildContext ctx = new BuildContext(null, null);
            ctx.preserveListReturn = true;

            Map<String, Object> element = new LinkedHashMap<>();
            element.put("type", "GenericObservation_vsac");
            element.put("name", "Obs");
            element.put("returnType", "list_of_observations");
            element.put("fields", List.of(
                    Map.of("id", "element_name", "value", "Obs"),
                    Map.of("id", "observation", "type", "observation_vsac",
                            "valueSets", List.of(Map.of("name", "LDL")))));

            Map<String, Object> mostRecentMod = new LinkedHashMap<>();
            mostRecentMod.put("id", "MostRecent");
            mostRecentMod.put("cqlTemplate", "");
            mostRecentMod.put("cqlLibraryFunction", "C3F.MostRecent");
            mostRecentMod.put("returnType", "observation");
            element.put("modifiers", List.of(mostRecentMod));

            String result = engine.buildExpression(element, ctx);
            // MostRecent should be skipped; result should NOT contain C3F.MostRecent
            assertThat(result).doesNotContain("C3F.MostRecent");
        }
    }

    // ========================================================================
    // formatParameterDefault — all CQL types
    // ========================================================================

    @Nested
    class FormatParameterDefaultTests {

        @Test
        void string_shouldWrapInQuotes() {
            String result = engine.formatParameterDefault("string", "hello world");
            assertThat(result).isEqualTo("'hello world'");
        }

        @Test
        void string_withSingleQuote_shouldEscape() {
            String result = engine.formatParameterDefault("string", "it's");
            assertThat(result).isEqualTo("'it\\'s'");
        }

        @Test
        void decimal_shouldFormatAsIs() {
            String result = engine.formatParameterDefault("decimal", "3.14");
            assertThat(result).isEqualTo("3.14");
        }

        @Test
        void datetime_withoutAt_shouldPrefixAt() {
            String result = engine.formatParameterDefault("datetime", "2024-01-15");
            assertThat(result).isEqualTo("@2024-01-15T00:00:00");
        }

        @Test
        void datetime_withAt_shouldKeepAt() {
            String result = engine.formatParameterDefault("datetime", "@2024-06-15T10:30:00");
            assertThat(result).isEqualTo("@2024-06-15T10:30:00");
        }

        @Test
        void datetime_withT_shouldNotDuplicateT() {
            String result = engine.formatParameterDefault("datetime", "2024-01-15T12:00:00");
            assertThat(result).isEqualTo("@2024-01-15T12:00:00");
        }

        @Test
        void time_shouldPrefixAtT() {
            String result = engine.formatParameterDefault("time", "14:30:00");
            assertThat(result).isEqualTo("@T14:30:00");
        }

        @Test
        void time_withAtPrefix_shouldConvertToAtT() {
            String result = engine.formatParameterDefault("time", "@14:30:00");
            assertThat(result).isEqualTo("@T14:30:00");
        }

        @Test
        void time_withAtT_shouldKeepAsIs() {
            String result = engine.formatParameterDefault("time", "@T14:30:00");
            assertThat(result).isEqualTo("@T14:30:00");
        }

        @Test
        void code_withMap_shouldFormat() {
            Map<String, Object> codeVal = Map.of("code", "E11.65", "system", "http://hl7.org/fhir/sid/icd-10-cm");
            String result = engine.formatParameterDefault("code", codeVal);
            assertThat(result).isNotNull();
            assertThat(result).contains("E11.65");
        }

        @Test
        void code_withNonMap_shouldReturnNull() {
            String result = engine.formatParameterDefault("code", "not-a-map");
            assertThat(result).isNull();
        }

        @Test
        void code_withEmptyCode_shouldReturnNull() {
            Map<String, Object> codeVal = Map.of("code", "", "system", "http://example.com");
            String result = engine.formatParameterDefault("code", codeVal);
            assertThat(result).isNull();
        }

        @Test
        void concept_withMap_shouldFormat() {
            Map<String, Object> conceptVal = Map.of(
                    "code", "38341003", "system", "http://snomed.info/sct", "display", "Hypertension");
            String result = engine.formatParameterDefault("concept", conceptVal);
            assertThat(result).isNotNull();
            assertThat(result).contains("38341003");
        }

        @Test
        void concept_withNonMap_shouldReturnNull() {
            assertThat(engine.formatParameterDefault("concept", "text")).isNull();
        }

        @Test
        void quantity_withMap_shouldFormat() {
            Map<String, Object> qtyVal = Map.of("value", "120", "unit", "mm[Hg]");
            String result = engine.formatParameterDefault("quantity", qtyVal);
            assertThat(result).isNotNull();
            assertThat(result).contains("120");
        }

        @Test
        void quantity_withEmptyValue_shouldReturnNull() {
            Map<String, Object> qtyVal = Map.of("value", "", "unit", "mg");
            String result = engine.formatParameterDefault("quantity", qtyVal);
            assertThat(result).isNull();
        }

        @Test
        void quantity_withNonMap_shouldReturnNull() {
            assertThat(engine.formatParameterDefault("quantity", "120")).isNull();
        }

        @Test
        void intervalInteger_withMap_shouldFormat() {
            Map<String, Object> ivl = Map.of("low", "0", "high", "100");
            String result = engine.formatParameterDefault("interval<integer>", ivl);
            assertThat(result).isNotNull();
            assertThat(result).contains("0");
            assertThat(result).contains("100");
        }

        @Test
        void intervalInteger_withNonMap_shouldUseFallback() {
            String result = engine.formatParameterDefault("interval<integer>", "fallback");
            assertThat(result).isEqualTo("fallback");
        }

        @Test
        void intervalDatetime_withMap_shouldFormat() {
            Map<String, Object> ivl = Map.of("low", "2024-01-01", "high", "2024-12-31");
            String result = engine.formatParameterDefault("interval<datetime>", ivl);
            assertThat(result).isNotNull();
            assertThat(result).contains("@2024-01-01");
            assertThat(result).contains("@2024-12-31");
        }

        @Test
        void intervalDatetime_withEmptyBounds_shouldUseNull() {
            Map<String, Object> ivl = Map.of("low", "", "high", "");
            String result = engine.formatParameterDefault("interval<datetime>", ivl);
            assertThat(result).isNotNull();
            assertThat(result).contains("null");
        }

        @Test
        void unknownType_shouldUseDefault() {
            String result = engine.formatParameterDefault("custom_type", "custom_value");
            assertThat(result).isEqualTo("custom_value");
        }

        @Test
        void nullType_shouldReturnToString() {
            String result = engine.formatParameterDefault(null, 42);
            assertThat(result).isEqualTo("42");
        }
    }

    // ========================================================================
    // collectDeclarations — valueset, code, externalCqlRef collection
    // ========================================================================

    @Nested
    class CollectDeclarationsTests {

        @Test
        void shouldCollectValueSetsFromFieldValueSets() {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "GenericObservation_vsac");
            node.put("fields", List.of(
                    new LinkedHashMap<>(Map.of("id", "observation",
                            "valueSets", List.of(Map.of("name", "Blood Pressure"), Map.of("name", "Heart Rate"))))));

            Set<String> vs = new LinkedHashSet<>();
            engine.collectDeclarations(node, vs, new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
            assertThat(vs).containsExactly("Blood Pressure", "Heart Rate");
        }

        @Test
        void shouldCollectCodesAndCodeSystems() {
            Map<String, Object> codeSystem = Map.of("id", "http://snomed.info/sct", "name", "SNOMED CT");
            Map<String, Object> code = new LinkedHashMap<>();
            code.put("code", "38341003");
            code.put("display", "Hypertension");
            code.put("codeSystem", codeSystem);

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("id", "condition");
            field.put("codes", List.of(code));

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "GenericCondition_vsac");
            node.put("fields", List.of(field));

            Set<String> codes = new LinkedHashSet<>();
            Map<String, String> codeSystems = new LinkedHashMap<>();
            engine.collectDeclarations(node, new LinkedHashSet<>(), codeSystems, codes, new LinkedHashSet<>());

            assertThat(codeSystems).containsKey("http://snomed.info/sct");
            assertThat(codes).anyMatch(c -> c.contains("38341003") && c.contains("Hypertension"));
        }

        @Test
        void shouldCollectExternalCqlIncludes() {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "externalCqlRef");
            node.put("fields", List.of(
                    Map.of("id", "library_name", "value", "SharedLogic"),
                    Map.of("id", "library_version", "value", "1.0.0")));

            Set<String> includes = new LinkedHashSet<>();
            engine.collectDeclarations(node, new LinkedHashSet<>(), new LinkedHashMap<>(), new LinkedHashSet<>(), includes);

            assertThat(includes).anyMatch(inc -> inc.contains("SharedLogic") && inc.contains("1.0.0"));
        }

        @Test
        void shouldCollectExternalCqlIncludes_withoutVersion() {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "externalCqlRef");
            node.put("fields", List.of(
                    Map.of("id", "library_name", "value", "SharedLogic"),
                    Map.of("id", "library_version", "value", "")));

            Set<String> includes = new LinkedHashSet<>();
            engine.collectDeclarations(node, new LinkedHashSet<>(), new LinkedHashMap<>(), new LinkedHashSet<>(), includes);

            assertThat(includes).anyMatch(inc -> inc.contains("include SharedLogic called SharedLogic"));
        }

        @Test
        void shouldCollectValueSetFromFieldValueMap() {
            Map<String, Object> vsMap = new LinkedHashMap<>();
            vsMap.put("name", "Diabetes");

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("id", "condition");
            field.put("value", vsMap);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "GenericCondition_vsac");
            node.put("fields", List.of(field));

            Set<String> vs = new LinkedHashSet<>();
            engine.collectDeclarations(node, vs, new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
            assertThat(vs).contains("Diabetes");
        }

        @Test
        void shouldRecurseIntoChildren() {
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("type", "GenericCondition_vsac");
            child.put("fields", List.of(
                    new LinkedHashMap<>(Map.of("id", "condition",
                            "valueSets", List.of(Map.of("name", "Nested VS"))))));

            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put("id", "And");
            parent.put("childInstances", List.of(child));

            Set<String> vs = new LinkedHashSet<>();
            engine.collectDeclarations(parent, vs, new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
            assertThat(vs).contains("Nested VS");
        }

        @Test
        void nullNode_shouldNotThrow() {
            engine.collectDeclarations(null, new LinkedHashSet<>(), new LinkedHashMap<>(),
                    new LinkedHashSet<>(), new LinkedHashSet<>());
            // no exception = pass
        }
    }

    // ========================================================================
    // emitCodeSystems, emitCodes
    // ========================================================================

    @Nested
    class EmitTests {

        @Test
        void emitCodeSystems_shouldEmitDeclarations() {
            StringBuilder sb = new StringBuilder();
            Map<String, String> cs = new LinkedHashMap<>();
            cs.put("http://snomed.info/sct", "SNOMED CT");
            engine.emitCodeSystems(sb, cs);
            assertThat(sb.toString()).contains("codesystem \"SNOMED CT\": 'http://snomed.info/sct'");
        }

        @Test
        void emitCodeSystems_empty_shouldEmitNothing() {
            StringBuilder sb = new StringBuilder();
            engine.emitCodeSystems(sb, new LinkedHashMap<>());
            assertThat(sb.toString()).isEmpty();
        }

        @Test
        void emitCodes_shouldEmitDeclarations() {
            StringBuilder sb = new StringBuilder();
            Set<String> codes = new LinkedHashSet<>();
            codes.add("code \"Hypertension\": '38341003' from \"SNOMED CT\"");
            engine.emitCodes(sb, codes);
            assertThat(sb.toString()).contains("code \"Hypertension\": '38341003' from \"SNOMED CT\"");
        }

        @Test
        void emitCodes_empty_shouldEmitNothing() {
            StringBuilder sb = new StringBuilder();
            engine.emitCodes(sb, new LinkedHashSet<>());
            assertThat(sb.toString()).isEmpty();
        }
    }

    // ========================================================================
    // BuildContext — parameter name index
    // ========================================================================

    @Nested
    class BuildContextTests {

        @Test
        void parameterNameIndex_shouldResolveByUniqueId() {
            List<Map<String, Object>> params = List.of(
                    Map.of("uniqueId", "p-1", "name", "Measurement Period"));
            BuildContext ctx = new BuildContext(null, params);
            assertThat(ctx.findParameterName("p-1")).isEqualTo("Measurement Period");
        }

        @Test
        void parameterNameIndex_unknownId_shouldReturnNull() {
            BuildContext ctx = new BuildContext(null, null);
            assertThat(ctx.findParameterName("unknown")).isNull();
        }

        @Test
        void findParameterName_null_shouldReturnNull() {
            BuildContext ctx = new BuildContext(null, null);
            assertThat(ctx.findParameterName(null)).isNull();
        }

        @Test
        void findBaseElementName_null_shouldReturnNull() {
            BuildContext ctx = new BuildContext(null, null);
            assertThat(ctx.findBaseElementName(null)).isNull();
        }
    }

    // ========================================================================
    // getFieldValue
    // ========================================================================

    @Nested
    class GetFieldValueTests {

        @Test
        void nullFields_shouldReturnDefault() {
            assertThat(engine.getFieldValue(null, "any", "default")).isEqualTo("default");
        }

        @Test
        void missingField_shouldReturnDefault() {
            List<Map<String, Object>> fields = List.of(Map.of("id", "other", "value", "x"));
            assertThat(engine.getFieldValue(fields, "missing", "default")).isEqualTo("default");
        }

        @Test
        void nullValue_shouldReturnDefault() {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("id", "target");
            field.put("value", null);
            assertThat(engine.getFieldValue(List.of(field), "target", "default")).isEqualTo("default");
        }

        @Test
        void presentValue_shouldReturnValue() {
            List<Map<String, Object>> fields = List.of(Map.of("id", "target", "value", "found"));
            assertThat(engine.getFieldValue(fields, "target", "default")).isEqualTo("found");
        }
    }

    // ========================================================================
    // getFinalReturnType
    // ========================================================================

    @Nested
    class GetFinalReturnTypeTests {

        @Test
        void withModifiers_shouldReturnLastModifierReturnType() {
            Map<String, Object> element = Map.of("returnType", "list_of_observations");
            List<Map<String, Object>> mods = List.of(
                    Map.of("returnType", "list_of_observations"),
                    Map.of("returnType", "boolean"));
            assertThat(engine.getFinalReturnType(element, mods)).isEqualTo("boolean");
        }

        @Test
        void modifiersWithNoReturnType_shouldFallBackToElement() {
            Map<String, Object> element = Map.of("returnType", "list_of_conditions");
            List<Map<String, Object>> mods = List.of(Map.of("id", "SomeMod"));
            assertThat(engine.getFinalReturnType(element, mods)).isEqualTo("list_of_conditions");
        }

        @Test
        void nullModifiers_shouldReturnElementType() {
            Map<String, Object> element = Map.of("returnType", "boolean");
            assertThat(engine.getFinalReturnType(element, null)).isEqualTo("boolean");
        }

        @Test
        void emptyModifiers_shouldReturnElementType() {
            Map<String, Object> element = Map.of("returnType", "integer");
            assertThat(engine.getFinalReturnType(element, List.of())).isEqualTo("integer");
        }
    }
}
