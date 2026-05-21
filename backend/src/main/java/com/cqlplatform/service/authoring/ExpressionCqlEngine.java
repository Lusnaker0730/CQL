package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.AuthoringConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Shared engine for converting expression trees into CQL fragments.
 * Used by both CDS CqlArtifactBuilder and eCQM EcqmCqlBuilder.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExpressionCqlEngine {

    private final CqlTemplateEngine templateEngine;
    private final ModifierService modifierService;
    private final CustomModifierCqlBuilder customModifierCqlBuilder;

    private static final Map<String, String> FHIR_VERSION_MAP = AuthoringConstants.FHIR_VERSION_MAP;
    private static final Map<String, String> FHIR_HELPERS_VERSION_MAP = AuthoringConstants.FHIR_HELPERS_VERSION_MAP;

    // PAT-161: arithmetic Quantity-literal operand validation.
    // value must be a plain CQL numeric literal; unit must be UCUM-safe characters
    // only (no single-quote → can't escape the Quantity literal delimiter; no
    // whitespace/backslash → can't smuggle CQL fragments).
    private static final Pattern ARITHMETIC_NUMERIC_PATTERN =
            Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern ARITHMETIC_UCUM_UNIT_PATTERN =
            Pattern.compile("[A-Za-z0-9./*+\\-()\\[\\]{}%_]{1,32}");

    // PAT-161: arithmetic operator allow-list. mod/div are CQL keyword operators
    // (word-style); ^ is the CQL exponentiation operator. Failsafe to "+" for
    // anything outside this set so a malicious operator string can never reach
    // the emitted CQL.
    private static final Set<String> ARITHMETIC_OPERATORS = Set.of(
            "+", "-", "*", "/", "mod", "div", "^");

    // PAT-162: arithmeticUnary function allow-list. All are CQL 1.5 built-ins
    // taking a single Decimal/Integer/Quantity argument. Failsafe to "Abs" for
    // anything outside this set (defensive fallback, mirrors ARITHMETIC_OPERATORS).
    private static final Set<String> UNARY_FUNCTIONS = Set.of(
            "Abs", "Ceiling", "Floor", "Negate", "Round", "Truncate");

    // PAT-164: Round precision argument validation. CQL spec allows
    // Round(x, precision) where precision is a non-negative Integer; anything
    // failing this regex falls back to single-arg Round(x).
    private static final Pattern ROUND_PRECISION_PATTERN = Pattern.compile("\\d+");

    /**
     * Per-build context holding base elements for cross-reference lookups
     * and a warnings collector. Created fresh for each build invocation
     * to ensure thread safety.
     */
    /**
     * Explicit render mode replaces the ad-hoc {@code preserveListReturn} flag flipping that
     * used to happen in four separate places (two in this class, two in EcqmCqlBuilder).
     * Each mode makes the rendering intent of the current recursion frame explicit; callers
     * switch modes via {@link BuildContext#withRenderMode} which guarantees lexically-scoped
     * restoration and can't be forgotten.
     */
    public enum RenderMode {
        /** Default — list-returning expressions are wrapped in {@code exists(...)}. */
        STANDARD,
        /**
         * Continuous-variable measure's Measure Population — preserve the list shape so the
         * Measure Observation wrapper can iterate, and skip modifiers that collapse a list
         * to a single resource / extract a scalar (see {@link #classifyListBehavior}).
         */
        CV_MEASURE_POPULATION,
        /**
         * Inside an episode-based conjunction's filter branch — a leaf expression here is
         * a boolean predicate over the already-identified episode, so STANDARD rendering
         * (exists-wrap for lists) applies to it independently. Kept as a named mode for
         * readability; behaviorally identical to STANDARD.
         */
        CV_EPISODE_FILTER
    }

    public static class BuildContext {
        public final List<Map<String, Object>> baseElements;
        public final Map<String, String> baseElementNameIndex;
        public final Map<String, String> parameterNameIndex;
        public final List<String> warnings = new ArrayList<>();

        /** Current render mode — package-visible for read; mutate only via {@link #withRenderMode}. */
        RenderMode renderMode = RenderMode.STANDARD;
        /** Resource type to preserve as a list when {@code renderMode = CV_MEASURE_POPULATION}. */
        String episodeResourceType = null;

        /**
         * Whether the output library declares a {@code "Measurement Period"} parameter. eCQM
         * artifacts always do; CDS artifacts never do. When {@code true}, time-dependent
         * elements like {@code AgeRange} bind their age function to the period end
         * ({@code AgeInYearsAt(end of "Measurement Period")}) so results are reproducible
         * regardless of when the measure is evaluated. When {@code false}, they fall back to
         * {@code AgeInYears()} (system clock).
         *
         * <p>Set by {@link EcqmCqlBuilder} at the top of {@code buildEcqmCql}; stays
         * {@code false} for CDS/authoring paths where no Measurement Period exists.
         */
        public boolean hasMeasurementPeriod = false;

        public BuildContext(List<Map<String, Object>> baseElements, List<Map<String, Object>> parameters) {
            this.baseElements = baseElements;
            Map<String, String> beIdx = new HashMap<>();
            if (baseElements != null) {
                for (Map<String, Object> be : baseElements) {
                    Object uid = be.get("uniqueId");
                    Object name = be.get("name");
                    if (uid != null && name != null) {
                        beIdx.put(uid.toString(), name.toString());
                    }
                }
            }
            this.baseElementNameIndex = beIdx;

            Map<String, String> pIdx = new HashMap<>();
            if (parameters != null) {
                for (Map<String, Object> p : parameters) {
                    Object uid = p.get("uniqueId");
                    Object name = p.get("name");
                    if (uid != null && name != null) {
                        pIdx.put(uid.toString(), name.toString());
                    }
                }
            }
            this.parameterNameIndex = pIdx;
        }

        public RenderMode getRenderMode() { return renderMode; }

        /** Run {@code body} with {@code renderMode} temporarily set. Mode is always restored. */
        public <T> T withRenderMode(RenderMode mode, String episodeType, java.util.function.Supplier<T> body) {
            RenderMode prevMode = this.renderMode;
            String prevEpisode = this.episodeResourceType;
            this.renderMode = mode;
            this.episodeResourceType = episodeType;
            try {
                return body.get();
            } finally {
                this.renderMode = prevMode;
                this.episodeResourceType = prevEpisode;
            }
        }

        /** Convenience overload for modes that don't carry an episode type. */
        public <T> T withRenderMode(RenderMode mode, java.util.function.Supplier<T> body) {
            return withRenderMode(mode, this.episodeResourceType, body);
        }

        public void warn(String message) {
            warnings.add(message);
        }

        public String findBaseElementName(String uniqueId) {
            if (uniqueId == null) return null;
            return baseElementNameIndex.get(uniqueId);
        }

        public String findParameterName(String uniqueId) {
            if (uniqueId == null) return null;
            return parameterNameIndex.get(uniqueId);
        }
    }

    // ── Expression building ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public String buildConjunctionExpression(Map<String, Object> group, BuildContext ctx) {
        if (group == null) {
            log.debug("buildConjunctionExpression: group is null");
            return "null";
        }

        List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("childInstances");
        if (children == null || children.isEmpty()) {
            log.debug("buildConjunctionExpression: childInstances is {} (keys: {})",
                    children == null ? "null" : "empty", group.keySet());
            return "null";
        }
        log.debug("buildConjunctionExpression: processing {} children", children.size());

        // Episode-based CV: separate the episode resource from filter conditions
        if (ctx.getRenderMode() == RenderMode.CV_MEASURE_POPULATION && ctx.episodeResourceType != null
                && "And".equals(getStr(group, "id", "And"))) {
            return buildEpisodeConjunction(children, ctx);
        }

        String conjId = getStr(group, "id", "And");
        String operator;
        switch (conjId) {
            case "Or":        operator = " or "; break;
            case "Union":     operator = " union "; break;
            case "Intersect": operator = " intersect "; break;
            default:          operator = " and "; break;
        }

        List<String> childExprs = new ArrayList<>();
        for (Map<String, Object> child : children) {
            Boolean conjunction = (Boolean) child.get("conjunction");
            if (Boolean.TRUE.equals(conjunction)) {
                childExprs.add("(" + buildConjunctionExpression(child, ctx) + ")");
            } else {
                childExprs.add(buildExpression(child, ctx));
            }
        }

        return String.join(operator + "\n  ", childExprs);
    }

    /**
     * Builds a conjunction for episode-based CV Measure Population.
     * The first element matching the episode resource type is kept as a list query;
     * all other elements become boolean filter conditions in a where clause.
     */
    @SuppressWarnings("unchecked")
    private String buildEpisodeConjunction(List<Map<String, Object>> children, BuildContext ctx) {
        String episodeType = ctx.episodeResourceType.toLowerCase();
        String baseExpr = null;
        List<String> filterExprs = new ArrayList<>();

        for (Map<String, Object> child : children) {
            Boolean conjunction = (Boolean) child.get("conjunction");
            if (Boolean.TRUE.equals(conjunction)) {
                filterExprs.add("(" + ctx.withRenderMode(RenderMode.CV_EPISODE_FILTER,
                        () -> buildConjunctionExpression(child, ctx)) + ")");
                continue;
            }

            String childType = getStr(child, "type", "").toLowerCase();
            if (baseExpr == null && childType.contains(episodeType)) {
                // This is the episode resource — build as list query (caller's CV_MEASURE_POPULATION mode)
                baseExpr = buildExpression(child, ctx);
            } else {
                filterExprs.add(ctx.withRenderMode(RenderMode.CV_EPISODE_FILTER,
                        () -> buildExpression(child, ctx)));
            }
        }

        if (baseExpr == null) {
            // No matching episode element found — fall back to normal boolean conjunction
            return String.join(" and \n  ", filterExprs);
        }

        if (filterExprs.isEmpty()) {
            return baseExpr;
        }

        return baseExpr + " _ep where " + String.join(" and ", filterExprs);
    }

    @SuppressWarnings("unchecked")
    public String buildExpression(Map<String, Object> element, BuildContext ctx) {
        String type = getStr(element, "type", "");
        String name = getStr(element, "name", "Unknown");

        List<Map<String, Object>> fields = (List<Map<String, Object>>) element.get("fields");
        String elementName = getFieldValue(fields, "element_name", name);

        String expr;
        switch (type) {
            case "AgeRange":
                expr = buildAgeRangeExpression(fields, ctx);
                break;
            case "Gender":
                expr = buildGenderExpression(fields);
                break;
            case "baseElementRef": {
                String refId = getFieldValue(fields, "reference_id", "");
                String refName = ctx.findBaseElementName(refId);
                expr = String.format("\"%s\"", escapeCqlIdentifier(refName != null ? refName : elementName));
                break;
            }
            case "parameterRef": {
                String refId = getFieldValue(fields, "reference_id", "");
                String resolvedName = ctx.findParameterName(refId);
                expr = String.format("\"%s\"", escapeCqlIdentifier(resolvedName != null ? resolvedName : elementName));
                break;
            }
            case "externalCqlRef": {
                String libName = getFieldValue(fields, "library_name", "");
                String refId = getFieldValue(fields, "reference_id", "");
                String defName = refId.contains(":") ? refId.substring(refId.indexOf(':') + 1) : elementName;
                if (libName != null && !libName.isEmpty()) {
                    expr = String.format("\"%s\".\"%s\"", escapeCqlIdentifier(libName), escapeCqlIdentifier(defName));
                } else {
                    expr = String.format("\"%s\"", escapeCqlIdentifier(defName));
                }
                break;
            }
            case "externalCqlElement": {
                // Library definition reference from LibraryDefinitionPicker (eCQM integration)
                String alias = getFieldValue(fields, "alias", "");
                String defName = getFieldValue(fields, "definitionName", "");
                if (alias != null && !alias.isEmpty() && defName != null && !defName.isEmpty()) {
                    expr = String.format("\"%s\".\"%s\"", escapeCqlIdentifier(alias), escapeCqlIdentifier(defName));
                } else if (defName != null && !defName.isEmpty()) {
                    expr = String.format("\"%s\"", escapeCqlIdentifier(defName));
                } else {
                    ctx.warn(String.format("External CQL element '%s' has no definition name", elementName));
                    expr = "null /* missing library definition */";
                }
                break;
            }
            case "arithmeticExpression": {
                // PAT-163: N-ary. New shape stores operands[] + operators[]; old PAT-161
                // 2-ary shape (left_*/right_*/operator) still works via fallback for any
                // artifact that escapes the V56 Flyway migration.
                Object operandsRaw = getFieldRawValue(fields, "operands");
                Object operatorsRaw = getFieldRawValue(fields, "operators");
                if (operandsRaw instanceof List && operatorsRaw instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> operands = (List<Map<String, Object>>) operandsRaw;
                    @SuppressWarnings("unchecked")
                    List<Object> rawOps = (List<Object>) operatorsRaw;
                    expr = emitNaryArithmeticCql(operands, rawOps, ctx, elementName);
                } else {
                    // Legacy 2-ary shape — kept as defense-in-depth even after V56 migration.
                    String operator = getFieldValue(fields, "operator", "+");
                    if (!ARITHMETIC_OPERATORS.contains(operator)) {
                        operator = "+";
                    }
                    String leftCql = resolveArithmeticOperand(fields, "left", ctx);
                    String rightCql = resolveArithmeticOperand(fields, "right", ctx);
                    if (leftCql != null && rightCql != null) {
                        expr = String.format("%s %s %s", leftCql, operator, rightCql);
                    } else {
                        ctx.warn(String.format("Arithmetic element '%s' has unresolved operand(s)", elementName));
                        expr = "null /* unresolved arithmetic operands */";
                    }
                }
                break;
            }
            case "arithmeticUnary": {
                // PAT-162: unary CQL function applied to a single operand
                // (Abs/Floor/Ceiling/Round/Truncate/Negate). Function name is
                // allow-listed; operand reuses the PAT-161 3-mode resolver
                // (element / literal / quantity) with side="operand".
                // PAT-164: Round accepts an optional Integer precision arg —
                // emit Round(x, N) when 'precision' field is a non-negative
                // integer; otherwise emit single-arg Round(x).
                String fn = getFieldValue(fields, "function", "Abs");
                if (!UNARY_FUNCTIONS.contains(fn)) {
                    fn = "Abs";
                }
                String operandCql = resolveArithmeticOperand(fields, "operand", ctx);
                if (operandCql != null) {
                    if ("Round".equals(fn)) {
                        String precision = getFieldValue(fields, "precision", "").trim();
                        if (!precision.isEmpty() && ROUND_PRECISION_PATTERN.matcher(precision).matches()) {
                            expr = String.format("Round(%s, %s)", operandCql, precision);
                        } else {
                            expr = String.format("Round(%s)", operandCql);
                        }
                    } else {
                        expr = String.format("%s(%s)", fn, operandCql);
                    }
                } else {
                    ctx.warn(String.format("Unary element '%s' has unresolved operand", elementName));
                    expr = "null /* unresolved unary operand */";
                }
                break;
            }
            default:
                if (type.startsWith("Generic")) {
                    expr = buildGenericResourceExpression(type, fields);
                } else {
                    ctx.warn(String.format("Unknown element type '%s' for element '%s'; defaulting to 'true'", type, elementName));
                    expr = "true /* " + elementName + " */";
                }
        }

        List<Map<String, Object>> modifiers = (List<Map<String, Object>>) element.get("modifiers");
        if (modifiers != null) {
            for (Map<String, Object> mod : modifiers) {
                // Episode-based CV Measure Population: skip modifiers that collapse a list
                // to a single item or extract non-resource values, so the population returns
                // a resource list for the Measure Observation wrapper to iterate over.
                if (ctx.getRenderMode() == RenderMode.CV_MEASURE_POPULATION && isListCollapsingOrValueExtractingModifier(mod)) {
                    String modName = getStr(mod, "name", getStr(mod, "id", "?"));
                    String behavior = classifyListBehavior(mod);
                    // Surface the silent skip to the author: the UI's CQL preview warns panel
                    // picks up ctx.warnings so the user sees WHY their modifier chain was shortened.
                    ctx.warn(String.format(
                            "Modifier '%s' (%s) skipped in Measure Population: "
                                    + "continuous-variable measures require a resource list for the "
                                    + "Measure Observation function to iterate over. This modifier is "
                                    + "applied inside the observation function body, not at the "
                                    + "population level.",
                            modName, behavior));
                    continue;
                }
                expr = applyModifier(expr, mod, ctx);
            }
        }

        String finalReturnType = getFinalReturnType(element, modifiers);
        if (finalReturnType != null && finalReturnType.startsWith("list_of_")
                && ctx.getRenderMode() != RenderMode.CV_MEASURE_POPULATION) {
            expr = String.format("exists(%s)", expr);
        }

        return expr;
    }

    /**
     * Resolve one side (left or right) of an arithmetic expression.
     * Supports three modes:
     * <ul>
     *   <li>{@code element} (default) — reference to another base element by id</li>
     *   <li>{@code literal} — raw numeric / Quantity string (legacy single-field form)</li>
     *   <li>{@code quantity} — PAT-161: structured Quantity from separate value + UCUM unit fields</li>
     * </ul>
     * Returns {@code null} when the operand is missing or fails validation —
     * caller emits {@code null /* unresolved arithmetic operands *&#47;} for the
     * whole expression so the CQL stays parseable and the warning surfaces in UI.
     */
    private String resolveArithmeticOperand(List<Map<String, Object>> fields, String side, BuildContext ctx) {
        String mode = getFieldValue(fields, side + "_mode", "element");
        if ("literal".equals(mode)) {
            String literal = getFieldValue(fields, side + "_literal", "");
            if (literal == null || literal.isEmpty()) return null;
            // Validate: only allow numeric literals (digits, dots, minus, spaces, quotes for units)
            if (!literal.matches("[\\d.\\-]+(?:\\s*'[^']*')?")) {
                return null;
            }
            return literal;
        }
        if ("quantity".equals(mode)) {
            String rawValue = getFieldValue(fields, side + "_literal_value", "");
            String rawUnit = getFieldValue(fields, side + "_literal_unit", "");
            if (rawValue == null || rawUnit == null) return null;
            String value = rawValue.trim();
            String unit = rawUnit.trim();
            if (value.isEmpty() || unit.isEmpty()) return null;
            if (!ARITHMETIC_NUMERIC_PATTERN.matcher(value).matches()) return null;
            if (!ARITHMETIC_UCUM_UNIT_PATTERN.matcher(unit).matches()) return null;
            return String.format("%s '%s'", value, unit);
        }
        // Element reference mode. Binary arithmetic uses `<side>_operand_id`
        // (left_operand_id / right_operand_id), but PAT-162's unary uses just
        // `operand_id` (avoiding the ugly `operand_operand_id`). Both work here.
        String operandIdField = "operand".equals(side) ? "operand_id" : side + "_operand_id";
        String refId = getFieldValue(fields, operandIdField, "");
        if (refId == null || refId.isEmpty()) return null;
        String refName = ctx.findBaseElementName(refId);
        return refName != null ? String.format("\"%s\"", escapeCqlIdentifier(refName)) : null;
    }

    // PAT-163: N-ary operand limits. Below 2 makes no sense (use a baseElementRef
    // instead); above 10 is a UX guardrail — long chains should be split into
    // multiple base elements for readability.
    static final int NARY_MIN_OPERANDS = 2;
    static final int NARY_MAX_OPERANDS = 10;

    // PAT-163: CQL operator precedence (higher numbers bind tighter). Mirrors
    // CQL 1.5 spec §10. {@code ^} is tightest; {@code *}, {@code /}, {@code mod},
    // {@code div} share level 2; {@code +} and {@code -} share level 1.
    private static int arithmeticPrecedence(String op) {
        switch (op) {
            case "^":   return 3;
            case "*":
            case "/":
            case "mod":
            case "div": return 2;
            case "+":
            case "-":   return 1;
            default:    return 1; // failsafe — shouldn't happen because of allow-list
        }
    }

    /**
     * PAT-163: emit N-ary arithmetic CQL with explicit precedence parens.
     *
     * <p>Strategy: walk operators left-to-right; whenever the next operator has
     * a different precedence than the current run, close the current group with
     * parens. Always emit parens around any non-trivial sub-group so the reader
     * sees the grouping without doing precedence math in their head. E.g.
     * {@code [a,b,c]} with {@code [+,*]} emits {@code a + (b * c)}.
     *
     * <p>Returns {@code null /* unresolved arithmetic operands *&#47;} when any
     * operand or operator is malformed or out of range, matching the legacy
     * 2-ary failure mode.
     */
    private String emitNaryArithmeticCql(
            List<Map<String, Object>> operands,
            List<Object> operators,
            BuildContext ctx, String elementName) {
        if (operands == null || operators == null
                || operands.size() < NARY_MIN_OPERANDS
                || operands.size() > NARY_MAX_OPERANDS
                || operators.size() != operands.size() - 1) {
            ctx.warn(String.format(
                    "Arithmetic element '%s' has invalid N-ary shape (operands=%d, operators=%d)",
                    elementName,
                    operands == null ? -1 : operands.size(),
                    operators == null ? -1 : operators.size()));
            return "null /* unresolved arithmetic operands */";
        }

        // Resolve each operand to CQL. Each operand map has the same shape as
        // the legacy fields (mode + operand_id / operand_literal / operand_literal_value
        // / operand_literal_unit), so we adapt to the existing resolver by wrapping
        // each into a single-field list.
        List<String> operandCqls = new ArrayList<>(operands.size());
        for (Map<String, Object> op : operands) {
            String cql = resolveNaryOperand(op, ctx);
            if (cql == null) {
                ctx.warn(String.format("Arithmetic element '%s' has unresolved operand(s)", elementName));
                return "null /* unresolved arithmetic operands */";
            }
            operandCqls.add(cql);
        }

        // Validate operators against allow-list, fail-safe to "+".
        List<String> opStrings = new ArrayList<>(operators.size());
        for (Object opRaw : operators) {
            String op = opRaw == null ? "+" : opRaw.toString();
            if (!ARITHMETIC_OPERATORS.contains(op)) op = "+";
            opStrings.add(op);
        }

        return groupByPrecedence(operandCqls, opStrings);
    }

    /**
     * PAT-163: per-operand resolver for N-ary shape. The operand map keys are
     * the same as the legacy field IDs (mode / operand_id / operand_literal /
     * operand_literal_value / operand_literal_unit) — that way an operand map
     * extracted from the N-ary {@code operands[]} array maps to exactly what
     * {@link #resolveArithmeticOperand} expects for side {@code "operand"}.
     */
    private String resolveNaryOperand(Map<String, Object> operand, BuildContext ctx) {
        String mode = strFromMap(operand, "mode", "element");
        if ("literal".equals(mode)) {
            String literal = strFromMap(operand, "operand_literal", "");
            if (literal == null || literal.isEmpty()) return null;
            if (!literal.matches("[\\d.\\-]+(?:\\s*'[^']*')?")) return null;
            return literal;
        }
        if ("quantity".equals(mode)) {
            String value = strFromMap(operand, "operand_literal_value", "").trim();
            String unit = strFromMap(operand, "operand_literal_unit", "").trim();
            if (value.isEmpty() || unit.isEmpty()) return null;
            if (!ARITHMETIC_NUMERIC_PATTERN.matcher(value).matches()) return null;
            if (!ARITHMETIC_UCUM_UNIT_PATTERN.matcher(unit).matches()) return null;
            return String.format("%s '%s'", value, unit);
        }
        // element ref mode
        String refId = strFromMap(operand, "operand_id", "");
        if (refId == null || refId.isEmpty()) return null;
        String refName = ctx.findBaseElementName(refId);
        return refName != null ? String.format("\"%s\"", escapeCqlIdentifier(refName)) : null;
    }

    private static String strFromMap(Map<String, Object> map, String key, String defaultVal) {
        if (map == null) return defaultVal;
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    /**
     * PAT-163: group operands by operator precedence, inserting parens around
     * any tighter-binding sub-expression. Recursive: split on the lowest-precedence
     * operator, emit left + " op " + right, wrapping each side in parens if it
     * itself contains operators.
     */
    private String groupByPrecedence(List<String> operands, List<String> operators) {
        if (operators.isEmpty()) {
            return operands.get(0);
        }
        // Find rightmost lowest-precedence operator (left-associative grouping).
        int minPrec = Integer.MAX_VALUE;
        int splitAt = -1;
        for (int i = operators.size() - 1; i >= 0; i--) {
            int p = arithmeticPrecedence(operators.get(i));
            if (p < minPrec) {
                minPrec = p;
                splitAt = i;
            }
        }
        // Left/right partitions
        List<String> leftOperands = operands.subList(0, splitAt + 1);
        List<String> leftOperators = operators.subList(0, splitAt);
        List<String> rightOperands = operands.subList(splitAt + 1, operands.size());
        List<String> rightOperators = operators.subList(splitAt + 1, operators.size());
        String left = groupByPrecedence(leftOperands, leftOperators);
        String right = groupByPrecedence(rightOperands, rightOperators);
        // Wrap sides that contain operators (i.e. are not single operands).
        if (!leftOperators.isEmpty()) left = "(" + left + ")";
        if (!rightOperators.isEmpty()) right = "(" + right + ")";
        return left + " " + operators.get(splitAt) + " " + right;
    }

    public String buildAgeRangeExpression(List<Map<String, Object>> fields, BuildContext ctx) {
        String minAge = getFieldValue(fields, "min_age", null);
        String maxAge = getFieldValue(fields, "max_age", null);
        String unit = getFieldValue(fields, "unit_of_time", "year");
        String ageFunction = mapUnitToAgeFunction(unit, ctx.hasMeasurementPeriod);

        Map<String, Object> model = new HashMap<>();
        model.put("ageFunction", ageFunction);
        model.put("minAge", minAge != null && !minAge.isEmpty() ? minAge : "");
        model.put("maxAge", maxAge != null && !maxAge.isEmpty() ? maxAge : "");
        return templateEngine.render("elements/AgeRange.ftl", model);
    }

    /**
     * Deprecated — kept for callers that can't supply a {@link BuildContext}. Emits the
     * system-clock form ({@code AgeInYears()}) which is wrong for eCQM (results drift with
     * wall-clock time). Use {@link #buildAgeRangeExpression(List, BuildContext)} instead.
     */
    @Deprecated(forRemoval = true)
    public String buildAgeRangeExpression(List<Map<String, Object>> fields) {
        return buildAgeRangeExpression(fields, new BuildContext(null, null));
    }

    /**
     * Maps a unit-of-time string to a CQL age function. When {@code bindToMeasurementPeriod}
     * is true (eCQM context), returns the period-bound form {@code AgeInYearsAt(end of "Measurement Period")}
     * so age is computed at a reproducible point. When false (CDS / authoring), returns the
     * plain {@code AgeInYears()} form which uses the system clock.
     */
    public String mapUnitToAgeFunction(String unit, boolean bindToMeasurementPeriod) {
        String base;
        if (unit == null) {
            base = "Years";
        } else {
            switch (unit.toLowerCase()) {
                case "year": case "years":   base = "Years"; break;
                case "month": case "months": base = "Months"; break;
                case "week": case "weeks":   base = "Weeks"; break;
                case "day": case "days":     base = "Days"; break;
                case "hour": case "hours":   base = "Hours"; break;
                default:                     base = "Years"; break;
            }
        }
        if (bindToMeasurementPeriod) {
            return "AgeIn" + base + "At(end of \"Measurement Period\")";
        }
        return "AgeIn" + base + "()";
    }

    /**
     * Backward-compat overload — defaults to the non-period-bound form. Existing callers
     * that don't know about {@code BuildContext} continue to work; new code should use
     * the two-arg form to get correct eCQM semantics.
     */
    @Deprecated(forRemoval = true)
    public String mapUnitToAgeFunction(String unit) {
        return mapUnitToAgeFunction(unit, false);
    }

    public String buildGenderExpression(List<Map<String, Object>> fields) {
        String gender = getFieldValue(fields, "gender", null);
        String normalizedGender = (gender != null && !gender.isEmpty()) ? gender.toLowerCase() : "";
        return templateEngine.render("elements/Gender.ftl", Map.of("gender", escapeCqlString(normalizedGender)));
    }

    @SuppressWarnings("unchecked")
    public String buildGenericResourceExpression(String type, List<Map<String, Object>> fields) {
        String resourceType = type.replace("Generic", "").replace("_vsac", "");

        if (fields == null)
            return renderElement("GenericResource.ftl", Map.of("resourceType", resourceType, "queryParts", List.of()));

        for (Map<String, Object> field : fields) {
            // Skip metadata fields (element_name, comment) — they don't carry resource data
            String fieldId = getStr(field, "id", "");
            if ("element_name".equals(fieldId) || "comment".equals(fieldId)) {
                continue;
            }

            List<Map<String, Object>> vsRefs = (List<Map<String, Object>>) field.get("valueSets");
            List<Map<String, Object>> codeRefs = (List<Map<String, Object>>) field.get("codes");

            List<String> queryParts = new ArrayList<>();

            if (vsRefs != null && !vsRefs.isEmpty()) {
                for (Map<String, Object> vs : vsRefs) {
                    String vsName = getStr(vs, "name", null);
                    if (vsName != null) {
                        queryParts.add(String.format("[%s: \"%s\"]", resourceType, escapeCqlIdentifier(vsName)));
                    }
                }
            }

            if (codeRefs != null && !codeRefs.isEmpty()) {
                for (Map<String, Object> code : codeRefs) {
                    String codeVal = getStr(code, "code", null);
                    String display = getStr(code, "display", codeVal);
                    if (codeVal != null) {
                        queryParts.add(String.format("[%s: \"%s\"]", resourceType, escapeCqlIdentifier(display != null ? display : codeVal)));
                    }
                }
            }

            if (!queryParts.isEmpty()) {
                return renderElement("GenericResource.ftl", Map.of("resourceType", resourceType, "queryParts", queryParts));
            }

            Object value = field.get("value");
            if (value instanceof Map) {
                Map<String, Object> vsVal = (Map<String, Object>) value;
                String vsName = (String) vsVal.get("name");
                if (vsName != null) {
                    queryParts.add(String.format("[%s: \"%s\"]", resourceType, escapeCqlIdentifier(vsName)));
                    return renderElement("GenericResource.ftl", Map.of("resourceType", resourceType, "queryParts", queryParts));
                }
            } else if (value instanceof String && !((String) value).isEmpty()) {
                queryParts.add(String.format("[%s: \"%s\"]", resourceType, escapeCqlIdentifier((String) value)));
                return renderElement("GenericResource.ftl", Map.of("resourceType", resourceType, "queryParts", queryParts));
            }
        }

        return renderElement("GenericResource.ftl", Map.of("resourceType", resourceType, "queryParts", List.of()));
    }

    // ── Modifier application ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    /**
     * Functional interface for one modifier kind's CQL-fragment generator.
     * Every entry of {@link #modifierAppliers} matches this shape; the kind is
     * dispatched by {@code cqlTemplate} string.
     */
    @FunctionalInterface
    private interface ModifierApplier {
        String apply(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx);
    }

    /**
     * Registry of {@code cqlTemplate} → {@link ModifierApplier}. Replaces the
     * earlier 224-line switch in {@code applyModifier} which made adding a new
     * modifier kind a copy-paste-into-a-wall ritual. Each case is now a small
     * named helper that's individually testable.
     */
    private final Map<String, ModifierApplier> modifierAppliers = buildModifierAppliers();

    private Map<String, ModifierApplier> buildModifierAppliers() {
        Map<String, ModifierApplier> m = new HashMap<>();

        // Trivial wrappers — only need expression
        ModifierApplier checkExistence = (expr, v, fn, id, ctx) ->
                renderModifier("CheckExistence.ftl", Map.of("expression", expr));
        m.put("CheckExistence", checkExistence);
        m.put("BooleanExists", checkExistence);
        m.put("BooleanNot", (expr, v, fn, id, ctx) ->
                renderModifier("BooleanNot.ftl", Map.of("expression", expr)));
        m.put("Count", (expr, v, fn, id, ctx) ->
                renderModifier("Count.ftl", Map.of("expression", expr)));
        m.put("AllTrue", (expr, v, fn, id, ctx) ->
                renderModifier("AllTrue.ftl", Map.of("expression", expr)));
        m.put("AnyTrue", (expr, v, fn, id, ctx) ->
                renderModifier("AnyTrue.ftl", Map.of("expression", expr)));
        m.put("IsTrue", (expr, v, fn, id, ctx) ->
                renderModifier("IsTrue.ftl", Map.of("expression", expr)));
        m.put("IsNotTrue", (expr, v, fn, id, ctx) ->
                renderModifier("IsNotTrue.ftl", Map.of("expression", expr)));
        m.put("IsFalse", (expr, v, fn, id, ctx) ->
                renderModifier("IsFalse.ftl", Map.of("expression", expr)));
        m.put("IsNotFalse", (expr, v, fn, id, ctx) ->
                renderModifier("IsNotFalse.ftl", Map.of("expression", expr)));

        // Field-based — delegated to named instance methods
        m.put("BooleanComparison", this::applyBooleanComparison);
        m.put("ValueComparisonNumber", this::applyValueComparison);
        m.put("ValueComparisonObservation", this::applyValueComparison);
        m.put("ConvertUnits", this::applyConvertUnits);
        m.put("WithUnit", this::applyWithUnit);
        m.put("LookBackModifier", this::applyLookBack);
        m.put("DuringMeasurementPeriod", this::applyDuringMeasurementPeriod);
        m.put("EqualsString", (expr, v, fn, id, ctx) -> applyStringMatch(expr, v, "EqualsString.ftl"));
        m.put("StartsWithString", (expr, v, fn, id, ctx) -> applyStringMatch(expr, v, "StartsWithString.ftl"));
        m.put("EndsWithString", (expr, v, fn, id, ctx) -> applyStringMatch(expr, v, "EndsWithString.ftl"));
        m.put("BeforeTimePrecise", (expr, v, fn, id, ctx) -> applyTimeBound(expr, v, "BeforeTime.ftl"));
        m.put("BeforeDateTimePrecise", (expr, v, fn, id, ctx) -> applyTimeBound(expr, v, "BeforeTime.ftl"));
        m.put("AfterTimePrecise", (expr, v, fn, id, ctx) -> applyTimeBound(expr, v, "AfterTime.ftl"));
        m.put("AfterDateTimePrecise", (expr, v, fn, id, ctx) -> applyTimeBound(expr, v, "AfterTime.ftl"));
        m.put("ContainsInteger", (expr, v, fn, id, ctx) -> applyContainsLiteral(expr, v, false));
        m.put("ContainsDecimal", (expr, v, fn, id, ctx) -> applyContainsLiteral(expr, v, false));
        m.put("ContainsQuantity", (expr, v, fn, id, ctx) -> applyContainsLiteral(expr, v, true));
        m.put("ContainsDateTime", this::applyContainsDateTime);
        m.put("BeforeInterval", (expr, v, fn, id, ctx) -> applyIntervalBound(expr, v, "BeforeTime.ftl"));
        m.put("AfterInterval", (expr, v, fn, id, ctx) -> applyIntervalBound(expr, v, "AfterTime.ftl"));
        m.put("Qualifier", this::applyQualifier);

        return m;
    }

    public String applyModifier(String expr, Map<String, Object> modifier, BuildContext ctx) {
        String cqlLibFunc = getStr(modifier, "cqlLibraryFunction", null);
        String cqlTemplate = getStr(modifier, "cqlTemplate", "");
        String modId = getStr(modifier, "id", "");
        Map<String, Object> values = (Map<String, Object>) modifier.get("values");

        // Custom modifiers (built via the UI's rule builder) are rebuilt server-side from
        // values.rules. The client-supplied cqlTemplate string is ignored — it was the CQL
        // injection sink for modifiers that bypassed the named-modifier registry.
        if (modId != null && modId.startsWith("custom_")) {
            try {
                return customModifierCqlBuilder.build(expr, values);
            } catch (CustomModifierBuildException ex) {
                ctx.warn("Custom modifier '" + modId + "' rebuild failed: " + ex.getMessage() + "; modifier skipped");
                log.warn("Custom modifier '{}' rebuild failed: {}", modId, ex.getMessage());
                return expr;
            }
        }

        ModifierApplier applier = modifierAppliers.get(cqlTemplate);
        if (applier != null) {
            return applier.apply(expr, values, cqlLibFunc, modId, ctx);
        }

        // Generic fallbacks for modifiers not in the registry.
        if (cqlTemplate != null && cqlTemplate.contains("{expression}")) {
            return cqlTemplate.replace("{expression}", expr);
        }
        if (cqlLibFunc != null) {
            return renderModifier("BaseModifier.ftl",
                    Map.of("expression", expr, "cqlLibraryFunction", cqlLibFunc));
        }

        ctx.warn(String.format("Unknown modifier template '%s' (id='%s'); modifier skipped", cqlTemplate, modId));
        log.warn("Unknown modifier template '{}' (id='{}')", cqlTemplate, modId);
        return expr;
    }

    // ------------------------------------------------------------------
    // Modifier appliers — one private method per "kind".
    // ------------------------------------------------------------------

    private String applyBooleanComparison(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx) {
        if (values == null) return expr;
        String comp = getStr(values, "value", "is not null");
        return renderModifier("BooleanComparison.ftl", Map.of("expression", expr, "value", comp));
    }

    private String applyValueComparison(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx) {
        if (values == null) return expr;
        String minOp = getStr(values, "minOperator", null);
        String minVal = getStr(values, "minValue", null);
        String maxOp = getStr(values, "maxOperator", null);
        String maxVal = getStr(values, "maxValue", null);
        String unit = getStr(values, "unit", null);

        List<String> conditions = new ArrayList<>();
        if (minOp != null && minVal != null && !minVal.isEmpty()) {
            String valExpr = unit != null && !unit.isEmpty()
                    ? String.format("%s '%s'", minVal, escapeCqlString(unit))
                    : minVal;
            conditions.add(String.format("(%s) %s %s", expr, minOp, valExpr));
        }
        if (maxOp != null && maxVal != null && !maxVal.isEmpty()) {
            String valExpr = unit != null && !unit.isEmpty()
                    ? String.format("%s '%s'", maxVal, escapeCqlString(unit))
                    : maxVal;
            conditions.add(String.format("(%s) %s %s", expr, maxOp, valExpr));
        }
        if (conditions.isEmpty()) return expr;
        String joined = String.join(" and ", conditions);
        return conditions.size() > 1 ? "(" + joined + ")" : joined;
    }

    private String applyConvertUnits(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx) {
        if (values == null) return expr;
        String unit = getStr(values, "unit", "");
        if (unit.isEmpty()) return expr;
        return renderModifier("ConvertUnits.ftl",
                Map.of("expression", expr, "unit", escapeCqlString(unit)));
    }

    private String applyWithUnit(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx) {
        if (cqlLibFunc == null) return expr;
        String unit = values == null ? "" : getStr(values, "unit", "");
        return renderModifier("WithUnit.ftl", Map.of(
                "expression", expr,
                "cqlLibraryFunction", cqlLibFunc,
                "unit", unit.isEmpty() ? "" : escapeCqlString(unit)));
    }

    private String applyLookBack(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx) {
        if (cqlLibFunc == null) return expr;
        String val = values == null ? "" : getStr(values, "value", "");
        String unit = values == null ? "years" : getStr(values, "unit", "years");
        if (val.isEmpty()) {
            return renderModifier("LookBackModifier.ftl", Map.of(
                    "expression", expr, "cqlLibraryFunction", cqlLibFunc,
                    "value", "", "unit", ""));
        }
        return renderModifier("LookBackModifier.ftl", Map.of(
                "expression", expr, "cqlLibraryFunction", cqlLibFunc,
                "value", val, "unit", escapeCqlString(unit)));
    }

    private String applyDuringMeasurementPeriod(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx) {
        // The catalog entry's nested `during` block carries alias + dateFieldSpec.
        // The saved artifact tree only keeps the modifier id — we look up the typed
        // config here and let the engine generate the null-safe case CQL.
        var def = modifierService.getById(modId);
        var cfg = def == null ? null : def.getDuring();
        if (cfg == null || cfg.getAlias() == null || cfg.getDateFieldSpec() == null) {
            ctx.warn("DuringMeasurementPeriod modifier '" + modId + "' missing during.{alias,dateFieldSpec} in catalog");
            return expr;
        }
        String whereClause = buildDuringMeasurementPeriodWhereClause(
                cfg.getAlias(), cfg.getDateFieldSpec());
        return renderModifier("DuringMeasurementPeriod.ftl", Map.of(
                "expression", expr,
                "alias", cfg.getAlias(),
                "whereClause", whereClause));
    }

    /** Shared helper for EqualsString / StartsWithString / EndsWithString — they only differ in template name. */
    private String applyStringMatch(String expr, Map<String, Object> values, String templateFile) {
        if (values == null) return expr;
        String val = getStr(values, "value", "");
        return renderModifier(templateFile, Map.of("expression", expr, "value", escapeCqlString(val)));
    }

    /** Shared helper for the four BeforeTime / AfterTime / Precise variants. */
    private String applyTimeBound(String expr, Map<String, Object> values, String templateFile) {
        if (values == null) return expr;
        String val = getStr(values, "value", "");
        return renderModifier(templateFile, Map.of("expression", expr, "value", formatDateTimeValue(val)));
    }

    /** Shared helper for ContainsInteger / ContainsDecimal / ContainsQuantity. */
    private String applyContainsLiteral(String expr, Map<String, Object> values, boolean withUnit) {
        if (values == null) return expr;
        String val = getStr(values, "value", "");
        String unit = withUnit ? escapeCqlString(getStr(values, "unit", "")) : "";
        return renderModifier("ContainsValue.ftl",
                Map.of("expression", expr, "value", val, "unit", unit));
    }

    private String applyContainsDateTime(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx) {
        if (values == null) return expr;
        String val = getStr(values, "value", "");
        return renderModifier("ContainsValue.ftl",
                Map.of("expression", expr, "value", formatDateTimeValue(val), "unit", ""));
    }

    /** Shared helper for BeforeInterval / AfterInterval — value is an interval expression, not formatted. */
    private String applyIntervalBound(String expr, Map<String, Object> values, String templateFile) {
        if (values == null) return expr;
        String val = getStr(values, "value", "");
        if (val.isEmpty()) return expr;
        return renderModifier(templateFile, Map.of("expression", expr, "value", val));
    }

    private String applyQualifier(String expr, Map<String, Object> values, String cqlLibFunc, String modId, BuildContext ctx) {
        if (values == null) return expr;
        String qualifier = getStr(values, "qualifier", "value set");
        String valueSet = getStr(values, "valueSet", null);
        String code = getStr(values, "code", null);
        Map<String, Object> model = new HashMap<>();
        model.put("expression", expr);
        model.put("qualifier", qualifier);
        model.put("valueSet", valueSet != null ? valueSet : "");
        model.put("code", code != null ? code : "");
        return renderModifier("Qualifier.ftl", model);
    }

    private String renderModifier(String templateFile, Map<String, Object> model) {
        return templateEngine.render("modifiers/" + templateFile, model);
    }

    private String renderElement(String templateFile, Map<String, Object> model) {
        return templateEngine.render("elements/" + templateFile, model);
    }

    /**
     * Generate a null-safe {@code case when ... end} CQL expression that checks whether
     * a resource's date field overlaps/lies within "Measurement Period".
     *
     * <p>Output structure (for choice types):
     * <pre>{@code
     * (case
     *   when A.field is FHIR.Period then FHIRHelpers.ToInterval(A.field as FHIR.Period) overlaps "Measurement Period"
     *   when A.field is FHIR.dateTime then FHIRHelpers.ToDateTime(A.field as FHIR.dateTime) in "Measurement Period"
     *   when A.field is FHIR.instant then FHIRHelpers.ToDateTime(A.field as FHIR.instant) in "Measurement Period"
     *   when A.fallback is not null then FHIRHelpers.ToDateTime(A.fallback) in "Measurement Period"
     *   else false
     * end)
     * }</pre>
     *
     * <p>For single-type (non-choice) fields, emits a simpler guard:
     * <pre>{@code (case when A.field is null then false else <cmp> end)}</pre>
     *
     * <p><b>Invariant:</b> every branch that invokes {@code FHIRHelpers.ToInterval(x)} or
     * {@code FHIRHelpers.ToDateTime(x)} is gated behind a {@code when ... is T then}
     * check, so {@code x} is never null when dispatched. This is what kept us from
     * reintroducing BUG-110 / BUG-112 and the reason we moved from JSON whereClause
     * strings to this structured generator.
     */
    String buildDuringMeasurementPeriodWhereClause(
            String alias,
            com.cqlplatform.model.authoring.ModifierDefinition.DateFieldSpec spec) {
        if (alias == null || spec == null || spec.getField() == null
                || spec.getTypes() == null || spec.getTypes().isEmpty()) {
            return "false";  // defensive — catalog misconfiguration
        }

        // Single, non-choice primary field: compact form, no case-choice needed.
        boolean isSingleType = spec.getTypes().size() == 1
                && (spec.getFallbacks() == null || spec.getFallbacks().isEmpty());
        if (isSingleType) {
            String cmp = buildTypeComparison(alias + "." + spec.getField(), spec.getTypes().get(0));
            return "(case when " + alias + "." + spec.getField() + " is null then false else " + cmp + " end)";
        }

        // Choice type / with fallbacks: emit one `when ... is TYPE then ...` per branch.
        StringBuilder sb = new StringBuilder("(case ");
        String fieldPath = alias + "." + spec.getField();
        for (String type : spec.getTypes()) {
            String castedRef = fieldPath + " as FHIR." + type;
            sb.append("when ").append(fieldPath).append(" is FHIR.").append(type)
                    .append(" then ").append(buildPeriodCheck(castedRef, type)).append(" ");
        }
        if (spec.getFallbacks() != null) {
            for (var fb : spec.getFallbacks()) {
                String fbPath = alias + "." + fb.getField();
                // Fallbacks are used when the primary field is absent. Guard with is-not-null.
                sb.append("when ").append(fbPath).append(" is not null then ")
                        .append(buildPeriodCheck(fbPath, fb.getType())).append(" ");
            }
        }
        sb.append("else false end)");
        return sb.toString();
    }

    /** Direct comparison with Measurement Period for a FHIR value of a specific type. */
    private static String buildPeriodCheck(String valueRef, String fhirType) {
        switch (fhirType) {
            case "Period":
                return "FHIRHelpers.ToInterval(" + valueRef + ") overlaps \"Measurement Period\"";
            case "dateTime":
            case "instant":
                return "FHIRHelpers.ToDateTime(" + valueRef + ") in \"Measurement Period\"";
            default:
                // Unknown type — treat as no-match rather than generate broken CQL.
                return "false";
        }
    }

    /** Non-choice field: no cast needed, just the comparison. */
    private static String buildTypeComparison(String valueRef, String fhirType) {
        switch (fhirType) {
            case "Period":
                return "FHIRHelpers.ToInterval(" + valueRef + ") overlaps \"Measurement Period\"";
            case "dateTime":
            case "instant":
                return "FHIRHelpers.ToDateTime(" + valueRef + ") in \"Measurement Period\"";
            default:
                return "false";
        }
    }

    // ── Declaration collection ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void collectDeclarations(Map<String, Object> node, Set<String> valueSets,
            Map<String, String> codeSystems, Set<String> codes, Set<String> includes) {
        if (node == null)
            return;

        String type = getStr(node, "type", "");
        if ("externalCqlRef".equals(type)) {
            List<Map<String, Object>> extFields = (List<Map<String, Object>>) node.get("fields");
            String libName = getFieldValue(extFields, "library_name", null);
            String libVersion = getFieldValue(extFields, "library_version", null);
            if (libName != null && !libName.isEmpty()) {
                String safeLibName = libName.replaceAll("[^a-zA-Z0-9_]", "_");
                String includeStmt;
                if (libVersion != null && !libVersion.isEmpty()) {
                    includeStmt = String.format("include %s version '%s' called %s",
                            safeLibName, escapeCqlString(libVersion), safeLibName);
                } else {
                    includeStmt = String.format("include %s called %s", safeLibName, safeLibName);
                }
                includes.add(includeStmt);
            }
        } else if ("externalCqlElement".equals(type)) {
            List<Map<String, Object>> extFields = (List<Map<String, Object>>) node.get("fields");
            String libName = getFieldValue(extFields, "libraryName", null);
            String libVersion = getFieldValue(extFields, "libraryVersion", null);
            String alias = getFieldValue(extFields, "alias", null);
            if (libName != null && !libName.isEmpty()) {
                String safeLibName = libName.replaceAll("[^a-zA-Z0-9_]", "_");
                String called = (alias != null && !alias.isEmpty()) ? alias : safeLibName;
                String includeStmt;
                if (libVersion != null && !libVersion.isEmpty()) {
                    includeStmt = String.format("include %s version '%s' called %s",
                            safeLibName, escapeCqlString(libVersion), called);
                } else {
                    includeStmt = String.format("include %s called %s", safeLibName, called);
                }
                includes.add(includeStmt);
            }
        }

        List<Map<String, Object>> fields = (List<Map<String, Object>>) node.get("fields");
        if (fields != null) {
            for (Map<String, Object> field : fields) {
                Object value = field.get("value");
                if (value instanceof Map) {
                    Map<String, Object> vsVal = (Map<String, Object>) value;
                    String vsName = (String) vsVal.get("name");
                    if (vsName != null)
                        valueSets.add(vsName);
                }

                List<Map<String, Object>> vsRefs = (List<Map<String, Object>>) field.get("valueSets");
                if (vsRefs != null) {
                    for (Map<String, Object> vs : vsRefs) {
                        String vsName = (String) vs.get("name");
                        if (vsName != null)
                            valueSets.add(vsName);
                    }
                }

                List<Map<String, Object>> codeRefs = (List<Map<String, Object>>) field.get("codes");
                if (codeRefs != null) {
                    for (Map<String, Object> code : codeRefs) {
                        String codeVal = getStr(code, "code", null);
                        String display = getStr(code, "display", codeVal);
                        Map<String, Object> codeSystem = (Map<String, Object>) code.get("codeSystem");
                        if (codeVal != null && codeSystem != null) {
                            String csId = getStr(codeSystem, "id", "");
                            String csName = getStr(codeSystem, "name", "");
                            String csDisplayName = !csName.isEmpty() ? csName : getCodeSystemDisplayName(csId);
                            if (!csId.isEmpty()) {
                                codeSystems.put(csId, csDisplayName);
                            }
                            codes.add(String.format("code \"%s\": '%s' from \"%s\"",
                                    escapeCqlIdentifier(display != null ? display : codeVal),
                                    escapeCqlString(codeVal),
                                    escapeCqlIdentifier(csDisplayName)));
                        }
                    }
                }
            }
        }

        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("childInstances");
        if (children != null) {
            for (Map<String, Object> child : children) {
                collectDeclarations(child, valueSets, codeSystems, codes, includes);
            }
        }
    }

    // ── Emit helpers (for CQL header generation) ─────────────────────────

    public void emitValueSets(StringBuilder cql, Set<String> valueSets) {
        if (!valueSets.isEmpty()) {
            for (String vs : valueSets) {
                cql.append(String.format("valueset \"%s\": '%s'%n", escapeCqlIdentifier(vs), escapeCqlString(vs)));
            }
            cql.append("\n");
        }
    }

    public void emitCodeSystems(StringBuilder cql, Map<String, String> codeSystems) {
        if (!codeSystems.isEmpty()) {
            for (var entry : codeSystems.entrySet()) {
                String uri = entry.getKey();
                String displayName = entry.getValue();
                cql.append(String.format("codesystem \"%s\": '%s'%n",
                        escapeCqlIdentifier(displayName), escapeCqlString(uri)));
            }
            cql.append("\n");
        }
    }

    public void emitCodes(StringBuilder cql, Set<String> codes) {
        if (!codes.isEmpty()) {
            for (String codeDecl : codes) {
                cql.append(codeDecl).append("\n");
            }
            cql.append("\n");
        }
    }

    public void emitIncludes(StringBuilder cql, Set<String> includes) {
        for (String inc : includes) {
            cql.append(inc).append("\n");
        }
        cql.append("\n");
    }

    // ── Parameter formatting ─────────────────────────────────────────────

    public String mapParameterType(String type) {
        if (type == null)
            return "Boolean";
        switch (type.toLowerCase()) {
            case "boolean":
                return "Boolean";
            case "integer":
                return "Integer";
            case "decimal":
                return "Decimal";
            case "string":
                return "String";
            case "datetime":
                return "DateTime";
            case "time":
                return "Time";
            case "code":
                return "Code";
            case "concept":
                return "Concept";
            case "quantity":
                return "Quantity";
            case "interval<integer>":
                return "Interval<Integer>";
            case "interval<datetime>":
                return "Interval<DateTime>";
            default:
                return type;
        }
    }

    @SuppressWarnings("unchecked")
    public String formatParameterDefault(String type, Object value) {
        if (value == null)
            return null;
        if (type == null)
            return value.toString();

        Map<String, Object> model = new HashMap<>();
        String normalizedType = type.toLowerCase();

        switch (normalizedType) {
            case "boolean":
                model.put("type", "boolean");
                model.put("value", value.toString().toLowerCase());
                break;
            case "integer":
            case "decimal":
                model.put("type", normalizedType);
                model.put("value", value.toString());
                break;
            case "string":
                model.put("type", "string");
                model.put("value", value.toString().replace("'", "\\'"));
                break;
            case "datetime": {
                String dtVal = value.toString();
                if (!dtVal.startsWith("@")) dtVal = "@" + dtVal;
                if (!dtVal.contains("T")) dtVal += "T00:00:00";
                model.put("type", "datetime");
                model.put("value", dtVal);
                break;
            }
            case "time": {
                String tVal = value.toString();
                if (!tVal.startsWith("@T")) {
                    tVal = tVal.startsWith("@") ? tVal.replace("@", "@T") : "@T" + tVal;
                }
                model.put("type", "time");
                model.put("value", tVal);
                break;
            }
            case "code":
                if (value instanceof Map) {
                    Map<String, Object> codeMap = (Map<String, Object>) value;
                    String code = getStr(codeMap, "code", "");
                    String system = getStr(codeMap, "system", "");
                    if (!code.isEmpty() && !system.isEmpty()) {
                        model.put("type", "code");
                        model.put("code", code);
                        model.put("csName", getCodeSystemDisplayName(system));
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
                break;
            case "concept":
                if (value instanceof Map) {
                    Map<String, Object> conceptMap = (Map<String, Object>) value;
                    String code = getStr(conceptMap, "code", "");
                    String system = getStr(conceptMap, "system", "");
                    String display = getStr(conceptMap, "display", "");
                    if (!code.isEmpty() && !system.isEmpty()) {
                        model.put("type", "concept");
                        model.put("code", code);
                        model.put("csName", getCodeSystemDisplayName(system));
                        model.put("display", display);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
                break;
            case "quantity":
                if (value instanceof Map) {
                    Map<String, Object> qtyMap = (Map<String, Object>) value;
                    String qtyValue = getStr(qtyMap, "value", "");
                    String unit = getStr(qtyMap, "unit", "");
                    if (!qtyValue.isEmpty()) {
                        model.put("type", "quantity");
                        model.put("qtyValue", qtyValue);
                        model.put("unit", unit);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
                break;
            case "interval<integer>":
                if (value instanceof Map) {
                    Map<String, Object> ivl = (Map<String, Object>) value;
                    model.put("type", "interval_integer");
                    model.put("low", getStr(ivl, "low", "null"));
                    model.put("high", getStr(ivl, "high", "null"));
                } else {
                    model.put("type", "default");
                    model.put("value", value.toString());
                }
                break;
            case "interval<datetime>":
                if (value instanceof Map) {
                    Map<String, Object> ivl = (Map<String, Object>) value;
                    String low = getStr(ivl, "low", "");
                    String high = getStr(ivl, "high", "");
                    model.put("type", "interval_datetime");
                    model.put("low", !low.isEmpty() ? formatDateTimeValue(low) : "null");
                    model.put("high", !high.isEmpty() ? formatDateTimeValue(high) : "null");
                } else {
                    model.put("type", "default");
                    model.put("value", value.toString());
                }
                break;
            default:
                model.put("type", "default");
                model.put("value", value.toString());
                break;
        }

        return templateEngine.render("parameters/defaults.ftl", model).strip();
    }

    // ── Utility methods ──────────────────────────────────────────────────

    public String formatDateTimeValue(String value) {
        if (value == null || value.isEmpty())
            return "null";
        if (value.contains("T")) {
            return "@" + value;
        }
        return "@" + value;
    }

    public String escapeCqlString(String value) {
        return com.cqlplatform.util.CqlEscapeUtil.escapeCqlString(value);
    }

    /**
     * Escape a value for use inside CQL quoted identifiers (delimited by double quotes).
     * In CQL, a quoted identifier uses {@code "name"} syntax; an embedded double-quote
     * must be escaped as {@code \"}. Non-ASCII characters (e.g. Chinese) are stripped
     * to avoid CQL engine compatibility issues.
     */
    public String escapeCqlIdentifier(String value) {
        return com.cqlplatform.util.CqlEscapeUtil.escapeCqlIdentifier(value);
    }

    public String getCodeSystemDisplayName(String systemUrl) {
        return AuthoringConstants.getCodeSystemDisplayName(systemUrl);
    }

    /**
     * Classify a modifier's effect on its list input:
     * <ul>
     *   <li>{@code "preserves-list"} — list → list (default; most modifiers)</li>
     *   <li>{@code "collapses-list"} — list → single resource (MostRecent, First, Last)</li>
     *   <li>{@code "extracts-value"} — list or resource → scalar/Quantity/Concept (QuantityValue, AverageObservation, etc.)</li>
     * </ul>
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Explicit {@code listBehavior} on the modifier instance (future escape hatch)</li>
     *   <li>Inferred from {@code returnType}:
     *     {@code list_of_*} → preserves-list,
     *     {@code system_*} → extracts-value,
     *     anything else (single-resource type) → collapses-list</li>
     * </ol>
     *
     * <p>This replaces the earlier heuristic that matched substrings in
     * {@code cqlLibraryFunction}. The heuristic silently misclassified
     * {@code C3F.AverageObservation} (no matching substring) — it was applied in Measure
     * Population despite returning a scalar, corrupting the Measure Observation wrapper
     * for any CV measure that used it.
     */
    String classifyListBehavior(Map<String, Object> modifier) {
        String explicit = getStr(modifier, "listBehavior", null);
        if (explicit != null) return explicit;
        String rt = getStr(modifier, "returnType", "");
        if (rt.startsWith("list_of_")) return "preserves-list";
        if (rt.startsWith("system_")) return "extracts-value";
        if (rt.isEmpty()) return "preserves-list";  // unknown → safest default
        return "collapses-list";  // single resource type (observation/condition/procedure/...)
    }

    /**
     * Returns true if the modifier should be skipped in the CV Measure Population path
     * (where renderMode = CV_MEASURE_POPULATION). Both {@code collapses-list} and {@code extracts-value}
     * strip the list shape the Measure Observation wrapper needs to iterate over.
     */
    private boolean isListCollapsingOrValueExtractingModifier(Map<String, Object> modifier) {
        String behavior = classifyListBehavior(modifier);
        return "collapses-list".equals(behavior) || "extracts-value".equals(behavior);
    }

    public String getFinalReturnType(Map<String, Object> element, List<Map<String, Object>> modifiers) {
        if (modifiers != null && !modifiers.isEmpty()) {
            Map<String, Object> lastMod = modifiers.get(modifiers.size() - 1);
            String rt = getStr(lastMod, "returnType", null);
            if (rt != null) return rt;
        }
        return getStr(element, "returnType", null);
    }

    public String getStr(Map<String, Object> map, String key, String defaultVal) {
        if (map == null)
            return defaultVal;
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    public String getFieldValue(List<Map<String, Object>> fields, String fieldId, String defaultVal) {
        if (fields == null)
            return defaultVal;
        for (Map<String, Object> field : fields) {
            if (fieldId.equals(field.get("id"))) {
                Object val = field.get("value");
                return val != null ? val.toString() : defaultVal;
            }
        }
        return defaultVal;
    }

    /**
     * PAT-163: like {@link #getFieldValue} but returns the raw Object value
     * instead of stringifying. Used for N-ary arithmetic where {@code operands}
     * and {@code operators} fields hold List/Array values rather than strings.
     */
    public Object getFieldRawValue(List<Map<String, Object>> fields, String fieldId) {
        if (fields == null) return null;
        for (Map<String, Object> field : fields) {
            if (fieldId.equals(field.get("id"))) {
                return field.get("value");
            }
        }
        return null;
    }

    public String resolveFhirVersion(String fhirVersion) {
        return FHIR_VERSION_MAP.getOrDefault(fhirVersion, AuthoringConstants.DEFAULT_FHIR_VERSION);
    }

    public String resolveHelpersVersion(String fhirVersion) {
        return FHIR_HELPERS_VERSION_MAP.getOrDefault(fhirVersion, AuthoringConstants.DEFAULT_FHIR_VERSION);
    }
}
