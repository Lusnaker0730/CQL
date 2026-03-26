package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.AuthoringConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Shared engine for converting expression trees into CQL fragments.
 * Used by both CDS CqlArtifactBuilder and eCQM EcqmCqlBuilder.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExpressionCqlEngine {

    private final CqlTemplateEngine templateEngine;

    private static final Map<String, String> FHIR_VERSION_MAP = AuthoringConstants.FHIR_VERSION_MAP;
    private static final Map<String, String> FHIR_HELPERS_VERSION_MAP = AuthoringConstants.FHIR_HELPERS_VERSION_MAP;

    /**
     * Per-build context holding base elements for cross-reference lookups
     * and a warnings collector. Created fresh for each build invocation
     * to ensure thread safety.
     */
    public static class BuildContext {
        public final List<Map<String, Object>> baseElements;
        public final Map<String, String> baseElementNameIndex;
        public final Map<String, String> parameterNameIndex;
        public final List<String> warnings = new ArrayList<>();
        /** When true, list-returning expressions keep their list type instead of being wrapped in exists(). */
        public boolean preserveListReturn = false;
        /** The resource type to preserve as a list (e.g. "Encounter") when preserveListReturn is true. */
        public String episodeResourceType = null;

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
        if (ctx.preserveListReturn && ctx.episodeResourceType != null
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

        // Temporarily disable preserveListReturn for filter expressions
        ctx.preserveListReturn = false;

        for (Map<String, Object> child : children) {
            Boolean conjunction = (Boolean) child.get("conjunction");
            if (Boolean.TRUE.equals(conjunction)) {
                filterExprs.add("(" + buildConjunctionExpression(child, ctx) + ")");
                continue;
            }

            String childType = getStr(child, "type", "").toLowerCase();
            if (baseExpr == null && childType.contains(episodeType)) {
                // This is the episode resource — build without exists()
                ctx.preserveListReturn = true;
                baseExpr = buildExpression(child, ctx);
                ctx.preserveListReturn = false;
            } else {
                filterExprs.add(buildExpression(child, ctx));
            }
        }

        // Restore flag
        ctx.preserveListReturn = true;

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
                expr = buildAgeRangeExpression(fields);
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
            case "arithmeticExpression": {
                String operator = getFieldValue(fields, "operator", "+");
                // Validate operator to prevent injection
                if (!"+".equals(operator) && !"-".equals(operator)
                        && !"*".equals(operator) && !"/".equals(operator)) {
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
                expr = applyModifier(expr, mod, ctx);
            }
        }

        String finalReturnType = getFinalReturnType(element, modifiers);
        if (finalReturnType != null && finalReturnType.startsWith("list_of_")
                && !ctx.preserveListReturn) {
            expr = String.format("exists(%s)", expr);
        }

        return expr;
    }

    /**
     * Resolve one side (left or right) of an arithmetic expression.
     * Supports element references and literal numeric values.
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
        // Element reference mode
        String refId = getFieldValue(fields, side + "_operand_id", "");
        if (refId == null || refId.isEmpty()) return null;
        String refName = ctx.findBaseElementName(refId);
        return refName != null ? String.format("\"%s\"", escapeCqlIdentifier(refName)) : null;
    }

    public String buildAgeRangeExpression(List<Map<String, Object>> fields) {
        String minAge = getFieldValue(fields, "min_age", null);
        String maxAge = getFieldValue(fields, "max_age", null);
        String unit = getFieldValue(fields, "unit_of_time", "year");
        String ageFunction = mapUnitToAgeFunction(unit);

        Map<String, Object> model = new HashMap<>();
        model.put("ageFunction", ageFunction);
        model.put("minAge", minAge != null && !minAge.isEmpty() ? minAge : "");
        model.put("maxAge", maxAge != null && !maxAge.isEmpty() ? maxAge : "");
        return templateEngine.render("elements/AgeRange.ftl", model);
    }

    public String mapUnitToAgeFunction(String unit) {
        if (unit == null)
            return "AgeInYears()";
        switch (unit.toLowerCase()) {
            case "year":
            case "years":
                return "AgeInYears()";
            case "month":
            case "months":
                return "AgeInMonths()";
            case "week":
            case "weeks":
                return "AgeInWeeks()";
            case "day":
            case "days":
                return "AgeInDays()";
            case "hour":
            case "hours":
                return "AgeInHours()";
            default:
                return "AgeInYears()";
        }
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
    public String applyModifier(String expr, Map<String, Object> modifier, BuildContext ctx) {
        String cqlLibFunc = getStr(modifier, "cqlLibraryFunction", null);
        String cqlTemplate = getStr(modifier, "cqlTemplate", "");
        String modId = getStr(modifier, "id", "");
        Map<String, Object> values = (Map<String, Object>) modifier.get("values");

        switch (cqlTemplate) {
            case "CheckExistence":
            case "BooleanExists":
                return renderModifier("CheckExistence.ftl", Map.of("expression", expr));
            case "BooleanNot":
                return renderModifier("BooleanNot.ftl", Map.of("expression", expr));
            case "Count":
                return renderModifier("Count.ftl", Map.of("expression", expr));
            case "AllTrue":
                return renderModifier("AllTrue.ftl", Map.of("expression", expr));
            case "AnyTrue":
                return renderModifier("AnyTrue.ftl", Map.of("expression", expr));
            case "BooleanComparison": {
                if (values != null) {
                    String comp = getStr(values, "value", "is not null");
                    return renderModifier("BooleanComparison.ftl", Map.of("expression", expr, "value", comp));
                }
                return expr;
            }
            case "ValueComparisonNumber":
            case "ValueComparisonObservation": {
                if (values != null) {
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
                    if (!conditions.isEmpty()) {
                        String joined = String.join(" and ", conditions);
                        return conditions.size() > 1 ? "(" + joined + ")" : joined;
                    }
                }
                return expr;
            }
            case "ConvertUnits": {
                if (values != null) {
                    String unit = getStr(values, "unit", "");
                    if (!unit.isEmpty()) {
                        return renderModifier("ConvertUnits.ftl", Map.of("expression", expr, "unit", escapeCqlString(unit)));
                    }
                }
                return expr;
            }
            case "WithUnit": {
                if (cqlLibFunc != null && values != null) {
                    String unit = getStr(values, "unit", "");
                    if (!unit.isEmpty()) {
                        return renderModifier("WithUnit.ftl", Map.of(
                                "expression", expr, "cqlLibraryFunction", cqlLibFunc, "unit", escapeCqlString(unit)));
                    }
                }
                if (cqlLibFunc != null)
                    return renderModifier("WithUnit.ftl", Map.of(
                            "expression", expr, "cqlLibraryFunction", cqlLibFunc, "unit", ""));
                return expr;
            }
            case "LookBackModifier": {
                if (cqlLibFunc != null && values != null) {
                    String val = getStr(values, "value", "");
                    String unit = getStr(values, "unit", "years");
                    if (!val.isEmpty()) {
                        return renderModifier("LookBackModifier.ftl", Map.of(
                                "expression", expr, "cqlLibraryFunction", cqlLibFunc, "value", val, "unit", escapeCqlString(unit)));
                    }
                }
                if (cqlLibFunc != null)
                    return renderModifier("LookBackModifier.ftl", Map.of(
                            "expression", expr, "cqlLibraryFunction", cqlLibFunc, "value", "", "unit", ""));
                return expr;
            }
            case "EqualsString": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return renderModifier("EqualsString.ftl", Map.of("expression", expr, "value", escapeCqlString(val)));
                }
                return expr;
            }
            case "StartsWithString": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return renderModifier("StartsWithString.ftl", Map.of("expression", expr, "value", escapeCqlString(val)));
                }
                return expr;
            }
            case "EndsWithString": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return renderModifier("EndsWithString.ftl", Map.of("expression", expr, "value", escapeCqlString(val)));
                }
                return expr;
            }
            case "BeforeTimePrecise":
            case "BeforeDateTimePrecise": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return renderModifier("BeforeTime.ftl", Map.of("expression", expr, "value", formatDateTimeValue(val)));
                }
                return expr;
            }
            case "AfterTimePrecise":
            case "AfterDateTimePrecise": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return renderModifier("AfterTime.ftl", Map.of("expression", expr, "value", formatDateTimeValue(val)));
                }
                return expr;
            }
            case "ContainsInteger":
            case "ContainsDecimal": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return renderModifier("ContainsValue.ftl", Map.of("expression", expr, "value", val, "unit", ""));
                }
                return expr;
            }
            case "ContainsQuantity": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    String unit = getStr(values, "unit", "");
                    return renderModifier("ContainsValue.ftl", Map.of("expression", expr, "value", val, "unit", escapeCqlString(unit)));
                }
                return expr;
            }
            case "ContainsDateTime": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return renderModifier("ContainsValue.ftl", Map.of(
                            "expression", expr, "value", formatDateTimeValue(val), "unit", ""));
                }
                return expr;
            }
            case "IsTrue":
                return renderModifier("IsTrue.ftl", Map.of("expression", expr));
            case "IsNotTrue":
                return renderModifier("IsNotTrue.ftl", Map.of("expression", expr));
            case "IsFalse":
                return renderModifier("IsFalse.ftl", Map.of("expression", expr));
            case "IsNotFalse":
                return renderModifier("IsNotFalse.ftl", Map.of("expression", expr));
            case "BeforeInterval": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    if (!val.isEmpty()) {
                        return renderModifier("BeforeTime.ftl", Map.of("expression", expr, "value", val));
                    }
                }
                return expr;
            }
            case "AfterInterval": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    if (!val.isEmpty()) {
                        return renderModifier("AfterTime.ftl", Map.of("expression", expr, "value", val));
                    }
                }
                return expr;
            }
            case "Qualifier": {
                if (values != null) {
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
                return expr;
            }
            default:
                break;
        }

        if (cqlTemplate != null && cqlTemplate.contains("{expression}")) {
            return cqlTemplate.replace("{expression}", expr);
        }

        if (cqlLibFunc != null) {
            return renderModifier("BaseModifier.ftl", Map.of("expression", expr, "cqlLibraryFunction", cqlLibFunc));
        }

        ctx.warn(String.format("Unknown modifier template '%s' (id='%s'); modifier skipped", cqlTemplate, modId));
        log.warn("Unknown modifier template '{}' (id='{}')", cqlTemplate, modId);
        return expr;
    }

    private String renderModifier(String templateFile, Map<String, Object> model) {
        return templateEngine.render("modifiers/" + templateFile, model);
    }

    private String renderElement(String templateFile, Map<String, Object> model) {
        return templateEngine.render("elements/" + templateFile, model);
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
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Escape a value for use inside CQL quoted identifiers (delimited by double quotes).
     * In CQL, a quoted identifier uses {@code "name"} syntax; an embedded double-quote
     * must be escaped as {@code \"}. Non-ASCII characters (e.g. Chinese) are stripped
     * to avoid CQL engine compatibility issues.
     */
    public String escapeCqlIdentifier(String value) {
        if (value == null) return "";
        // Strip non-ASCII characters and clean up residual whitespace/parentheses
        String ascii = value.replaceAll("[^\\x00-\\x7F]", "")
                .replaceAll("\\(\\s*\\)", "")   // remove empty parentheses left after stripping
                .replaceAll("\\s{2,}", " ")      // collapse multiple spaces
                .trim();
        return ascii.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public String getCodeSystemDisplayName(String systemUrl) {
        return AuthoringConstants.getCodeSystemDisplayName(systemUrl);
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

    public String resolveFhirVersion(String fhirVersion) {
        return FHIR_VERSION_MAP.getOrDefault(fhirVersion, AuthoringConstants.DEFAULT_FHIR_VERSION);
    }

    public String resolveHelpersVersion(String fhirVersion) {
        return FHIR_HELPERS_VERSION_MAP.getOrDefault(fhirVersion, AuthoringConstants.DEFAULT_FHIR_VERSION);
    }
}
