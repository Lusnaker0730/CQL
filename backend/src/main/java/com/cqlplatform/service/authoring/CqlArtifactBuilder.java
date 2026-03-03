package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.AuthoringConstants;
import com.cqlplatform.model.authoring.CqlBuildResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts artifact JSON expression trees into CQL source code.
 */
@Component
@Slf4j
public class CqlArtifactBuilder {

    private static final String FHIR_HELPERS = "FHIRHelpers";
    private static final String C3F_LIBRARY = "CDSConnectCommonsForFHIRv401";

    private static final Map<String, String> FHIR_VERSION_MAP = AuthoringConstants.FHIR_VERSION_MAP;
    private static final Map<String, String> FHIR_HELPERS_VERSION_MAP = AuthoringConstants.FHIR_HELPERS_VERSION_MAP;

    /**
     * Per-build context holding base elements for cross-reference lookups
     * and a warnings collector. Created fresh for each buildCql() invocation
     * to ensure thread safety.
     */
    static class BuildContext {
        final List<Map<String, Object>> baseElements;
        final List<String> warnings = new ArrayList<>();

        BuildContext(List<Map<String, Object>> baseElements) {
            this.baseElements = baseElements;
        }

        void warn(String message) {
            warnings.add(message);
        }
    }

    @SuppressWarnings("unchecked")
    public CqlBuildResult buildCql(String name, String version, Map<String, Object> expTreeInclude,
            Map<String, Object> expTreeExclude,
            List<Map<String, Object>> subpopulations,
            List<Map<String, Object>> baseElements,
            List<Map<String, Object>> parameters,
            Map<String, Object> errorStatement,
            List<Map<String, Object>> recommendations) {
        return buildCql(name, version, expTreeInclude, expTreeExclude, subpopulations,
                baseElements, parameters, errorStatement, recommendations, "R4");
    }

    @SuppressWarnings("unchecked")
    public CqlBuildResult buildCql(String name, String version, Map<String, Object> expTreeInclude,
            Map<String, Object> expTreeExclude,
            List<Map<String, Object>> subpopulations,
            List<Map<String, Object>> baseElements,
            List<Map<String, Object>> parameters,
            Map<String, Object> errorStatement,
            List<Map<String, Object>> recommendations,
            String fhirVersion) {

        BuildContext ctx = new BuildContext(baseElements);

        String resolvedFhirVersion = FHIR_VERSION_MAP.getOrDefault(fhirVersion, AuthoringConstants.DEFAULT_FHIR_VERSION);
        String resolvedHelpersVersion = FHIR_HELPERS_VERSION_MAP.getOrDefault(fhirVersion, AuthoringConstants.DEFAULT_FHIR_VERSION);

        StringBuilder cql = new StringBuilder();
        Set<String> valueSets = new LinkedHashSet<>();
        Set<String> codeSystems = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();

        includes.add(String.format("include %s version '%s' called FHIRHelpers", FHIR_HELPERS, resolvedHelpersVersion));
        includes.add(String.format("include %s version '2.1.0' called C3F", C3F_LIBRARY));

        // Collect value sets, codes, and external library includes from trees
        collectFromTree(expTreeInclude, valueSets, codeSystems, codes);
        collectFromTree(expTreeExclude, valueSets, codeSystems, codes);
        collectExternalIncludes(expTreeInclude, includes);
        collectExternalIncludes(expTreeExclude, includes);
        if (baseElements != null) {
            for (Map<String, Object> be : baseElements) {
                collectFromTree(be, valueSets, codeSystems, codes);
                collectExternalIncludes(be, includes);
            }
        }
        if (subpopulations != null) {
            for (Map<String, Object> sp : subpopulations) {
                collectFromTree(sp, valueSets, codeSystems, codes);
                collectExternalIncludes(sp, includes);
            }
        }

        // Library header
        String safeName = name.replaceAll("[^a-zA-Z0-9_]", "_");
        cql.append(String.format("library %s version '%s'%n", safeName, version != null ? version : AuthoringConstants.DEFAULT_VERSION));
        cql.append("\n");
        cql.append(String.format("using FHIR version '%s'%n%n", resolvedFhirVersion));

        // Includes
        for (String inc : includes) {
            cql.append(inc).append("\n");
        }
        cql.append("\n");

        // Value Sets
        if (!valueSets.isEmpty()) {
            for (String vs : valueSets) {
                cql.append(String.format("valueset \"%s\": '%s'%n", vs, vs));
            }
            cql.append("\n");
        }

        // Code Systems
        if (!codeSystems.isEmpty()) {
            for (String cs : codeSystems) {
                // Format: codesystem "LOINC": 'http://loinc.org'
                String csName = getCodeSystemDisplayName(cs);
                cql.append(String.format("codesystem \"%s\": '%s'%n", csName, cs));
            }
            cql.append("\n");
        }

        // Codes
        if (!codes.isEmpty()) {
            for (String codeDecl : codes) {
                cql.append(codeDecl).append("\n");
            }
            cql.append("\n");
        }

        // Parameters
        if (parameters != null) {
            for (Map<String, Object> param : parameters) {
                String pName = getStr(param, "name", "Param");
                String pType = getStr(param, "type", "boolean");
                String cqlType = mapParameterType(pType);
                Object pDefault = param.get("value");
                String formattedDefault = formatParameterDefault(pType, pDefault);
                if (formattedDefault != null) {
                    cql.append(String.format("parameter \"%s\" %s default %s%n", pName, cqlType, formattedDefault));
                } else {
                    cql.append(String.format("parameter \"%s\" %s%n", pName, cqlType));
                }
            }
            cql.append("\n");
        }

        // Context
        cql.append("context Patient\n\n");

        // Base Elements
        if (baseElements != null) {
            for (Map<String, Object> be : baseElements) {
                String beName = getStr(be, "name", "BaseElement");
                String beExpr = buildExpression(be, ctx);
                cql.append(String.format("define \"%s\":%n  %s%n%n", beName, beExpr));
            }
        }

        // Inclusion (MeetsInclusionCriteria)
        String inclusionExpr = buildConjunctionExpression(expTreeInclude, ctx);
        cql.append(String.format("define \"%s\":%n  %s%n%n", AuthoringConstants.DEF_MEETS_INCLUSION, inclusionExpr));

        // Exclusion (MeetsExclusionCriteria)
        String exclusionExpr = buildConjunctionExpression(expTreeExclude, ctx);
        cql.append(String.format("define \"%s\":%n  %s%n%n", AuthoringConstants.DEF_MEETS_EXCLUSION, exclusionExpr));

        // InPopulation
        cql.append(String.format("define \"%s\":%n  \"%s\" and not \"%s\"%n%n",
                AuthoringConstants.DEF_IN_POPULATION, AuthoringConstants.DEF_MEETS_INCLUSION, AuthoringConstants.DEF_MEETS_EXCLUSION));

        // Subpopulations
        if (subpopulations != null) {
            for (Map<String, Object> sp : subpopulations) {
                Boolean special = (Boolean) sp.get("special");
                if (Boolean.TRUE.equals(special))
                    continue;
                String spName = getStr(sp, "subpopulationName", "Subpopulation");
                String spExpr = buildConjunctionExpression(sp, ctx);
                cql.append(String.format("define \"%s\":%n  %s%n%n", spName, spExpr));
            }
        }

        // Recommendations
        if (recommendations != null && !recommendations.isEmpty()) {
            for (int i = 0; i < recommendations.size(); i++) {
                Map<String, Object> rec = recommendations.get(i);
                String defName = recommendations.size() == 1 ? AuthoringConstants.DEF_RECOMMENDATION : AuthoringConstants.DEF_RECOMMENDATION + " " + (i + 1);
                String condition = buildRecommendationCondition(rec);
                boolean isCdsCard = Boolean.TRUE.equals(rec.get("cdsCardMode"));

                cql.append(String.format("define \"%s\":%n  if %s then ", defName));
                if (isCdsCard) {
                    cql.append(buildCdsCardTuple(rec));
                } else {
                    String recText = getStr(rec, "text", "Consider action");
                    cql.append(String.format("'%s'", escapeCqlString(recText)));
                }
                cql.append("\n  else null\n\n");
            }
        }

        // Error statement
        if (errorStatement != null && !errorStatement.isEmpty()) {
            String errExpr = buildErrorStatement(errorStatement);
            if (errExpr != null) {
                cql.append(String.format("define \"%s\":%n  %s%n%n", AuthoringConstants.DEF_ERRORS, errExpr));
            }
        }

        return new CqlBuildResult(cql.toString(), List.copyOf(ctx.warnings));
    }

    @SuppressWarnings("unchecked")
    private String buildConjunctionExpression(Map<String, Object> group, BuildContext ctx) {
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
    private String buildExpression(Map<String, Object> element, BuildContext ctx) {
        String type = getStr(element, "type", "");
        String name = getStr(element, "name", "Unknown");

        // Get fields
        List<Map<String, Object>> fields = (List<Map<String, Object>>) element.get("fields");
        String elementName = getFieldValue(fields, "element_name", name);

        // Build base expression based on type
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
                // Look up the base element name from the reference_id
                String refName = findBaseElementName(refId, ctx);
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
                // reference_id format: "libId:definitionName"
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

        // Apply modifiers
        List<Map<String, Object>> modifiers = (List<Map<String, Object>>) element.get("modifiers");
        if (modifiers != null) {
            for (Map<String, Object> mod : modifiers) {
                expr = applyModifier(expr, mod, ctx);
            }
        }

        // Auto-wrap with exists() if the final return type is still a list.
        // Conjunction expressions (and/or) require boolean operands, so list-returning
        // elements must be converted via exists().
        String finalReturnType = getFinalReturnType(element, modifiers);
        if (finalReturnType != null && finalReturnType.startsWith("list_of_")) {
            expr = String.format("exists(%s)", expr);
        }

        return expr;
    }

    @SuppressWarnings("unchecked")
    private String buildAgeRangeExpression(List<Map<String, Object>> fields) {
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

        return conditions.isEmpty() ? "true" : String.join(" and ", conditions);
    }

    private String mapUnitToAgeFunction(String unit) {
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

    private String buildGenderExpression(List<Map<String, Object>> fields) {
        String gender = getFieldValue(fields, "gender", null);
        if (gender == null || gender.isEmpty())
            return "true";
        return String.format("Patient.gender = '%s'", gender.toLowerCase());
    }

    @SuppressWarnings("unchecked")
    private String buildGenericResourceExpression(String type, List<Map<String, Object>> fields) {
        // Extract FHIR resource type from template type (e.g., GenericObservation_vsac
        // -> Observation)
        String resourceType = type.replace("Generic", "").replace("_vsac", "");

        if (fields == null)
            return String.format("[%s]", resourceType);

        for (Map<String, Object> field : fields) {
            // Check for valueSets array (new rich format)
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
                    Map<String, Object> codeSystem = (Map<String, Object>) code.get("codeSystem");
                    String csName = codeSystem != null ? getStr(codeSystem, "name", "") : "";
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

            // Legacy: single value (string or map)
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

    @SuppressWarnings("unchecked")
    private String applyModifier(String expr, Map<String, Object> modifier, BuildContext ctx) {
        String cqlLibFunc = getStr(modifier, "cqlLibraryFunction", null);
        String cqlTemplate = getStr(modifier, "cqlTemplate", "");
        String modId = getStr(modifier, "id", "");
        Map<String, Object> values = (Map<String, Object>) modifier.get("values");

        // Handle specific templates that need values
        switch (cqlTemplate) {
            case "CheckExistence":
                return String.format("exists(%s)", expr);
            case "BooleanNot":
                return String.format("not (%s)", expr);
            case "Count":
                return String.format("Count(%s)", expr);
            case "AllTrue":
                return String.format("AllTrue(%s)", expr);
            case "AnyTrue":
                return String.format("AnyTrue(%s)", expr);
            case "BooleanExists":
                return String.format("exists(%s)", expr);
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
                        return String.join(" and ", conditions);
                    }
                }
                return expr;
            }
            case "WithUnit": {
                // C3F.WithUnit(expression, 'unit_value')
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
                // C3F.ObservationLookBack(expression, N 'unit')
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

        // Custom modifier templates with {expression} placeholder
        if (cqlTemplate != null && cqlTemplate.contains("{expression}")) {
            return cqlTemplate.replace("{expression}", expr);
        }

        // Default: library function without values
        if (cqlLibFunc != null) {
            return String.format("%s(%s)", cqlLibFunc, expr);
        }

        ctx.warn(String.format("Unknown modifier template '%s' (id='%s'); modifier skipped", cqlTemplate, modId));
        log.warn("Unknown modifier template '{}' (id='{}')", cqlTemplate, modId);
        return expr;
    }

    private String formatDateTimeValue(String value) {
        if (value == null || value.isEmpty())
            return "null";
        if (value.contains("T")) {
            return "@" + value;
        }
        return "@" + value;
    }

    /**
     * Build the if-condition for a recommendation based on its subpopulation refs.
     */
    @SuppressWarnings("unchecked")
    private String buildRecommendationCondition(Map<String, Object> rec) {
        List<Map<String, Object>> spRefs = (List<Map<String, Object>>) rec.get("subpopulations");
        if (spRefs != null && !spRefs.isEmpty()) {
            List<String> conditions = new ArrayList<>();
            for (Map<String, Object> spRef : spRefs) {
                String spId = getStr(spRef, "uniqueId", "");
                String spName = getStr(spRef, "subpopulationName", "");
                if (spId.equals(AuthoringConstants.SUBPOP_DOESNT_MEET_INCLUSION)) {
                    conditions.add("not \"" + AuthoringConstants.DEF_MEETS_INCLUSION + "\"");
                } else if (spId.equals(AuthoringConstants.SUBPOP_MEETS_EXCLUSION)) {
                    conditions.add("\"" + AuthoringConstants.DEF_MEETS_EXCLUSION + "\"");
                } else if (!spName.isEmpty()) {
                    conditions.add(String.format("\"%s\" and \"%s\"", AuthoringConstants.DEF_IN_POPULATION, spName));
                }
            }
            if (conditions.isEmpty()) {
                return "\"" + AuthoringConstants.DEF_IN_POPULATION + "\"";
            }
            return String.join(" and ", conditions);
        }
        return "\"" + AuthoringConstants.DEF_IN_POPULATION + "\"";
    }

    /**
     * Build a CQL Tuple literal for a CDS Card recommendation.
     */
    @SuppressWarnings("unchecked")
    private String buildCdsCardTuple(Map<String, Object> rec) {
        String summary = getStr(rec, "text", "Consider action");
        String detail = getStr(rec, "detail", null);
        String indicator = getStr(rec, "indicator", "info");
        String sourceLabel = getStr(rec, "sourceLabel", null);
        String selectionBehavior = getStr(rec, "selectionBehavior", null);
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) rec.get("suggestions");

        StringBuilder sb = new StringBuilder("Tuple {\n");
        sb.append(String.format("    summary: '%s'", escapeCqlString(summary)));
        if (detail != null && !detail.isEmpty()) {
            sb.append(String.format(",\n    detail: '%s'", escapeCqlString(detail)));
        }
        sb.append(String.format(",\n    indicator: '%s'", escapeCqlString(indicator)));
        if (sourceLabel != null && !sourceLabel.isEmpty()) {
            sb.append(String.format(",\n    sourceLabel: '%s'", escapeCqlString(sourceLabel)));
        }
        if (selectionBehavior != null && !selectionBehavior.isEmpty()) {
            sb.append(String.format(",\n    selectionBehavior: '%s'", escapeCqlString(selectionBehavior)));
        }
        if (suggestions != null && !suggestions.isEmpty()) {
            sb.append(",\n    suggestions: {\n");
            for (int i = 0; i < suggestions.size(); i++) {
                if (i > 0) sb.append(",\n");
                sb.append("      ").append(buildSuggestionTuple(suggestions.get(i)));
            }
            sb.append("\n    }");
        }
        sb.append("\n  }");
        return sb.toString();
    }

    /**
     * Build a CQL Tuple literal for a single suggestion.
     */
    @SuppressWarnings("unchecked")
    private String buildSuggestionTuple(Map<String, Object> sug) {
        String label = getStr(sug, "label", "");
        Boolean isRecommended = (Boolean) sug.get("isRecommended");
        List<Map<String, Object>> actions = (List<Map<String, Object>>) sug.get("actions");

        StringBuilder sb = new StringBuilder("Tuple { ");
        sb.append(String.format("label: '%s'", escapeCqlString(label)));
        if (Boolean.TRUE.equals(isRecommended)) {
            sb.append(", isRecommended: true");
        }
        if (actions != null && !actions.isEmpty()) {
            sb.append(", actions: {\n");
            for (int i = 0; i < actions.size(); i++) {
                if (i > 0) sb.append(",\n");
                sb.append("        ").append(buildActionTuple(actions.get(i)));
            }
            sb.append("\n      }");
        }
        sb.append(" }");
        return sb.toString();
    }

    /**
     * Build a CQL Tuple literal for a single suggestion action.
     */
    private String buildActionTuple(Map<String, Object> action) {
        String type = getStr(action, "type", "create");
        String description = getStr(action, "description", "");
        StringBuilder sb = new StringBuilder("Tuple { ");
        sb.append(String.format("type: '%s'", escapeCqlString(type)));
        if (!description.isEmpty()) {
            sb.append(String.format(", description: '%s'", escapeCqlString(description)));
        }
        sb.append(" }");
        return sb.toString();
    }

    /**
     * Escape single quotes and backslashes for CQL string literals.
     */
    private String escapeCqlString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    @SuppressWarnings("unchecked")
    private String buildErrorStatement(Map<String, Object> errorStatement) {
        List<Map<String, Object>> clauses = (List<Map<String, Object>>) errorStatement.get("ifThenClauses");
        String elseClause = getStr(errorStatement, "elseClause", null);

        if (clauses == null || clauses.isEmpty())
            return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            Map<String, Object> clause = clauses.get(i);
            Map<String, Object> condition = (Map<String, Object>) clause.get("ifCondition");
            String thenClause = getStr(clause, "thenClause", "null");
            String condValue = condition != null ? getStr(condition, "value", "true") : "true";

            // Map condition values to CQL expressions
            String cqlCondition = mapErrorConditionToCql(condValue);

            if (i == 0) {
                sb.append(String.format("if %s then '%s'", cqlCondition, thenClause));
            } else {
                sb.append(String.format("\n  else if %s then '%s'", cqlCondition, thenClause));
            }
        }

        if (elseClause != null && !elseClause.isEmpty()) {
            sb.append(String.format("\n  else '%s'", elseClause));
        } else {
            sb.append("\n  else null");
        }

        return sb.toString();
    }

    private String findBaseElementName(String uniqueId, BuildContext ctx) {
        if (ctx.baseElements == null || uniqueId == null)
            return null;
        for (Map<String, Object> be : ctx.baseElements) {
            if (uniqueId.equals(be.get("uniqueId"))) {
                return getStr(be, "name", null);
            }
        }
        return null;
    }

    private String mapErrorConditionToCql(String condValue) {
        if (condValue == null)
            return "true";
        switch (condValue) {
            case "null":
                return "\"" + AuthoringConstants.DEF_RECOMMENDATION + "\" is null";
            case "doesnt_meet_inclusion":
                return "not \"" + AuthoringConstants.DEF_MEETS_INCLUSION + "\"";
            case "meets_exclusion":
                return "\"" + AuthoringConstants.DEF_MEETS_EXCLUSION + "\"";
            case "errors":
                return "\"" + AuthoringConstants.DEF_ERRORS + "\" is not null";
            default:
                return condValue;
        }
    }

    /**
     * Scan expression tree for externalCqlRef elements and collect include
     * statements
     * for the external libraries they reference.
     */
    @SuppressWarnings("unchecked")
    private void collectExternalIncludes(Map<String, Object> node, Set<String> includes) {
        if (node == null)
            return;

        String type = getStr(node, "type", "");
        if ("externalCqlRef".equals(type)) {
            List<Map<String, Object>> fields = (List<Map<String, Object>>) node.get("fields");
            String libName = getFieldValue(fields, "library_name", null);
            String libVersion = getFieldValue(fields, "library_version", null);
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

        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("childInstances");
        if (children != null) {
            for (Map<String, Object> child : children) {
                collectExternalIncludes(child, includes);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectFromTree(Map<String, Object> node, Set<String> valueSets,
            Set<String> codeSystems, Set<String> codes) {
        if (node == null)
            return;

        List<Map<String, Object>> fields = (List<Map<String, Object>>) node.get("fields");
        if (fields != null) {
            for (Map<String, Object> field : fields) {
                // Legacy single value
                Object value = field.get("value");
                if (value instanceof Map) {
                    Map<String, Object> vsVal = (Map<String, Object>) value;
                    String vsName = (String) vsVal.get("name");
                    if (vsName != null)
                        valueSets.add(vsName);
                }

                // Rich valueSets array
                List<Map<String, Object>> vsRefs = (List<Map<String, Object>>) field.get("valueSets");
                if (vsRefs != null) {
                    for (Map<String, Object> vs : vsRefs) {
                        String vsName = (String) vs.get("name");
                        String vsOid = (String) vs.get("oid");
                        if (vsName != null)
                            valueSets.add(vsName);
                    }
                }

                // Rich codes array
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
                            // Format: code "Hemoglobin [Mass/volume] in Blood": '718-7' from "LOINC"
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
                collectFromTree(child, valueSets, codeSystems, codes);
            }
        }
    }

    private String getCodeSystemDisplayName(String systemUrl) {
        return AuthoringConstants.getCodeSystemDisplayName(systemUrl);
    }

    @SuppressWarnings("unchecked")
    private boolean hasSubpopulationRefs(Map<String, Object> recommendation) {
        List<Map<String, Object>> spRefs = (List<Map<String, Object>>) recommendation.get("subpopulations");
        return spRefs != null && !spRefs.isEmpty();
    }

    private String mapParameterType(String type) {
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
    private String formatParameterDefault(String type, Object value) {
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

    /**
     * Determine the final return type of an element after all modifiers are applied.
     * If modifiers are present, uses the last modifier's returnType; otherwise
     * falls back to the element's own returnType.
     */
    @SuppressWarnings("unchecked")
    private String getFinalReturnType(Map<String, Object> element, List<Map<String, Object>> modifiers) {
        if (modifiers != null && !modifiers.isEmpty()) {
            Map<String, Object> lastMod = modifiers.get(modifiers.size() - 1);
            String rt = getStr(lastMod, "returnType", null);
            if (rt != null) return rt;
        }
        return getStr(element, "returnType", null);
    }

    private String getStr(Map<String, Object> map, String key, String defaultVal) {
        if (map == null)
            return defaultVal;
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    @SuppressWarnings("unchecked")
    private String getFieldValue(List<Map<String, Object>> fields, String fieldId, String defaultVal) {
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
}
