package com.cqlplatform.service.authoring;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CustomModifierCqlBuilderTest {

    private final CustomModifierCqlBuilder builder = new CustomModifierCqlBuilder();

    // ── Build: happy paths per operator ──────────────────────────────────

    @Test
    void build_equalsString_emitsStringLiteralEquals() {
        String cql = builder.build("[Observation]",
                wrapRules("AND", rule("status", "equals", "final", "string")));
        assertThat(cql).isEqualTo("([Observation]).where(status = 'final')");
    }

    @Test
    void build_equalsCode_emitsCodeEquivalence() {
        String cql = builder.build("[Condition]",
                wrapRules("AND", rule("clinicalStatus.coding.code", "equals", "active", "code")));
        assertThat(cql).isEqualTo("([Condition]).where(clinicalStatus.coding.code ~ 'active')");
    }

    @Test
    void build_equalsDecimal_emitsNumericEquals() {
        String cql = builder.build("[Observation]",
                wrapRules("AND", rule("valueQuantity.value", "equals", "7.2", "decimal")));
        assertThat(cql).isEqualTo("([Observation]).where(valueQuantity.value = 7.2)");
    }

    @Test
    void build_notEqualsCode_emitsNotEquivalence() {
        String cql = builder.build("[Condition]",
                wrapRules("AND", rule("clinicalStatus.coding.code", "not_equals", "resolved", "code")));
        assertThat(cql).isEqualTo("([Condition]).where(clinicalStatus.coding.code !~ 'resolved')");
    }

    @Test
    void build_numericComparisons_emitOperators() {
        String cql = builder.build("[Observation]",
                wrapRules("AND",
                        rule("valueQuantity.value", "gt", "5", "decimal"),
                        rule("valueQuantity.value", "lte", "100", "decimal")));
        assertThat(cql).isEqualTo("([Observation]).where(valueQuantity.value > 5 and valueQuantity.value <= 100)");
    }

    @Test
    void build_stringOperators_useFhirFunctions() {
        String cql = builder.build("[Patient]",
                wrapRules("OR",
                        rule("name.family", "starts_with", "Wang", "string"),
                        rule("name.family", "ends_with", "Lin", "string"),
                        rule("name.family", "contains", "Hsu", "string")));
        assertThat(cql).isEqualTo("([Patient]).where(StartsWith(name.family, 'Wang')"
                + " or EndsWith(name.family, 'Lin')"
                + " or PositionOf('Hsu', name.family) >= 0)");
    }

    @Test
    void build_in_emitsValueSetReference() {
        String cql = builder.build("[Condition]",
                wrapRules("AND", rule("code", "in", "Diabetes Codes", "code")));
        assertThat(cql).isEqualTo("([Condition]).where(code in \"Diabetes Codes\")");
    }

    @Test
    void build_isNullOperators_emitNullChecks() {
        String cql = builder.build("[Observation]",
                wrapRules("AND",
                        rule("effectiveDateTime", "is_null", "", "dateTime"),
                        rule("status", "is_not_null", "", "string")));
        assertThat(cql).isEqualTo("([Observation]).where(effectiveDateTime is null and status is not null)");
    }

    @Test
    void build_beforeAfter_emitDateLiterals() {
        String cql = builder.build("[Observation]",
                wrapRules("AND",
                        rule("effectiveDateTime", "after", "2024-01-01", "dateTime"),
                        rule("effectiveDateTime", "before", "2025-06-30T23:59:59", "dateTime")));
        assertThat(cql).isEqualTo("([Observation]).where(effectiveDateTime after @2024-01-01"
                + " and effectiveDateTime before @2025-06-30T23:59:59)");
    }

    @Test
    void build_withinLast_emitsNowMinusDuration() {
        String cql = builder.build("[Observation]",
                wrapRules("AND", rule("effectiveDateTime", "within_last", "30 days", "dateTime")));
        assertThat(cql).isEqualTo("([Observation]).where(effectiveDateTime >= Now() - 30 days)");
    }

    @Test
    void build_nestedGroup_wrapsInParens() {
        Map<String, Object> outer = Map.of(
                "conjunction", "AND",
                "rules", List.of(rule("status", "equals", "final", "string")),
                "groups", List.of(Map.of(
                        "conjunction", "OR",
                        "rules", List.of(
                                rule("code.coding.code", "equals", "A1c", "code"),
                                rule("code.coding.code", "equals", "HbA1c", "code")),
                        "groups", List.of()))
        );
        String cql = builder.build("[Observation]", Map.of("rules", outer));
        assertThat(cql).isEqualTo("([Observation]).where(status = 'final'"
                + " and (code.coding.code ~ 'A1c' or code.coding.code ~ 'HbA1c'))");
    }

    @Test
    void build_emptyRules_returnsExprUnchanged() {
        String cql = builder.build("[Observation]",
                Map.of("rules", Map.of("conjunction", "AND", "rules", List.of(), "groups", List.of())));
        assertThat(cql).isEqualTo("[Observation]");
    }

    // ── Build: rejection paths (CQL injection defense) ───────────────────

    @Test
    void build_missingRulesField_throws() {
        assertThatThrownBy(() -> builder.build("[Observation]", Map.of()))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("values.rules");
    }

    @Test
    void build_unknownOperator_throws() {
        assertThatThrownBy(() -> builder.build("[Observation]",
                wrapRules("AND", rule("status", "evil_op", "final", "string"))))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("unsupported operator");
    }

    @Test
    void build_unknownFieldType_throws() {
        assertThatThrownBy(() -> builder.build("[Observation]",
                wrapRules("AND", rule("status", "equals", "final", "Quantity"))))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("unsupported fieldType");
    }

    @Test
    void build_gtWithNonNumericValue_throws() {
        assertThatThrownBy(() -> builder.build("[Observation]",
                wrapRules("AND", rule("valueQuantity.value", "gt", "not-a-number", "decimal"))))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("numeric literal");
    }

    @Test
    void build_equalsDecimalWithNonNumeric_throws() {
        assertThatThrownBy(() -> builder.build("[Observation]",
                wrapRules("AND", rule("valueQuantity.value", "equals", "abc", "decimal"))))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("numeric value");
    }

    @Test
    void build_beforeWithBadDate_throws() {
        assertThatThrownBy(() -> builder.build("[Observation]",
                wrapRules("AND", rule("effectiveDateTime", "before", "yesterday", "dateTime"))))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("ISO date");
    }

    @Test
    void build_withinLastWithBadUnit_throws() {
        assertThatThrownBy(() -> builder.build("[Observation]",
                wrapRules("AND", rule("effectiveDateTime", "within_last", "30 eons", "dateTime"))))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("unit must be one of");
    }

    @Test
    void build_invalidConjunction_throws() {
        assertThatThrownBy(() -> builder.build("[Observation]",
                Map.of("rules", Map.of(
                        "conjunction", "XOR",
                        "rules", List.of(rule("status", "equals", "final", "string")),
                        "groups", List.of()))))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("conjunction");
    }

    @Test
    void build_fieldWithDangerousChars_quotedAsIdentifier() {
        // A segment that doesn't match the identifier regex (e.g. spaces or punctuation) must
        // be wrapped as a quoted identifier and any embedded double-quote inside the value
        // escaped to \" — never pasted verbatim into CQL.
        String cql = builder.build("[Observation]",
                wrapRules("AND", rule("code.weird name", "is_null", "", "string")));
        assertThat(cql).isEqualTo("([Observation]).where(code.\"weird name\" is null)");
    }

    @Test
    void build_fieldWithEmbeddedQuote_escapedSafely() {
        // A user-supplied segment containing a double-quote must have it escaped inside the
        // quoted identifier so the attacker can't break out of the identifier literal.
        String cql = builder.build("[Observation]",
                wrapRules("AND", rule("code.bad\"name", "is_null", "", "string")));
        assertThat(cql).isEqualTo("([Observation]).where(code.\"bad\\\"name\" is null)");
    }

    @Test
    void build_stringEscapesAreApplied() {
        String cql = builder.build("[Patient]",
                wrapRules("AND", rule("name.family", "equals", "O'Brien", "string")));
        assertThat(cql).isEqualTo("([Patient]).where(name.family = 'O\\'Brien')");
    }

    @Test
    void build_nestingTooDeep_throws() {
        // Build a 12-deep group chain
        Map<String, Object> deepest = Map.of(
                "conjunction", "AND",
                "rules", List.of(rule("status", "is_null", "", "string")),
                "groups", List.of());
        Map<String, Object> current = deepest;
        for (int i = 0; i < 12; i++) {
            current = Map.of(
                    "conjunction", "AND",
                    "rules", List.of(),
                    "groups", List.of(current));
        }
        final Map<String, Object> wrappedRules = current;
        assertThatThrownBy(() -> builder.build("[Observation]", Map.of("rules", wrappedRules)))
                .isInstanceOf(CustomModifierBuildException.class)
                .hasMessageContaining("nesting exceeds");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static Map<String, Object> wrapRules(String conjunction, Map<String, Object>... rules) {
        return Map.of("rules", Map.of(
                "conjunction", conjunction,
                "rules", List.of(rules),
                "groups", List.of()));
    }

    private static Map<String, Object> rule(String field, String operator, String value, String fieldType) {
        return Map.of(
                "id", "r1",
                "field", field,
                "operator", operator,
                "value", value,
                "fieldType", fieldType);
    }
}
