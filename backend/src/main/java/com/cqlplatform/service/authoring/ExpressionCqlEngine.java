package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.AuthoringConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Shared engine for converting expression trees into CQL fragments.
 * Used by both CDS CqlArtifactBuilder and eCQM EcqmCqlBuilder.
 */
@Component
@Slf4j
public class ExpressionCqlEngine {

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
        public final List<String> warnings = new ArrayList<>();

        public BuildContext(List<Map<String, Object>> baseElements) {
            this.baseElements = baseElements;
            Map<String, String> idx = new HashMap<>();
            if (baseElements != null) {
                for (Map<String, Object> be : baseElements) {
                    Object uid = be.get("uniqueId");
                    Object name = be.get("name");
                    if (uid != null && name != null) {
                        idx.put(uid.toString(), name.toString());
                    }
                }
            }
            this.baseElementNameIndex = idx;
        }

        public void warn(String message) {
            warnings.add(message);
        }

        public String findBaseElementName(String uniqueId) {
            if (uniqueId == null) return null;
            return baseElementNameIndex.get(uniqueId);
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

        String conjId = getStr(group, "id", "And");
        String operator = "Or".equals(conjId) ? " or " : " and ";

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
                expr = String.format("\"%s\"", refName != null ? refName : elementName);
                break;
            }
            case "parameterRef": {
                String paramName = elementName;
                expr = String.format("\"%s\"", paramName);
                break;
            }
            case "externalCqlRef": {
                String libName = getFieldValue(fields, "library_name", "");
                String refId = getFieldValue(fields, "reference_id", "");
                String defName = refId.contains(":") ? refId.substring(refId.indexOf(':') + 1) : elementName;
                if (libName != null && !libName.isEmpty()) {
                    expr = String.format("\"%s\".\"%s\"", libName, defName);
                } else {
                    expr = String.format("\"%s\"", defName);
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
        if (finalReturnType != null && finalReturnType.startsWith("list_of_")) {
            expr = String.format("exists(%s)", expr);
        }

        return expr;
    }

    public String buildAgeRangeExpression(List<Map<String, Object>> fields) {
        String minAge = getFieldValue(fields, "min_age", null);
        String maxAge = getFieldValue(fields, "max_age", null);
        String unit = getFieldValue(fields, "unit_of_time", "year");

        String ageFunction = mapUnitToAgeFunction(unit);

        List<String> conditions = new ArrayList<>();
        if (minAge != null && !minAge.isEmpty()) {
            conditions.add(String.format("%s >= %s", ageFunction, minAge));
        }
        if (maxAge != null && !maxAge.isEmpty()) {
            conditions.add(String.format("%s <= %s", ageFunction, maxAge));
        }

        if (conditions.isEmpty()) return "true";
        String joined = String.join(" and ", conditions);
        return conditions.size() > 1 ? "(" + joined + ")" : joined;
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
        if (gender == null || gender.isEmpty())
            return "true";
        return String.format("Patient.gender = '%s'", gender.toLowerCase());
    }

    @SuppressWarnings("unchecked")
    public String buildGenericResourceExpression(String type, List<Map<String, Object>> fields) {
        String resourceType = type.replace("Generic", "").replace("_vsac", "");

        if (fields == null)
            return String.format("[%s]", resourceType);

        for (Map<String, Object> field : fields) {
            List<Map<String, Object>> vsRefs = (List<Map<String, Object>>) field.get("valueSets");
            List<Map<String, Object>> codeRefs = (List<Map<String, Object>>) field.get("codes");

            List<String> queryParts = new ArrayList<>();

            if (vsRefs != null && !vsRefs.isEmpty()) {
                for (Map<String, Object> vs : vsRefs) {
                    String vsName = getStr(vs, "name", null);
                    if (vsName != null) {
                        queryParts.add(String.format("[%s: \"%s\"]", resourceType, vsName));
                    }
                }
            }

            if (codeRefs != null && !codeRefs.isEmpty()) {
                for (Map<String, Object> code : codeRefs) {
                    String codeVal = getStr(code, "code", null);
                    String display = getStr(code, "display", codeVal);
                    if (codeVal != null) {
                        queryParts
                                .add(String.format("[%s: \"%s\"]", resourceType, display != null ? display : codeVal));
                    }
                }
            }

            if (!queryParts.isEmpty()) {
                if (queryParts.size() == 1) {
                    return queryParts.get(0);
                }
                return String.join(" union\n  ", queryParts);
            }

            Object value = field.get("value");
            if (value instanceof Map) {
                Map<String, Object> vsVal = (Map<String, Object>) value;
                String vsName = (String) vsVal.get("name");
                if (vsName != null) {
                    return String.format("[%s: \"%s\"]", resourceType, vsName);
                }
            } else if (value instanceof String && !((String) value).isEmpty()) {
                return String.format("[%s: \"%s\"]", resourceType, value);
            }
        }

        return String.format("[%s]", resourceType);
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
                return String.format("exists(%s)", expr);
            case "BooleanNot":
                return String.format("not (%s)", expr);
            case "Count":
                return String.format("Count(%s)", expr);
            case "AllTrue":
                return String.format("AllTrue(%s)", expr);
            case "AnyTrue":
                return String.format("AnyTrue(%s)", expr);
            case "BooleanComparison": {
                if (values != null) {
                    String comp = getStr(values, "value", "is not null");
                    return String.format("(%s) %s", expr, comp);
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
                                ? String.format("%s '%s'", minVal, unit)
                                : minVal;
                        conditions.add(String.format("(%s) %s %s", expr, minOp, valExpr));
                    }
                    if (maxOp != null && maxVal != null && !maxVal.isEmpty()) {
                        String valExpr = unit != null && !unit.isEmpty()
                                ? String.format("%s '%s'", maxVal, unit)
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
            case "WithUnit": {
                if (cqlLibFunc != null && values != null) {
                    String unit = getStr(values, "unit", "");
                    if (!unit.isEmpty()) {
                        return String.format("%s(%s, '%s')", cqlLibFunc, expr, unit);
                    }
                }
                if (cqlLibFunc != null)
                    return String.format("%s(%s)", cqlLibFunc, expr);
                return expr;
            }
            case "LookBackModifier": {
                if (cqlLibFunc != null && values != null) {
                    String val = getStr(values, "value", "");
                    String unit = getStr(values, "unit", "years");
                    if (!val.isEmpty()) {
                        return String.format("%s(%s, %s %s)", cqlLibFunc, expr, val, unit);
                    }
                }
                if (cqlLibFunc != null)
                    return String.format("%s(%s)", cqlLibFunc, expr);
                return expr;
            }
            case "EqualsString": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return String.format("(%s) = '%s'", expr, val);
                }
                return expr;
            }
            case "StartsWithString": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return String.format("StartsWith(%s, '%s')", expr, val);
                }
                return expr;
            }
            case "EndsWithString": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return String.format("EndsWith(%s, '%s')", expr, val);
                }
                return expr;
            }
            case "BeforeTimePrecise":
            case "BeforeDateTimePrecise": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return String.format("(%s) before %s", expr, formatDateTimeValue(val));
                }
                return expr;
            }
            case "AfterTimePrecise":
            case "AfterDateTimePrecise": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return String.format("(%s) after %s", expr, formatDateTimeValue(val));
                }
                return expr;
            }
            case "ContainsInteger":
            case "ContainsDecimal": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return String.format("(%s) contains %s", expr, val);
                }
                return expr;
            }
            case "ContainsQuantity": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    String unit = getStr(values, "unit", "");
                    if (!unit.isEmpty()) {
                        return String.format("(%s) contains %s '%s'", expr, val, unit);
                    }
                    return String.format("(%s) contains %s", expr, val);
                }
                return expr;
            }
            case "ContainsDateTime": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    return String.format("(%s) contains %s", expr, formatDateTimeValue(val));
                }
                return expr;
            }
            case "IsTrue":
                return String.format("(%s) is true", expr);
            case "IsNotTrue":
                return String.format("(%s) is not true", expr);
            case "IsFalse":
                return String.format("(%s) is false", expr);
            case "IsNotFalse":
                return String.format("(%s) is not false", expr);
            case "BeforeInterval": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    if (!val.isEmpty()) {
                        return String.format("(%s) before %s", expr, val);
                    }
                }
                return expr;
            }
            case "AfterInterval": {
                if (values != null) {
                    String val = getStr(values, "value", "");
                    if (!val.isEmpty()) {
                        return String.format("(%s) after %s", expr, val);
                    }
                }
                return expr;
            }
            case "Qualifier": {
                if (values != null) {
                    String qualifier = getStr(values, "qualifier", "value set");
                    String valueSet = getStr(values, "valueSet", null);
                    String code = getStr(values, "code", null);
                    if ("value set".equals(qualifier) && valueSet != null && !valueSet.isEmpty()) {
                        return String.format("%s Q where Q.code in \"%s\"", expr, valueSet);
                    } else if ("code".equals(qualifier) && code != null && !code.isEmpty()) {
                        return String.format("%s Q where Q.code ~ \"%s\"", expr, code);
                    }
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
            return String.format("%s(%s)", cqlLibFunc, expr);
        }

        ctx.warn(String.format("Unknown modifier template '%s' (id='%s'); modifier skipped", cqlTemplate, modId));
        log.warn("Unknown modifier template '{}' (id='{}')", cqlTemplate, modId);
        return expr;
    }

    // ── Declaration collection ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void collectDeclarations(Map<String, Object> node, Set<String> valueSets,
            Set<String> codeSystems, Set<String> codes, Set<String> includes) {
        if (node == null)
            return;

        String type = getStr(node, "type", "");
        if ("externalCqlRef".equals(type)) {
            List<Map<String, Object>> extFields = (List<Map<String, Object>>) node.get("fields");
            String libName = getFieldValue(extFields, "library_name", null);
            String libVersion = getFieldValue(extFields, "library_version", null);
            if (libName != null && !libName.isEmpty()) {
                String includeStmt;
                if (libVersion != null && !libVersion.isEmpty()) {
                    includeStmt = String.format("include %s version '%s' called %s", libName, libVersion, libName);
                } else {
                    includeStmt = String.format("include %s called %s", libName, libName);
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
                            if (!csId.isEmpty()) {
                                codeSystems.add(csId);
                            }
                            String csDisplayName = !csName.isEmpty() ? csName : getCodeSystemDisplayName(csId);
                            codes.add(String.format("code \"%s\": '%s' from \"%s\"",
                                    display != null ? display : codeVal, codeVal, csDisplayName));
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
                cql.append(String.format("valueset \"%s\": '%s'%n", vs, vs));
            }
            cql.append("\n");
        }
    }

    public void emitCodeSystems(StringBuilder cql, Set<String> codeSystems) {
        if (!codeSystems.isEmpty()) {
            for (String cs : codeSystems) {
                String csName = getCodeSystemDisplayName(cs);
                cql.append(String.format("codesystem \"%s\": '%s'%n", csName, cs));
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

        switch (type.toLowerCase()) {
            case "boolean":
                return value.toString().toLowerCase();
            case "integer":
            case "decimal":
                return value.toString();
            case "string":
                return String.format("'%s'", value.toString().replace("'", "\\'"));
            case "datetime":
                String dtVal = value.toString();
                if (!dtVal.startsWith("@"))
                    dtVal = "@" + dtVal;
                if (!dtVal.contains("T"))
                    dtVal += "T00:00:00";
                return dtVal;
            case "time":
                String tVal = value.toString();
                if (!tVal.startsWith("@T")) {
                    tVal = tVal.startsWith("@") ? tVal.replace("@", "@T") : "@T" + tVal;
                }
                return tVal;
            case "code":
                if (value instanceof Map) {
                    Map<String, Object> codeMap = (Map<String, Object>) value;
                    String code = getStr(codeMap, "code", "");
                    String system = getStr(codeMap, "system", "");
                    if (!code.isEmpty() && !system.isEmpty()) {
                        String csName = getCodeSystemDisplayName(system);
                        return String.format("Code '%s' from \"%s\"", code, csName);
                    }
                }
                return null;
            case "concept":
                if (value instanceof Map) {
                    Map<String, Object> conceptMap = (Map<String, Object>) value;
                    String code = getStr(conceptMap, "code", "");
                    String system = getStr(conceptMap, "system", "");
                    String display = getStr(conceptMap, "display", "");
                    if (!code.isEmpty() && !system.isEmpty()) {
                        String csName = getCodeSystemDisplayName(system);
                        String displayPart = !display.isEmpty() ? String.format(" display '%s'", display) : "";
                        return String.format("Concept { Code '%s' from \"%s\" }%s", code, csName, displayPart);
                    }
                }
                return null;
            case "quantity":
                if (value instanceof Map) {
                    Map<String, Object> qtyMap = (Map<String, Object>) value;
                    String qtyValue = getStr(qtyMap, "value", "");
                    String unit = getStr(qtyMap, "unit", "");
                    if (!qtyValue.isEmpty()) {
                        return !unit.isEmpty() ? String.format("%s '%s'", qtyValue, unit) : qtyValue;
                    }
                }
                return null;
            case "interval<integer>":
                if (value instanceof Map) {
                    Map<String, Object> ivl = (Map<String, Object>) value;
                    String low = getStr(ivl, "low", "null");
                    String high = getStr(ivl, "high", "null");
                    return String.format("Interval[%s, %s]", low, high);
                }
                return value.toString();
            case "interval<datetime>":
                if (value instanceof Map) {
                    Map<String, Object> ivl = (Map<String, Object>) value;
                    String low = getStr(ivl, "low", "");
                    String high = getStr(ivl, "high", "");
                    String fmtLow = !low.isEmpty() ? formatDateTimeValue(low) : "null";
                    String fmtHigh = !high.isEmpty() ? formatDateTimeValue(high) : "null";
                    return String.format("Interval[%s, %s]", fmtLow, fmtHigh);
                }
                return value.toString();
            default:
                return value.toString();
        }
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
