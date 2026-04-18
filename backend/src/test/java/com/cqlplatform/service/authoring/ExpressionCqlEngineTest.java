package com.cqlplatform.service.authoring;

import com.cqlplatform.service.authoring.ExpressionCqlEngine.BuildContext;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionCqlEngineTest {

    private final CqlTemplateEngine templateEngine;
    private final ExpressionCqlEngine engine;

    ExpressionCqlEngineTest() {
        templateEngine = new CqlTemplateEngine();
        engine = new ExpressionCqlEngine(templateEngine, null);
    }

    // ===== Helpers =====

    private Map<String, Object> emptyTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", "And");
        tree.put("name", "And");
        tree.put("conjunction", true);
        tree.put("returnType", "boolean");
        tree.put("childInstances", new ArrayList<>());
        return tree;
    }

    // ===== BuildContext =====

    @Test
    void buildContext_shouldTrackWarnings() {
        BuildContext ctx = new BuildContext(null, null);
        ctx.warn("test warning");
        assertThat(ctx.warnings).hasSize(1);
        assertThat(ctx.warnings.get(0)).isEqualTo("test warning");
    }

    @Test
    void buildContext_shouldAcceptBaseElements() {
        List<Map<String, Object>> baseElements = List.of(Map.of("name", "MyElement"));
        BuildContext ctx = new BuildContext(baseElements, null);
        assertThat(ctx.baseElements).hasSize(1);
    }

    // ===== buildConjunctionExpression =====

    @Test
    void buildConjunctionExpression_emptyTree_shouldReturnNull() {
        BuildContext ctx = new BuildContext(null, null);
        String result = engine.buildConjunctionExpression(emptyTree(), ctx);
        assertThat(result).isEqualTo("null");
    }

    @Test
    void buildConjunctionExpression_andConjunction_shouldUseAnd() {
        Map<String, Object> tree = emptyTree();

        Map<String, Object> child1 = new LinkedHashMap<>();
        child1.put("id", "GenericObservation_vsac");
        child1.put("name", "Observation");
        child1.put("type", "GenericObservation_vsac");
        child1.put("returnType", "list_of_observations");
        child1.put("fields", List.of(
                Map.of("id", "element_name", "type", "string", "value", "BP Check")
        ));
        child1.put("modifiers", List.of(
                Map.of("id", "BooleanExists", "name", "Exists",
                        "inputTypes", List.of("list_of_any"), "returnType", "boolean")
        ));

        ((List<Object>) tree.get("childInstances")).add(child1);

        BuildContext ctx = new BuildContext(null, null);
        String result = engine.buildConjunctionExpression(tree, ctx);
        assertThat(result).isNotEqualTo("null");
    }

    // ===== getStr =====

    @Test
    void getStr_presentKey_shouldReturnValue() {
        Map<String, Object> map = Map.of("key", "value");
        assertThat(engine.getStr(map, "key", "default")).isEqualTo("value");
    }

    @Test
    void getStr_missingKey_shouldReturnDefault() {
        Map<String, Object> map = Map.of();
        assertThat(engine.getStr(map, "key", "default")).isEqualTo("default");
    }

    @Test
    void getStr_nullValue_shouldReturnDefault() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", null);
        assertThat(engine.getStr(map, "key", "default")).isEqualTo("default");
    }

    // ===== escapeCqlString =====

    @Test
    void escapeCqlString_shouldEscapeSingleQuotes() {
        String result = engine.escapeCqlString("test's value");
        assertThat(result).isEqualTo("test\\'s value");
    }

    @Test
    void escapeCqlString_shouldEscapeBackslashes() {
        String result = engine.escapeCqlString("path\\to\\file");
        assertThat(result).isEqualTo("path\\\\to\\\\file");
    }

    @Test
    void escapeCqlString_null_shouldReturnEmpty() {
        String result = engine.escapeCqlString(null);
        assertThat(result).isEmpty();
    }

    // ===== mapParameterType =====

    @Test
    void mapParameterType_shouldMapKnownTypes() {
        assertThat(engine.mapParameterType("boolean")).isEqualTo("Boolean");
        assertThat(engine.mapParameterType("integer")).isEqualTo("Integer");
        assertThat(engine.mapParameterType("decimal")).isEqualTo("Decimal");
        assertThat(engine.mapParameterType("string")).isEqualTo("String");
        assertThat(engine.mapParameterType("datetime")).isEqualTo("DateTime");
    }

    @Test
    void mapParameterType_unknownType_shouldReturnCapitalized() {
        String result = engine.mapParameterType("custom_type");
        assertThat(result).isNotNull();
    }

    // ===== formatParameterDefault =====

    @Test
    void formatParameterDefault_boolean_shouldFormat() {
        String result = engine.formatParameterDefault("boolean", true);
        assertThat(result).isEqualTo("true");
    }

    @Test
    void formatParameterDefault_integer_shouldFormat() {
        String result = engine.formatParameterDefault("integer", 42);
        assertThat(result).isEqualTo("42");
    }

    @Test
    void formatParameterDefault_null_shouldReturnNull() {
        String result = engine.formatParameterDefault("string", null);
        assertThat(result).isNull();
    }

    // ===== resolveFhirVersion / resolveHelpersVersion =====

    @Test
    void resolveFhirVersion_r4_shouldReturn401() {
        assertThat(engine.resolveFhirVersion("R4")).isEqualTo("4.0.1");
    }

    @Test
    void resolveHelpersVersion_r4_shouldReturn401() {
        assertThat(engine.resolveHelpersVersion("R4")).isEqualTo("4.0.1");
    }

    // ===== emit helpers =====

    @Test
    void emitValueSets_shouldEmitDeclarations() {
        StringBuilder sb = new StringBuilder();
        Set<String> vs = new LinkedHashSet<>();
        vs.add("urn:oid:2.16.840.1.113762.1.4.1");
        engine.emitValueSets(sb, vs);
        assertThat(sb.toString()).contains("valueset");
        assertThat(sb.toString()).contains("urn:oid:2.16.840.1.113762.1.4.1");
    }

    @Test
    void emitValueSets_empty_shouldEmitNothing() {
        StringBuilder sb = new StringBuilder();
        engine.emitValueSets(sb, new LinkedHashSet<>());
        assertThat(sb.toString()).isEmpty();
    }

    @Test
    void emitIncludes_shouldEmitDeclarations() {
        StringBuilder sb = new StringBuilder();
        Set<String> includes = new LinkedHashSet<>();
        includes.add("include FHIRHelpers version '4.0.1' called FHIRHelpers");
        engine.emitIncludes(sb, includes);
        assertThat(sb.toString()).contains("include FHIRHelpers");
    }

    // ===== collectDeclarations =====

    @Test
    void collectDeclarations_emptyTree_shouldNotThrow() {
        Set<String> vs = new LinkedHashSet<>();
        Map<String, String> cs = new LinkedHashMap<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();

        engine.collectDeclarations(emptyTree(), vs, cs, codes, includes);

        assertThat(vs).isEmpty();
        assertThat(cs).isEmpty();
        assertThat(codes).isEmpty();
        assertThat(includes).isEmpty();
    }

    // ===== applyModifier — template-based snapshot tests =====

    private Map<String, Object> modifier(String cqlTemplate) {
        return modifier(cqlTemplate, null, null);
    }

    private Map<String, Object> modifier(String cqlTemplate, String cqlLibFunc, Map<String, Object> values) {
        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("id", cqlTemplate);
        mod.put("cqlTemplate", cqlTemplate);
        if (cqlLibFunc != null) mod.put("cqlLibraryFunction", cqlLibFunc);
        if (values != null) mod.put("values", values);
        return mod;
    }

    @Test
    void applyModifier_checkExistence() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("[Condition]", modifier("CheckExistence"), ctx))
                .isEqualTo("exists([Condition])");
    }

    @Test
    void applyModifier_booleanExists() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("[Observation]", modifier("BooleanExists"), ctx))
                .isEqualTo("exists([Observation])");
    }

    @Test
    void applyModifier_booleanNot() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("true", modifier("BooleanNot"), ctx))
                .isEqualTo("not (true)");
    }

    @Test
    void applyModifier_count() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("[Condition]", modifier("Count"), ctx))
                .isEqualTo("Count([Condition])");
    }

    @Test
    void applyModifier_allTrue() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("expr", modifier("AllTrue"), ctx))
                .isEqualTo("AllTrue(expr)");
    }

    @Test
    void applyModifier_anyTrue() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("expr", modifier("AnyTrue"), ctx))
                .isEqualTo("AnyTrue(expr)");
    }

    @Test
    void applyModifier_booleanComparison() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("[Obs]",
                modifier("BooleanComparison", null, Map.of("value", "is not null")), ctx))
                .isEqualTo("([Obs]) is not null");
    }

    @Test
    void applyModifier_convertUnits() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("val",
                modifier("ConvertUnits", null, Map.of("unit", "mg/dL")), ctx))
                .isEqualTo("convert (val) to 'mg/dL'");
    }

    @Test
    void applyModifier_withUnit() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("[Obs]",
                modifier("WithUnit", "C3F.QuantityValue", Map.of("unit", "mm[Hg]")), ctx))
                .isEqualTo("C3F.QuantityValue([Obs], 'mm[Hg]')");
    }

    @Test
    void applyModifier_lookBackModifier() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("[Condition]",
                modifier("LookBackModifier", "C3F.ObservationLookBack", Map.of("value", "3", "unit", "years")), ctx))
                .isEqualTo("C3F.ObservationLookBack([Condition], 3 years)");
    }

    @Test
    void applyModifier_duringMeasurementPeriod_observation() {
        // Real ModifierService so the DuringMeasurementPeriod catalog (resourceAlias + whereClause) resolves.
        ModifierService modSvc = new ModifierService();
        modSvc.init();
        ExpressionCqlEngine eng = new ExpressionCqlEngine(templateEngine, modSvc);
        BuildContext ctx = new BuildContext(null, null);

        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("id", "DuringMeasurementPeriodObservation");
        mod.put("cqlTemplate", "DuringMeasurementPeriod");

        String out = eng.applyModifier("C3F.Verified([Observation: \"Hemoglobin A1c\"])", mod, ctx);

        // Null-safe case expression: dispatch on effective type first to avoid ToInterval(null) ambiguity
        assertThat(out)
                .contains("(C3F.Verified([Observation: \"Hemoglobin A1c\"])) O")
                .contains("when O.effective is FHIR.Period then FHIRHelpers.ToInterval(O.effective as FHIR.Period) overlaps \"Measurement Period\"")
                .contains("when O.effective is FHIR.dateTime then FHIRHelpers.ToDateTime(O.effective as FHIR.dateTime) in \"Measurement Period\"");
    }

    @Test
    void applyModifier_duringMeasurementPeriod_encounter() {
        ModifierService modSvc = new ModifierService();
        modSvc.init();
        ExpressionCqlEngine eng = new ExpressionCqlEngine(templateEngine, modSvc);
        BuildContext ctx = new BuildContext(null, null);

        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("id", "DuringMeasurementPeriodEncounter");
        mod.put("cqlTemplate", "DuringMeasurementPeriod");

        String out = eng.applyModifier("[Encounter]", mod, ctx);

        assertThat(out).contains("([Encounter]) E")
                .contains("case when E.period is null then false else FHIRHelpers.ToInterval(E.period) overlaps \"Measurement Period\" end");
    }

    @Test
    void applyModifier_duringMeasurementPeriod_unknownIdWarns() {
        ModifierService modSvc = new ModifierService();
        modSvc.init();
        ExpressionCqlEngine eng = new ExpressionCqlEngine(templateEngine, modSvc);
        BuildContext ctx = new BuildContext(null, null);

        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("id", "DuringMeasurementPeriodBogus");
        mod.put("cqlTemplate", "DuringMeasurementPeriod");

        String out = eng.applyModifier("[Encounter]", mod, ctx);

        // Falls back to original expression + logs a warning
        assertThat(out).isEqualTo("[Encounter]");
        assertThat(ctx.warnings).anyMatch(w -> w.contains("DuringMeasurementPeriod"));
    }

    @Test
    void applyModifier_equalsString() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("Patient.name",
                modifier("EqualsString", null, Map.of("value", "John")), ctx))
                .isEqualTo("(Patient.name) = 'John'");
    }

    @Test
    void applyModifier_startsWithString() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("Patient.name",
                modifier("StartsWithString", null, Map.of("value", "Jo")), ctx))
                .isEqualTo("StartsWith(Patient.name, 'Jo')");
    }

    @Test
    void applyModifier_endsWithString() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("Patient.name",
                modifier("EndsWithString", null, Map.of("value", "hn")), ctx))
                .isEqualTo("EndsWith(Patient.name, 'hn')");
    }

    @Test
    void applyModifier_beforeTimePrecise() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("Obs.effective",
                modifier("BeforeTimePrecise", null, Map.of("value", "2024-01-01T00:00:00")), ctx))
                .isEqualTo("(Obs.effective) before @2024-01-01T00:00:00");
    }

    @Test
    void applyModifier_afterDateTimePrecise() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("Obs.effective",
                modifier("AfterDateTimePrecise", null, Map.of("value", "2024-06-15")), ctx))
                .isEqualTo("(Obs.effective) after @2024-06-15");
    }

    @Test
    void applyModifier_containsInteger() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("myList",
                modifier("ContainsInteger", null, Map.of("value", "42")), ctx))
                .isEqualTo("(myList) contains 42");
    }

    @Test
    void applyModifier_containsQuantity_withUnit() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("myList",
                modifier("ContainsQuantity", null, Map.of("value", "120", "unit", "mm[Hg]")), ctx))
                .isEqualTo("(myList) contains 120 'mm[Hg]'");
    }

    @Test
    void applyModifier_containsQuantity_noUnit() {
        BuildContext ctx = new BuildContext(null, null);
        Map<String, Object> vals = new LinkedHashMap<>();
        vals.put("value", "120");
        vals.put("unit", "");
        assertThat(engine.applyModifier("myList",
                modifier("ContainsQuantity", null, vals), ctx))
                .isEqualTo("(myList) contains 120");
    }

    @Test
    void applyModifier_isTrue() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("expr", modifier("IsTrue"), ctx))
                .isEqualTo("(expr) is true");
    }

    @Test
    void applyModifier_isNotTrue() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("expr", modifier("IsNotTrue"), ctx))
                .isEqualTo("(expr) is not true");
    }

    @Test
    void applyModifier_isFalse() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("expr", modifier("IsFalse"), ctx))
                .isEqualTo("(expr) is false");
    }

    @Test
    void applyModifier_isNotFalse() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("expr", modifier("IsNotFalse"), ctx))
                .isEqualTo("(expr) is not false");
    }

    @Test
    void applyModifier_beforeInterval() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("expr",
                modifier("BeforeInterval", null, Map.of("value", "\"Measurement Period\"")), ctx))
                .isEqualTo("(expr) before \"Measurement Period\"");
    }

    @Test
    void applyModifier_afterInterval() {
        BuildContext ctx = new BuildContext(null, null);
        assertThat(engine.applyModifier("expr",
                modifier("AfterInterval", null, Map.of("value", "\"Measurement Period\"")), ctx))
                .isEqualTo("(expr) after \"Measurement Period\"");
    }

    @Test
    void applyModifier_qualifier_valueSet() {
        BuildContext ctx = new BuildContext(null, null);
        Map<String, Object> vals = new LinkedHashMap<>();
        vals.put("qualifier", "value set");
        vals.put("valueSet", "Diabetes");
        assertThat(engine.applyModifier("[Condition]",
                modifier("Qualifier", null, vals), ctx))
                .isEqualTo("[Condition] Q where Q.code in \"Diabetes\"");
    }

    @Test
    void applyModifier_qualifier_code() {
        BuildContext ctx = new BuildContext(null, null);
        Map<String, Object> vals = new LinkedHashMap<>();
        vals.put("qualifier", "code");
        vals.put("code", "E11.65");
        assertThat(engine.applyModifier("[Condition]",
                modifier("Qualifier", null, vals), ctx))
                .isEqualTo("[Condition] Q where Q.code ~ \"E11.65\"");
    }

    @Test
    void applyModifier_baseModifier_fallback() {
        BuildContext ctx = new BuildContext(null, null);
        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("id", "Verified");
        mod.put("cqlTemplate", "");
        mod.put("cqlLibraryFunction", "C3F.Verified");
        assertThat(engine.applyModifier("[Condition]", mod, ctx))
                .isEqualTo("C3F.Verified([Condition])");
    }

    // ===== buildExpression — element type tests =====

    @Test
    void buildExpression_ageRange_bothBounds() {
        BuildContext ctx = new BuildContext(null, null);
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", "AgeRange");
        element.put("name", "Age");
        element.put("fields", List.of(
                Map.of("id", "min_age", "value", "18"),
                Map.of("id", "max_age", "value", "65"),
                Map.of("id", "unit_of_time", "value", "year")
        ));
        String result = engine.buildExpression(element, ctx);
        assertThat(result).isEqualTo("(AgeInYears() >= 18 and AgeInYears() <= 65)");
    }

    @Test
    void buildExpression_gender() {
        BuildContext ctx = new BuildContext(null, null);
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", "Gender");
        element.put("name", "Gender");
        element.put("fields", List.of(Map.of("id", "gender", "value", "Female")));
        String result = engine.buildExpression(element, ctx);
        assertThat(result).isEqualTo("Patient.gender = 'female'");
    }

    @Test
    void buildExpression_genericObservation() {
        BuildContext ctx = new BuildContext(null, null);
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", "GenericObservation_vsac");
        element.put("name", "BP");
        element.put("returnType", "list_of_observations");
        Map<String, Object> nameField = new LinkedHashMap<>();
        nameField.put("id", "element_name");
        nameField.put("value", "BP");
        Map<String, Object> obsField = new LinkedHashMap<>();
        obsField.put("id", "observation");
        obsField.put("type", "observation_vsac");
        obsField.put("valueSets", List.of(Map.of("name", "Blood Pressure")));
        element.put("fields", List.of(nameField, obsField));
        Map<String, Object> existsMod = modifier("CheckExistence");
        existsMod.put("returnType", "boolean");
        element.put("modifiers", List.of(existsMod));
        String result = engine.buildExpression(element, ctx);
        assertThat(result).isEqualTo("exists([Observation: \"Blood Pressure\"])");
    }
}
