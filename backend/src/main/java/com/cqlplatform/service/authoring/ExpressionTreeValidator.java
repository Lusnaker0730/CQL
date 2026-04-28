package com.cqlplatform.service.authoring;

import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.authoring.ArtifactRequest;
import com.cqlplatform.model.authoring.AuthoringConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressionTreeValidator {

    private final TemplateService templateService;
    private final ModifierService modifierService;

    /**
     * Allowed characters for CQL identifiers (define names, parameter names, etc.).
     * Permits alphanumeric, CJK characters, spaces, hyphens, underscores, dots, and common punctuation.
     * Disallows quotes, backslashes, and control characters that could be used for CQL injection.
     */
    private static final Pattern CQL_IDENTIFIER_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}_\\-. ]{1,255}$");

    /**
     * Comparison operators we allow in modifier {@code values.minOperator} / {@code values.maxOperator}.
     * These flow into CQL via direct {@code String.format} substitution in
     * {@code ExpressionCqlEngine.applyModifier}, so we MUST whitelist them at the
     * boundary (defense in depth — frontend already restricts via dropdown).
     */
    private static final Set<String> COMPARISON_OPERATORS = Set.of(
            "<", "<=", ">", ">=", "=", "!=", "<>");

    /**
     * Whitelist for {@code BooleanComparison.value} — these strings are concatenated
     * verbatim into CQL expressions ({@code (expression) is null} etc.).
     */
    private static final Set<String> BOOLEAN_COMPARISON_VALUES = Set.of(
            "is null", "is not null",
            "is true", "is not true",
            "is false", "is not false");

    /**
     * Whitelist for {@code Qualifier.qualifier}. Frontend offers exactly these strings
     * via dropdown; the backend rejects anything else.
     */
    private static final Set<String> QUALIFIER_TYPES = Set.of("value set", "code");

    /** Numeric literal (int or decimal, optionally signed). */
    private static final Pattern NUMERIC_LITERAL_PATTERN =
            Pattern.compile("^-?\\d+(\\.\\d+)?$");

    /**
     * UCUM-ish unit pattern. Permits the characters that legitimately appear in unit
     * codes (alnum, slash, dot, brackets, dash, underscore, percent, brace) and rejects
     * quotes/backslashes/spaces that would let a caller break out of the {@code 'unit'}
     * literal in the generated CQL.
     */
    private static final Pattern UNIT_PATTERN =
            Pattern.compile("^[A-Za-z0-9/.\\[\\]\\-_%{}*]{1,32}$");

    /**
     * Loose ISO-8601 date or datetime accepted by the CQL parser. The engine's
     * {@code formatDateTimeValue} also normalizes these, but we want to reject obvious
     * garbage upfront.
     */
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+\\-]\\d{2}:?\\d{2})?)?$");

    /**
     * Lookup of {@code values.<key>} → validation rule, keyed by {@code cqlTemplate}.
     * Each entry in the map represents the value-field schema for one modifier kind.
     * Modifiers not in this map have no validatable user-input value fields (e.g.
     * {@code CheckExistence}, {@code BooleanNot}, {@code Count}).
     */
    private static final Map<String, Map<String, FieldRule>> MODIFIER_VALUE_RULES = buildModifierValueRules();

    private enum FieldRule {
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

    private static final Set<String> RESERVED_DEFINE_NAMES = Set.of(
            AuthoringConstants.DEF_MEETS_INCLUSION,
            AuthoringConstants.DEF_MEETS_EXCLUSION,
            AuthoringConstants.DEF_IN_POPULATION,
            AuthoringConstants.DEF_RECOMMENDATION,
            AuthoringConstants.DEF_ERRORS);

    public void validate(ArtifactRequest request) {
        List<String> errors = new ArrayList<>();

        validateTree(request.getExpTreeInclude(), "expTreeInclude", errors);
        validateTree(request.getExpTreeExclude(), "expTreeExclude", errors);
        validateNodeList(request.getBaseElements(), "baseElements", errors);
        validateNodeList(request.getSubpopulations(), "subpopulations", errors);

        validateDefineNames(request, errors);

        if (!errors.isEmpty()) {
            log.warn("Expression tree validation errors: {}", errors);
            throw new ValidationException("Invalid expression tree elements", errors);
        }
    }

    // ------------------------------------------------------------------
    // Define-name uniqueness: same-category duplicates, cross-category
    // collisions, and reserved system name conflicts.
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void validateDefineNames(ArtifactRequest request, List<String> errors) {
        // Collect names per category
        List<String> beNames = new ArrayList<>();
        if (request.getBaseElements() != null) {
            for (Map<String, Object> be : request.getBaseElements()) {
                String name = trimOrNull((String) be.get("name"));
                if (name != null) {
                    beNames.add(name);
                }
            }
        }

        List<String> spNames = new ArrayList<>();
        if (request.getSubpopulations() != null) {
            for (Map<String, Object> sp : request.getSubpopulations()) {
                if (Boolean.TRUE.equals(sp.get("special"))) {
                    continue;
                }
                String name = trimOrNull((String) sp.get("subpopulationName"));
                if (name != null) {
                    spNames.add(name);
                }
            }
        }

        List<String> paramNames = new ArrayList<>();
        if (request.getParameters() != null) {
            for (Map<String, Object> param : request.getParameters()) {
                String name = trimOrNull((String) param.get("name"));
                if (name != null) {
                    paramNames.add(name);
                }
            }
        }

        // 0. Identifier character validation
        for (String name : beNames) {
            if (!CQL_IDENTIFIER_PATTERN.matcher(name).matches()) {
                errors.add(String.format("baseElements: name '%s' contains invalid characters", name));
            }
        }
        for (String name : spNames) {
            if (!CQL_IDENTIFIER_PATTERN.matcher(name).matches()) {
                errors.add(String.format("subpopulations: name '%s' contains invalid characters", name));
            }
        }
        for (String name : paramNames) {
            if (!CQL_IDENTIFIER_PATTERN.matcher(name).matches()) {
                errors.add(String.format("parameters: name '%s' contains invalid characters", name));
            }
        }

        // 1. Same-category duplicates
        findDuplicates(beNames, "baseElements").forEach(dup ->
                errors.add(String.format("baseElements: duplicate name '%s'", dup)));
        findDuplicates(spNames, "subpopulations").forEach(dup ->
                errors.add(String.format("subpopulations: duplicate name '%s'", dup)));
        findDuplicates(paramNames, "parameters").forEach(dup ->
                errors.add(String.format("parameters: duplicate name '%s'", dup)));

        // 2. Cross-category collisions (all share CQL define namespace)
        Set<String> defineNames = new LinkedHashSet<>();
        for (String name : beNames) {
            defineNames.add(name);
        }
        for (String name : spNames) {
            if (!defineNames.add(name)) {
                errors.add(String.format(
                        "subpopulations: name '%s' conflicts with a base element", name));
            }
        }

        // 3. Reserved system define names
        for (String name : beNames) {
            if (isReservedDefineName(name)) {
                errors.add(String.format(
                        "baseElements: name '%s' conflicts with system-generated CQL definition", name));
            }
        }
        for (String name : spNames) {
            if (isReservedDefineName(name)) {
                errors.add(String.format(
                        "subpopulations: name '%s' conflicts with system-generated CQL definition", name));
            }
        }
        for (String name : paramNames) {
            if (isReservedDefineName(name)) {
                errors.add(String.format(
                        "parameters: name '%s' conflicts with system-generated CQL definition", name));
            }
        }
    }

    private static boolean isReservedDefineName(String name) {
        if (RESERVED_DEFINE_NAMES.contains(name)) {
            return true;
        }
        // Recommendations can be numbered: "Recommendation 1", "Recommendation 2", ...
        return name.startsWith(AuthoringConstants.DEF_RECOMMENDATION + " ");
    }

    private static Set<String> findDuplicates(List<String> names, String category) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                duplicates.add(name);
            }
        }
        return duplicates;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ------------------------------------------------------------------
    // Element type / modifier ID validation
    // ------------------------------------------------------------------

    private void validateTree(Map<String, Object> tree, String treeName, List<String> errors) {
        if (tree == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childInstances =
                (List<Map<String, Object>>) tree.get("childInstances");
        if (childInstances != null) {
            for (Map<String, Object> child : childInstances) {
                validateNode(child, treeName, errors);
            }
        }
    }

    private void validateNodeList(List<Map<String, Object>> nodes, String listName, List<String> errors) {
        if (nodes == null) {
            return;
        }
        for (Map<String, Object> node : nodes) {
            validateNode(node, listName, errors);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNode(Map<String, Object> node, String path, List<String> errors) {
        if (node == null) {
            return;
        }

        // Conjunction nodes recurse into childInstances
        Boolean conjunction = toBoolean(node.get("conjunction"));
        if (Boolean.TRUE.equals(conjunction)) {
            List<Map<String, Object>> children =
                    (List<Map<String, Object>>) node.get("childInstances");
            if (children != null) {
                for (Map<String, Object> child : children) {
                    validateNode(child, path, errors);
                }
            }
        }

        // Validate element type
        String type = (String) node.get("type");
        if (type != null && !type.isBlank() && !templateService.isValidElementType(type)) {
            String name = (String) node.get("name");
            errors.add(String.format("%s: unknown element type '%s'%s",
                    path, type, name != null ? " (name: " + name + ")" : ""));
        }

        // Validate external CQL library names
        if ("externalCqlRef".equals(type)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) node.get("fields");
            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    String fieldId = (String) field.get("id");
                    if ("library_name".equals(fieldId) || "library_version".equals(fieldId)) {
                        Object val = field.get("value");
                        if (val instanceof String && !((String) val).isEmpty()
                                && !CQL_IDENTIFIER_PATTERN.matcher((String) val).matches()) {
                            errors.add(String.format("%s: %s '%s' contains invalid characters",
                                    path, fieldId, val));
                        }
                    }
                }
            }
        }

        // Validate modifier IDs
        List<Map<String, Object>> modifiers =
                (List<Map<String, Object>>) node.get("modifiers");
        if (modifiers != null) {
            for (Map<String, Object> modifier : modifiers) {
                String modId = (String) modifier.get("id");
                if (modId != null && !modId.isBlank() && !modifierService.isValidModifierId(modId)) {
                    String modName = (String) modifier.get("name");
                    errors.add(String.format("%s: unknown modifier id '%s'%s",
                            path, modId, modName != null ? " (name: " + modName + ")" : ""));
                }
                validateModifierValues(modifier, path, errors);
            }
        }
    }

    /**
     * Defense-in-depth validation for {@code modifier.values} that flows into
     * {@code ExpressionCqlEngine.applyModifier} and ultimately into generated CQL.
     *
     * <p>Comparison operators, numeric literals, and qualifier types are
     * substituted directly into CQL via {@code String.format("(%s) %s %s", ...)}
     * or FreeMarker {@code ${value}} interpolation — neither path escapes
     * arbitrary user input. Frontend dropdowns enforce these whitelists today,
     * but a malicious client bypassing the UI could craft requests with a
     * crafted {@code minOperator} string containing CQL escape sequences; the
     * CQL parser would eventually reject malformed output, but it's still a
     * defense-in-depth gap (CLAUDE.md "validate at system boundaries").
     * Reject early instead.
     */
    @SuppressWarnings("unchecked")
    private void validateModifierValues(Map<String, Object> modifier, String path, List<String> errors) {
        String cqlTemplate = (String) modifier.get("cqlTemplate");
        if (cqlTemplate == null || cqlTemplate.isBlank()) return;

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
            // Empty optional fields are legitimate for most modifiers (engine just
            // skips). Don't fail validation on them.
            if (val.isEmpty()) continue;

            if (!isFieldValid(rule, val)) {
                String modName = (String) modifier.get("name");
                errors.add(String.format(
                        "%s: modifier %s field '%s' has invalid value '%s' for rule %s",
                        path,
                        modName != null ? "'" + modName + "'" : cqlTemplate,
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

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }
}
