package com.cqlplatform.service.authoring;

import com.cqlplatform.util.CqlEscapeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server-side rebuilder for "custom modifier" CQL fragments.
 *
 * <p>The CDS Authoring UI exposes a rule builder ({@code CustomModifierBuilder.tsx}) that lets
 * users compose arbitrary {@code .where(...)} clauses from a structured rule tree. Before this
 * class existed, the frontend serialized the rendered CQL string in {@code modifier.cqlTemplate}
 * and the backend engine string-substituted it into the output via
 * {@link ExpressionCqlEngine#applyModifier} — a CQL injection sink if a hostile client crafted
 * a payload around the rule builder.
 *
 * <p>This builder is the trusted source of truth: callers pass the structured
 * {@code values} map (the original {@code ModifierRuleGroup} tree) and we re-derive the
 * CQL on the server with the same operator/identifier/literal rules the frontend applies.
 * The client-supplied {@code cqlTemplate} is discarded — only the structured tree is honored.
 *
 * <p>Expected {@code values} schema (mirrors {@code ModifierRule} / {@code ModifierRuleGroup}
 * in the frontend):
 * <pre>
 * {
 *   rules: {                     // ModifierRuleGroup
 *     conjunction: "AND" | "OR",
 *     rules: [ ModifierRule, ... ],
 *     groups: [ ModifierRuleGroup, ... ]
 *   }
 * }
 *
 * ModifierRule = {
 *   field: "value.code.coding.code",   // dot-path; each segment must be a CQL identifier
 *   operator: "equals" | ... ,         // whitelist below
 *   value: "...",                      // operator-specific; validated against per-op regex
 *   fieldType: "code" | "string" | "decimal" | "dateTime"  // disambiguates equals/not_equals
 * }
 * </pre>
 */
@Component
@Slf4j
public class CustomModifierCqlBuilder {

    /** Identifier segment — alphanumeric plus underscore, must start with a letter or underscore. */
    private static final Pattern IDENT_SEGMENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    /** Numeric literal (int / decimal, optional sign). Mirrors frontend numericLiteral check. */
    private static final Pattern NUMERIC = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    /** ISO date or datetime accepted by CQL date literal syntax. */
    private static final Pattern ISO_DATE = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,3})?)?)?$");
    /** Pure non-negative number for duration counts. */
    private static final Pattern DURATION_NUM = Pattern.compile("^\\d+(\\.\\d+)?$");

    private static final Set<String> ALLOWED_DURATION_UNITS = Set.of(
            "year", "years", "month", "months", "week", "weeks", "day", "days",
            "hour", "hours", "minute", "minutes");

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "equals", "not_equals",
            "gt", "gte", "lt", "lte",
            "starts_with", "ends_with", "contains",
            "in",
            "before", "after", "within_last",
            "is_null", "is_not_null");

    private static final Set<String> ALLOWED_FIELD_TYPES = Set.of(
            "code", "string", "decimal", "dateTime");

    /**
     * Build the complete custom-modifier CQL fragment around {@code expr}.
     *
     * @return {@code "(expr).where(<where>)"}, or {@code expr} unchanged when the rules
     *         tree is empty (no constraints to apply).
     * @throws CustomModifierBuildException when the rules tree is structurally invalid
     *         or contains tokens outside the allow-lists.
     */
    public String build(String expr, Map<String, Object> values) {
        Object rulesObj = values == null ? null : values.get("rules");
        if (!(rulesObj instanceof Map<?, ?> rulesMap)) {
            throw new CustomModifierBuildException("custom modifier values.rules missing or not an object");
        }
        @SuppressWarnings("unchecked")
        String whereClause = buildGroup((Map<String, Object>) rulesMap, 0);
        if (whereClause == null || whereClause.isEmpty()) {
            // Empty group — caller treats this as no-op constraint
            return expr;
        }
        return String.format("(%s).where(%s)", expr, whereClause);
    }

    /**
     * Validate-only entry point — invoked by ExpressionTreeValidator at request boundary
     * so save attempts fail fast (400) instead of surfacing during CQL generation (422).
     */
    public void validate(Map<String, Object> values) {
        // build() is the validator — it throws on any structural problem.
        build("__expr__", values);
    }

    private static final int MAX_DEPTH = 10;

    @SuppressWarnings("unchecked")
    private String buildGroup(Map<String, Object> group, int depth) {
        if (depth > MAX_DEPTH) {
            throw new CustomModifierBuildException("custom modifier rule group nesting exceeds " + MAX_DEPTH);
        }
        String conjunction = asString(group.get("conjunction"), "AND");
        if (!"AND".equals(conjunction) && !"OR".equals(conjunction)) {
            throw new CustomModifierBuildException("custom modifier group.conjunction must be AND or OR, was: " + conjunction);
        }

        List<String> parts = new ArrayList<>();

        Object rulesList = group.get("rules");
        if (rulesList instanceof List<?> rules) {
            for (Object ruleObj : rules) {
                if (!(ruleObj instanceof Map<?, ?> ruleMap)) continue;
                String clause = buildRule((Map<String, Object>) ruleMap);
                if (clause != null) parts.add(clause);
            }
        }

        Object subGroups = group.get("groups");
        if (subGroups instanceof List<?> subs) {
            for (Object subObj : subs) {
                if (!(subObj instanceof Map<?, ?> subMap)) continue;
                String subClause = buildGroup((Map<String, Object>) subMap, depth + 1);
                if (subClause != null && !subClause.isEmpty()) {
                    parts.add("(" + subClause + ")");
                }
            }
        }

        if (parts.isEmpty()) return null;
        String op = "AND".equals(conjunction) ? " and " : " or ";
        return String.join(op, parts);
    }

    private String buildRule(Map<String, Object> rule) {
        String field = asString(rule.get("field"), null);
        String operator = asString(rule.get("operator"), null);
        String value = asString(rule.get("value"), "");
        String fieldType = asString(rule.get("fieldType"), null);

        if (field == null || field.isEmpty() || operator == null || operator.isEmpty()) {
            // Incomplete rule — mirror frontend behavior of skipping it
            return null;
        }
        if (!ALLOWED_OPERATORS.contains(operator)) {
            throw new CustomModifierBuildException("custom modifier: unsupported operator '" + operator + "'");
        }
        if (fieldType != null && !fieldType.isEmpty() && !ALLOWED_FIELD_TYPES.contains(fieldType)) {
            throw new CustomModifierBuildException("custom modifier: unsupported fieldType '" + fieldType + "'");
        }

        String accessor = buildAccessor(field);

        return switch (operator) {
            case "is_null" -> accessor + " is null";
            case "is_not_null" -> accessor + " is not null";
            case "equals" -> equalityClause(accessor, value, fieldType, false);
            case "not_equals" -> equalityClause(accessor, value, fieldType, true);
            case "gt" -> numericComparison(accessor, value, ">");
            case "gte" -> numericComparison(accessor, value, ">=");
            case "lt" -> numericComparison(accessor, value, "<");
            case "lte" -> numericComparison(accessor, value, "<=");
            case "starts_with" -> String.format("StartsWith(%s, '%s')", accessor, CqlEscapeUtil.escapeCqlString(value));
            case "ends_with" -> String.format("EndsWith(%s, '%s')", accessor, CqlEscapeUtil.escapeCqlString(value));
            case "contains" -> String.format("PositionOf('%s', %s) >= 0", CqlEscapeUtil.escapeCqlString(value), accessor);
            case "in" -> String.format("%s in \"%s\"", accessor, CqlEscapeUtil.escapeCqlIdentifier(value));
            case "before", "after" -> dateBound(accessor, value, operator);
            case "within_last" -> withinLast(accessor, value);
            default -> throw new CustomModifierBuildException("custom modifier: unreachable operator " + operator);
        };
    }

    /** Build a CQL property accessor from a dot-path. Segments matching IDENT_SEGMENT pass through;
     *  any segment outside that grammar is wrapped as a quoted identifier and escaped. Empty
     *  segments are rejected outright. */
    private String buildAccessor(String field) {
        String[] segments = field.split("\\.");
        if (segments.length == 0) {
            throw new CustomModifierBuildException("custom modifier: empty field path");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty()) {
                throw new CustomModifierBuildException("custom modifier: empty segment in field path '" + field + "'");
            }
            if (i > 0) sb.append('.');
            if (IDENT_SEGMENT.matcher(seg).matches()) {
                sb.append(seg);
            } else {
                sb.append('"').append(CqlEscapeUtil.escapeCqlIdentifier(seg)).append('"');
            }
        }
        return sb.toString();
    }

    private String equalityClause(String accessor, String value, String fieldType, boolean negated) {
        // Code paths use CQL code-equivalence (~) rather than string equality.
        if ("code".equals(fieldType)) {
            String op = negated ? "!~" : "~";
            return String.format("%s %s '%s'", accessor, op, CqlEscapeUtil.escapeCqlString(value));
        }
        if ("decimal".equals(fieldType)) {
            if (!NUMERIC.matcher(value).matches()) {
                throw new CustomModifierBuildException(
                        "custom modifier: decimal field '" + accessor + "' equals requires numeric value, got: " + value);
            }
            return String.format("%s %s %s", accessor, negated ? "!=" : "=", value);
        }
        // Default: treat as string literal
        return String.format("%s %s '%s'", accessor, negated ? "!=" : "=", CqlEscapeUtil.escapeCqlString(value));
    }

    private String numericComparison(String accessor, String value, String sym) {
        if (!NUMERIC.matcher(value).matches()) {
            throw new CustomModifierBuildException(
                    "custom modifier: comparison '" + sym + "' requires numeric literal, got: " + value);
        }
        return String.format("%s %s %s", accessor, sym, value);
    }

    private String dateBound(String accessor, String value, String operator) {
        if (!ISO_DATE.matcher(value).matches()) {
            throw new CustomModifierBuildException(
                    "custom modifier: '" + operator + "' requires ISO date/datetime, got: " + value);
        }
        return String.format("%s %s @%s", accessor, operator, value);
    }

    private String withinLast(String accessor, String value) {
        // Frontend serializes value as "<num> <unit>"
        String[] parts = value.split(" ", 2);
        if (parts.length != 2) {
            throw new CustomModifierBuildException(
                    "custom modifier: within_last value must be '<num> <unit>', got: " + value);
        }
        String num = parts[0];
        String unit = parts[1];
        if (!DURATION_NUM.matcher(num).matches()) {
            throw new CustomModifierBuildException(
                    "custom modifier: within_last requires numeric count, got: " + num);
        }
        if (!ALLOWED_DURATION_UNITS.contains(unit)) {
            throw new CustomModifierBuildException(
                    "custom modifier: within_last unit must be one of " + ALLOWED_DURATION_UNITS + ", got: " + unit);
        }
        return String.format("%s >= Now() - %s %s", accessor, num, unit);
    }

    private static String asString(Object value, String fallback) {
        if (value == null) return fallback;
        return value.toString();
    }
}
