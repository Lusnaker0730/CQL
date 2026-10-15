package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.StratifierDefinition;
import com.cqlplatform.testsupport.Fuzz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-265 — {@link ValueKeys#of} (the stratum / supplemental-data key of a serialised CQL value,
 * PAT-233 / PAT-234) and the multi-component stratum encoding (PAT-235) under random values: any
 * nesting of strings (including the reserved separator characters), numbers, booleans, nulls, lists
 * and code / concept maps. A key is a label: never empty, never {@code "null"}, never longer than
 * {@link ValueKeys#MAX_KEY}, never containing a separator, and the component encoding decodes to
 * exactly the codes and values it was built from.
 */
@DisplayName("PAT-265 — value key and stratum encoding properties")
class ValueKeysPropertyTest {

    private static final char RS = StratifierEvaluator.CODE_SEPARATOR;
    private static final char US = StratifierEvaluator.COMPONENT_SEPARATOR;

    private final StratifierEvaluator stratifierEvaluator = new StratifierEvaluator(new PopulationEvaluator(), new MeasureScoreCalculator());

    static String randomText(Random r) {
        int n = Fuzz.between(r, 0, 30);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(switch (Fuzz.between(r, 0, 9)) {
                case 0 -> ' ';
                case 1 -> RS;
                case 2 -> US;
                case 3 -> '|';
                case 4 -> '中';
                default -> (char) Fuzz.between(r, 'a', 'z');
            });
        }
        if (Fuzz.chance(r, 0.05)) return "null";
        if (Fuzz.chance(r, 0.03)) return "x".repeat(Fuzz.between(r, ValueKeys.MAX_KEY - 2, ValueKeys.MAX_KEY + 40));
        return sb.toString();
    }

    /** Any value shape the engine can hand to the key function. */
    static Object randomValue(Random r, int depth) {
        int kind = Fuzz.between(r, 0, depth > 2 ? 5 : 8);
        switch (kind) {
            case 0: return null;
            case 1: return randomText(r);
            case 2: return Fuzz.pick(r, (Object) 65, 65.0, -1, 0, 1.5e10, Long.MAX_VALUE);
            case 3: return r.nextBoolean();
            case 4: {
                Map<String, Object> code = new LinkedHashMap<>();
                code.put("code", Fuzz.pick(r, "F", "M", randomText(r), ""));
                if (Fuzz.chance(r, 0.6)) code.put("display", Fuzz.pick(r, "Female", randomText(r), "", "  "));
                if (Fuzz.chance(r, 0.5)) code.put("system", "http://example.org");
                return code;
            }
            case 5: {
                Map<String, Object> other = new LinkedHashMap<>();
                other.put("value", randomText(r));
                return other;
            }
            case 6: {
                Map<String, Object> concept = new LinkedHashMap<>();
                List<Object> codes = new ArrayList<>();
                int n = Fuzz.between(r, 0, 3);
                for (int i = 0; i < n; i++) codes.add(randomValue(r, depth + 1));
                concept.put("codes", codes);
                if (Fuzz.chance(r, 0.5)) concept.put("display", randomText(r));
                return concept;
            }
            default: {
                List<Object> list = new ArrayList<>();
                int n = Fuzz.between(r, 0, 4);
                for (int i = 0; i < n; i++) list.add(randomValue(r, depth + 1));
                return list;
            }
        }
    }

    private static String clean(String text) {
        return text.replace(US, ' ').replace(RS, ' ').trim();
    }

    @Test
    @DisplayName("a key is a clean, bounded, deterministic label — or null")
    void of_isACleanLabelOrNull() {
        Fuzz.forAll(20261040L, 1500, r -> randomValue(r, 0), (value, r) -> {
            String key = ValueKeys.of(value);
            assertThat(ValueKeys.of(value)).as("deterministic").isEqualTo(key);
            if (key == null) return;
            assertThat(key).isNotEmpty().isNotEqualTo("null");
            assertThat(key).as("trimmed").isEqualTo(key.trim());
            assertThat(key.indexOf(RS)).as("no record separator").isNegative();
            assertThat(key.indexOf(US)).as("no unit separator").isNegative();
            assertThat(key.length()).isLessThanOrEqualTo(ValueKeys.MAX_KEY);
        });
    }

    @Test
    @DisplayName("a string keys as its own text with the separators blanked, blank and \"null\" as no category")
    void of_onStrings() {
        Fuzz.forAll(20261041L, 800, ValueKeysPropertyTest::randomText, (text, r) -> {
            String expected = clean(text);
            String key = ValueKeys.of(text);
            if (expected.isEmpty() || expected.equals("null")) {
                assertThat(key).isNull();
            } else if (expected.length() > ValueKeys.MAX_KEY) {
                assertThat(key).hasSize(ValueKeys.MAX_KEY).startsWith(expected.substring(0, ValueKeys.MAX_KEY - 1)).endsWith("…");
            } else {
                assertThat(key).isEqualTo(expected);
            }
        });
    }

    @Test
    @DisplayName("a one-element list keys as its element, and a list of only empty parts is no category")
    void of_onLists() {
        Fuzz.forAll(20261042L, 800, r -> randomValue(r, 1), (value, r) -> {
            assertThat(ValueKeys.of(Arrays.asList(value))).isEqualTo(ValueKeys.of(value));
            List<Object> twice = Arrays.asList(value, value);
            String single = ValueKeys.of(value);
            String pair = ValueKeys.of(twice);
            if (single == null) {
                assertThat(pair).isNull();
            } else {
                assertThat(pair).isNotNull().startsWith(single.length() > ValueKeys.MAX_KEY / 2 ? single.substring(0, 10) : single);
            }
        });
    }

    @Test
    @DisplayName("a code map keys on code (display), a concept map on its display or its codes")
    void of_onCodeMaps() {
        Fuzz.forAll(20261043L, 500, r -> randomValue(r, 0), (value, r) -> {
            if (!(value instanceof Map<?, ?> map)) return;
            String key = ValueKeys.of(value);
            if (map.get("code") != null) {
                Object display = map.get("display");
                String raw = display != null && !String.valueOf(display).isBlank()
                        ? map.get("code") + " (" + display + ")" : String.valueOf(map.get("code"));
                assertThat(key).isEqualTo(ValueKeys.of(raw));
            } else if (map.get("codes") instanceof Iterable<?> codes) {
                Object display = map.get("display");
                if (display != null && !String.valueOf(display).isBlank()) {
                    assertThat(key).isEqualTo(ValueKeys.of(String.valueOf(display)));
                } else {
                    assertThat(key).isEqualTo(ValueKeys.of(String.valueOf(ValueKeys.of(codes))));
                }
            } else {
                assertThat(key).isEqualTo(ValueKeys.of(String.valueOf(map)));
            }
        });
    }

    record Component(String code, Object value) {
    }

    @Test
    @DisplayName("multi-component stratum keys decode to the codes and values they were built from, in order")
    void componentKeys_roundTrip() {
        Fuzz.forAll(20261044L, 600, r -> {
            int n = Fuzz.between(r, 1, 4);
            List<Component> components = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                components.add(new Component(Fuzz.chance(r, 0.2) ? null : Fuzz.pick(r, "gender", "age", randomText(r), ""), randomValue(r, 0)));
            }
            return components;
        }, (components, r) -> {
            List<StratifierDefinition.Component> definitions = new ArrayList<>();
            Map<String, ExpressionResult> results = new LinkedHashMap<>();
            List<String> expectedCodes = new ArrayList<>();
            List<String> expectedValues = new ArrayList<>();
            boolean anyMissing = false;
            for (int i = 0; i < components.size(); i++) {
                Component c = components.get(i);
                String expression = "Stratifier " + i;
                definitions.add(StratifierDefinition.Component.builder().code(c.code()).criteriaExpression(expression).build());
                results.put(expression, ExpressionResult.builder().name(expression).value(c.value()).build());
                String value = ValueKeys.of(c.value());
                if (value == null) anyMissing = true;
                expectedCodes.add((c.code() != null ? c.code() : expression).replace(US, ' ').replace(RS, ' '));
                expectedValues.add(value);
            }
            StratifierDefinition stratifier = StratifierDefinition.builder().stratifierId("s").components(definitions).build();
            String key = stratifierEvaluator.resolveStratumKey(stratifier, results);
            if (anyMissing) {
                assertThat(key).as("a patient missing any component is in no stratum").isNull();
                return;
            }
            assertThat(key).isNotNull();
            List<MeasureEvaluationResult.StratumComponent> decoded = StratifierEvaluator.decodeComponents(key);
            assertThat(decoded).hasSize(components.size());
            for (int i = 0; i < decoded.size(); i++) {
                assertThat(decoded.get(i).getCode()).as("code " + i).isEqualTo(expectedCodes.get(i));
                assertThat(decoded.get(i).getValue()).as("value " + i).isEqualTo(expectedValues.get(i));
            }
            assertThat(StratifierEvaluator.displayValue(key)).isEqualTo(String.join(StratifierEvaluator.DISPLAY_SEPARATOR, expectedValues));
            assertThat(stratifierEvaluator.resolveStratumValue(stratifier, results)).isEqualTo(StratifierEvaluator.displayValue(key));
        });
    }
}
