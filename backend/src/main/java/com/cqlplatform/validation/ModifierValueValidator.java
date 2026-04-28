package com.cqlplatform.validation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Whitelist + regex validation for {@code modifier.values} fields that flow into
 * generated CQL via {@code ExpressionCqlEngine.applyModifier} (direct
 * {@code String.format} substitution or FreeMarker {@code ${value}} interpolation).
 *
 * <p>Frontend dropdowns enforce most of these constraints today, but a malicious
 * client bypassing the UI could craft requests with comparison operators, units, or
 * datetime fields containing CQL escape sequences. The CQL parser would eventually
 * reject malformed output, but we validate at the boundary as defense in depth
 * (CLAUDE.md: "validate at system boundaries").
 *
 * <p>Used by both {@code authoring.ExpressionTreeValidator} (CDS Hooks artifacts)
 * and {@code ecqm.EcqmExpressionTreeValidator} (eCQM artifacts) — they share the
 * same modifier templates and thus the same validation rules.
 */
public final class ModifierValueValidator {

    /**
     * Comparison operators allowed in {@code values.minOperator} / {@code values.maxOperator}.
     */
    private static final Set<String> COMPARISON_OPERATORS = Set.of(
            "<", "<=", ">", ">=", "=", "!=", "<>");

    /**
     * Whitelist for {@code BooleanComparison.value} — concatenated verbatim into CQL
     * ({@code (expression) is null} etc.).
     */
    private static final Set<String> BOOLEAN_COMPARISON_VALUES = Set.of(
            "is null", "is not null",
            "is true", "is not true",
            "is false", "is not false");

    /**
     * Whitelist for {@code Qualifier.qualifier}. Frontend offers exactly these via dropdown.
     */
    private static final Set<String> QUALIFIER_TYPES = Set.of("value set", "code");

    /** Numeric literal (int or decimal, optionally signed). */
    private static final Pattern NUMERIC_LITERAL_PATTERN =
            Pattern.compile("^-?\\d+(\\.\\d+)?$");

    /**
     * UCUM-ish unit pattern. Permits chars that legitimately appear in unit codes
     * (alnum, slash, dot, brackets, dash, underscore, percent, brace, asterisk) and
     * rejects quotes/backslashes/spaces that would let a caller break out of the
     * {@code 'unit'} literal in generated CQL.
     */
    private static final Pattern UNIT_PATTERN =
            Pattern.compile("^[A-Za-z0-9/.\\[\\]\\-_%{}*]{1,32}$");

    /** Loose ISO-8601 date or datetime accepted by the CQL parser. */
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+\\-]\\d{2}:?\\d{2})?)?$");

    /**
     * Lookup of {@code values.<key>} → validation rule, keyed by {@code cqlTemplate}.
     * Modifiers not in this map have no validatable user-input value fields (e.g.
     * {@code CheckExistence}, {@code BooleanNot}, {@code Count}).
     */
    private static final Map<String, Map<String, FieldRule>> MODIFIER_VALUE_RULES = buildModifierValueRules();

    public enum FieldRule {
        /** Numeric literal (int / decimal, optional sign). */
        NUMERIC,
        /** Comparison operator from COMPARISON_OPERATORS. */
        COMPARISON_OP,
        /** Quantity unit (UCUM-ish). */
        UNIT,
        /** ISO date / datetime. */
        DATETIME,
        /** BooleanComparison fragment from whitelist. */
        BOOLEAN_COMPARISON_VALUE,
        /** Qualifier type from {value set, code}. */
        QUALIFIER_TYPE,
        /** Free-text string — engine escapes via escapeCqlString; no further check needed. */
        FREE_TEXT_ESCAPED
    }

    private static Map<String, Map<String, FieldRule>> buildModifierValueRules() {
        Map<String, Map<String, FieldRule>> m = new HashMap<>();
        m.put("BooleanComparison", Map.of("value", FieldRule.BOOLEAN_COMPARISON_VALUE));
        m.put("ValueComparisonNumber", Map.of(
                "minOperator", FieldRule.COMPARISON_OP,
                "minValue", FieldRule.NUMERIC,
                "maxOperator", FieldRule.COMPARISON_OP,
                "maxValue", FieldRule.NUMERIC,
                "unit", FieldRule.UNIT));
        m.put("ValueComparisonObservation", Map.of(
                "minOperator", FieldRule.COMPARISON_OP,
                "minValue", FieldRule.NUMERIC,
                "maxOperator", FieldRule.COMPARISON_OP,
                "maxValue", FieldRule.NUMERIC,
                "unit", FieldRule.UNIT));
        m.put("ConvertUnits", Map.of("unit", FieldRule.UNIT));
        m.put("WithUnit", Map.of("unit", FieldRule.UNIT));
        m.put("LookBackModifier", Map.of(
                "value", FieldRule.NUMERIC,
                "unit", FieldRule.UNIT));
        m.put("EqualsString", Map.of("value", FieldRule.FREE_TEXT_ESCAPED));
        m.put("StartsWithString", Map.of("value", FieldRule.FREE_TEXT_ESCAPED));
        m.put("EndsWithString", Map.of("value", FieldRule.FREE_TEXT_ESCAPED));
        m.put("BeforeTimePrecise", Map.of("value", FieldRule.DATETIME));
        m.put("BeforeDateTimePrecise", Map.of("value", FieldRule.DATETIME));
        m.put("AfterTimePrecise", Map.of("value", FieldRule.DATETIME));
        m.put("AfterDateTimePrecise", Map.of("value", FieldRule.DATETIME));
        m.put("ContainsInteger", Map.of("value", FieldRule.NUMERIC));
        m.put("ContainsDecimal", Map.of("value", FieldRule.NUMERIC));
        m.put("ContainsQuantity", Map.of(
                "value", FieldRule.NUMERIC,
                "unit", FieldRule.UNIT));
        m.put("ContainsDateTime", Map.of("value", FieldRule.DATETIME));
        m.put("Qualifier", Map.of(
                "qualifier", FieldRule.QUALIFIER_TYPE,
                "valueSet", FieldRule.FREE_TEXT_ESCAPED,
                "code", FieldRule.FREE_TEXT_ESCAPED));
        return Map.copyOf(m);
    }

    private ModifierValueValidator() {
    }

    /**
     * Validate a single modifier's {@code values} map. Empty / missing fields are
     * tolerated (engine skips them); only present-but-malformed values produce errors.
     *
     * @param modifier modifier map (must have {@code cqlTemplate} for any check to occur)
     * @param path     human-readable path for error messages (e.g. {@code "expTreeInclude"})
     * @param errors   mutable list errors are appended to
     */
    @SuppressWarnings("unchecked")
    public static void validate(Map<String, Object> modifier, String path, java.util.List<String> errors) {
        if (modifier == null) return;

        Object cqlTemplateObj = modifier.get("cqlTemplate");
        if (!(cqlTemplateObj instanceof String cqlTemplate) || cqlTemplate.isBlank()) return;

        Map<String, FieldRule> rules = MODIFIER_VALUE_RULES.get(cqlTemplate);
        if (rules == null) return; // unknown / no validatable inputs (CheckExistence etc.)

        Object rawValues = modifier.get("values");
        if (!(rawValues instanceof Map<?, ?>)) return;
        Map<String, Object> values = (Map<String, Object>) rawValues;

        for (Map.Entry<String, FieldRule> entry : rules.entrySet()) {
            String fieldKey = entry.getKey();
            FieldRule rule = entry.getValue();
            Object raw = values.get(fieldKey);
            if (raw == null) continue;
            String val = String.valueOf(raw).trim();
            if (val.isEmpty()) continue;

            if (!isFieldValid(rule, val)) {
                Object modName = modifier.get("name");
                errors.add(String.format(
                        "%s: modifier %s field '%s' has invalid value '%s' for rule %s",
                        path,
                        modName instanceof String mn ? "'" + mn + "'" : cqlTemplate,
                        fieldKey, val, rule.name()));
            }
        }
    }

    private static boolean isFieldValid(FieldRule rule, String value) {
        return switch (rule) {
            case NUMERIC -> NUMERIC_LITERAL_PATTERN.matcher(value).matches();
            case COMPARISON_OP -> COMPARISON_OPERATORS.contains(value);
            case UNIT -> UNIT_PATTERN.matcher(value).matches();
            case DATETIME -> DATETIME_PATTERN.matcher(value).matches();
            case BOOLEAN_COMPARISON_VALUE -> BOOLEAN_COMPARISON_VALUES.contains(value);
            case QUALIFIER_TYPE -> QUALIFIER_TYPES.contains(value);
            case FREE_TEXT_ESCAPED -> true; // escapeCqlString handles these
        };
    }
}
