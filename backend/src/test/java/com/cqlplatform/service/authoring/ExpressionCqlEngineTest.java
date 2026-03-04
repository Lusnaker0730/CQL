package com.cqlplatform.service.authoring;

import com.cqlplatform.service.authoring.ExpressionCqlEngine.BuildContext;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionCqlEngineTest {

    private final ExpressionCqlEngine engine = new ExpressionCqlEngine();

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
        BuildContext ctx = new BuildContext(null);
        ctx.warn("test warning");
        assertThat(ctx.warnings).hasSize(1);
        assertThat(ctx.warnings.get(0)).isEqualTo("test warning");
    }

    @Test
    void buildContext_shouldAcceptBaseElements() {
        List<Map<String, Object>> baseElements = List.of(Map.of("name", "MyElement"));
        BuildContext ctx = new BuildContext(baseElements);
        assertThat(ctx.baseElements).hasSize(1);
    }

    // ===== buildConjunctionExpression =====

    @Test
    void buildConjunctionExpression_emptyTree_shouldReturnNull() {
        BuildContext ctx = new BuildContext(null);
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

        BuildContext ctx = new BuildContext(null);
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
        Set<String> cs = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();

        engine.collectDeclarations(emptyTree(), vs, cs, codes, includes);

        assertThat(vs).isEmpty();
        assertThat(cs).isEmpty();
        assertThat(codes).isEmpty();
        assertThat(includes).isEmpty();
    }
}
