package com.cqlplatform.service.authoring;

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
    private static final String C3F_LIBRARY = "CDS_Connect_Commons_for_FHIRv401";

    @SuppressWarnings("unchecked")
    public String buildCql(String name, String version, Map<String, Object> expTreeInclude,
                           Map<String, Object> expTreeExclude,
                           List<Map<String, Object>> subpopulations,
                           List<Map<String, Object>> baseElements,
                           List<Map<String, Object>> parameters,
                           Map<String, Object> errorStatement,
                           List<Map<String, Object>> recommendations) {

        StringBuilder cql = new StringBuilder();
        Set<String> valueSets = new LinkedHashSet<>();
        Set<String> codeSystems = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();

        includes.add(String.format("include %s version '4.0.1' called FHIRHelpers", FHIR_HELPERS));
        includes.add(String.format("include %s version '1.1.1' called C3F", C3F_LIBRARY));

        // Collect value sets and codes from trees
        collectValueSetsFromTree(expTreeInclude, valueSets);
        collectValueSetsFromTree(expTreeExclude, valueSets);
        if (baseElements != null) {
            for (Map<String, Object> be : baseElements) {
                collectValueSetsFromTree(be, valueSets);
            }
        }
        if (subpopulations != null) {
            for (Map<String, Object> sp : subpopulations) {
                collectValueSetsFromTree(sp, valueSets);
            }
        }

        // Library header
        String safeName = name.replaceAll("[^a-zA-Z0-9_]", "_");
        cql.append(String.format("library %s version '%s'%n", safeName, version != null ? version : "1.0.0"));
        cql.append("\n");
        cql.append("using FHIR version '4.0.1'\n\n");

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

        // Parameters
        if (parameters != null) {
            for (Map<String, Object> param : parameters) {
                String pName = getStr(param, "name", "Param");
                String pType = getStr(param, "type", "Boolean");
                Object pDefault = param.get("value");
                if (pDefault != null) {
                    cql.append(String.format("parameter \"%s\" %s default %s%n", pName, pType, pDefault));
                } else {
                    cql.append(String.format("parameter \"%s\" %s%n", pName, pType));
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
                String beExpr = buildExpression(be);
                cql.append(String.format("define \"%s\":%n  %s%n%n", beName, beExpr));
            }
        }

        // Inclusion (MeetsInclusionCriteria)
        String inclusionExpr = buildConjunctionExpression(expTreeInclude);
        cql.append(String.format("define \"MeetsInclusionCriteria\":%n  %s%n%n", inclusionExpr));

        // Exclusion (MeetsExclusionCriteria)
        String exclusionExpr = buildConjunctionExpression(expTreeExclude);
        cql.append(String.format("define \"MeetsExclusionCriteria\":%n  %s%n%n", exclusionExpr));

        // InPopulation
        cql.append("define \"InPopulation\":\n  \"MeetsInclusionCriteria\" and not \"MeetsExclusionCriteria\"\n\n");

        // Subpopulations
        if (subpopulations != null) {
            for (Map<String, Object> sp : subpopulations) {
                Boolean special = (Boolean) sp.get("special");
                if (Boolean.TRUE.equals(special)) continue;
                String spName = getStr(sp, "subpopulationName", "Subpopulation");
                String spExpr = buildConjunctionExpression(sp);
                cql.append(String.format("define \"%s\":%n  %s%n%n", spName, spExpr));
            }
        }

        // Recommendations
        if (recommendations != null && !recommendations.isEmpty()) {
            if (recommendations.size() == 1 && !hasSubpopulationRefs(recommendations.get(0))) {
                // Simple single recommendation
                cql.append("define \"Recommendation\":\n");
                cql.append("  if \"InPopulation\" then ");
                String recText = getStr(recommendations.get(0), "text", "Consider action");
                cql.append(String.format("'%s'", recText.replace("'", "\\'")));
                cql.append("\n  else null\n\n");
            } else {
                // Multiple recommendations or subpopulation-targeted
                for (int i = 0; i < recommendations.size(); i++) {
                    Map<String, Object> rec = recommendations.get(i);
                    String recText = getStr(rec, "text", "Consider action");
                    String defName = recommendations.size() == 1 ? "Recommendation" : "Recommendation " + (i + 1);

                    List<Map<String, Object>> spRefs = (List<Map<String, Object>>) rec.get("subpopulations");
                    if (spRefs != null && !spRefs.isEmpty()) {
                        List<String> conditions = new ArrayList<>();
                        conditions.add("\"InPopulation\"");
                        for (Map<String, Object> spRef : spRefs) {
                            String spName = getStr(spRef, "subpopulationName", "");
                            if (!spName.isEmpty()) {
                                conditions.add(String.format("\"%s\"", spName));
                            }
                        }
                        cql.append(String.format("define \"%s\":%n  if %s then '%s'%n  else null%n%n",
                                defName, String.join(" and ", conditions), recText.replace("'", "\\'")));
                    } else {
                        cql.append(String.format("define \"%s\":%n  if \"InPopulation\" then '%s'%n  else null%n%n",
                                defName, recText.replace("'", "\\'")));
                    }
                }
            }
        }

        // Error statement
        if (errorStatement != null && !errorStatement.isEmpty()) {
            String errExpr = buildErrorStatement(errorStatement);
            if (errExpr != null) {
                cql.append(String.format("define \"Errors\":%n  %s%n%n", errExpr));
            }
        }

        return cql.toString();
    }

    @SuppressWarnings("unchecked")
    private String buildConjunctionExpression(Map<String, Object> group) {
        if (group == null) return "null";

        List<Map<String, Object>> children = (List<Map<String, Object>>) group.get("childInstances");
        if (children == null || children.isEmpty()) return "null";

        String conjId = getStr(group, "id", "And");
        String operator = "Or".equals(conjId) ? " or " : " and ";

        List<String> childExprs = new ArrayList<>();
        for (Map<String, Object> child : children) {
            Boolean conjunction = (Boolean) child.get("conjunction");
            if (Boolean.TRUE.equals(conjunction)) {
                childExprs.add("(" + buildConjunctionExpression(child) + ")");
            } else {
                childExprs.add(buildExpression(child));
            }
        }

        return String.join(operator + "\n  ", childExprs);
    }

    @SuppressWarnings("unchecked")
    private String buildExpression(Map<String, Object> element) {
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
            default:
                if (type.startsWith("Generic")) {
                    expr = buildGenericResourceExpression(type, fields);
                } else {
                    expr = "true /* " + elementName + " */";
                }
        }

        // Apply modifiers
        List<Map<String, Object>> modifiers = (List<Map<String, Object>>) element.get("modifiers");
        if (modifiers != null) {
            for (Map<String, Object> mod : modifiers) {
                expr = applyModifier(expr, mod);
            }
        }

        return expr;
    }

    @SuppressWarnings("unchecked")
    private String buildAgeRangeExpression(List<Map<String, Object>> fields) {
        String minAge = getFieldValue(fields, "min_age", null);
        String maxAge = getFieldValue(fields, "max_age", null);
        String unit = getFieldValue(fields, "unit_of_time", "years");

        List<String> conditions = new ArrayList<>();
        if (minAge != null && !minAge.isEmpty()) {
            conditions.add(String.format("AgeInYears() >= %s", minAge));
        }
        if (maxAge != null && !maxAge.isEmpty()) {
            conditions.add(String.format("AgeInYears() <= %s", maxAge));
        }

        return conditions.isEmpty() ? "true" : String.join(" and ", conditions);
    }

    private String buildGenderExpression(List<Map<String, Object>> fields) {
        String gender = getFieldValue(fields, "gender", null);
        if (gender == null || gender.isEmpty()) return "true";
        return String.format("Patient.gender = '%s'", gender.toLowerCase());
    }

    @SuppressWarnings("unchecked")
    private String buildGenericResourceExpression(String type, List<Map<String, Object>> fields) {
        // Extract FHIR resource type from template type (e.g., GenericObservation_vsac -> Observation)
        String resourceType = type.replace("Generic", "").replace("_vsac", "");

        // Try to find the VSAC value set field
        if (fields != null) {
            for (Map<String, Object> field : fields) {
                Object value = field.get("value");
                if (value instanceof Map) {
                    Map<String, Object> vsVal = (Map<String, Object>) value;
                    String vsName = (String) vsVal.get("name");
                    String vsOid = (String) vsVal.get("oid");
                    if (vsName != null) {
                        return String.format("[%s: \"%s\"]", resourceType, vsName);
                    }
                } else if (value instanceof String && !((String) value).isEmpty()) {
                    return String.format("[%s: \"%s\"]", resourceType, value);
                }
            }
        }

        return String.format("[%s]", resourceType);
    }

    @SuppressWarnings("unchecked")
    private String applyModifier(String expr, Map<String, Object> modifier) {
        String cqlLibFunc = getStr(modifier, "cqlLibraryFunction", null);
        String cqlTemplate = getStr(modifier, "cqlTemplate", "");

        if (cqlLibFunc != null) {
            return String.format("%s(%s)", cqlLibFunc, expr);
        }

        switch (cqlTemplate) {
            case "CheckExistence":
                return String.format("exists(%s)", expr);
            case "BooleanNot":
                return String.format("not (%s)", expr);
            case "Count":
                return String.format("Count(%s)", expr);
            case "ValueComparisonNumber": {
                Map<String, Object> values = (Map<String, Object>) modifier.get("values");
                if (values != null) {
                    String op = getStr(values, "minOperator", ">");
                    String val = getStr(values, "minValue", "0");
                    return String.format("(%s) %s %s", expr, op, val);
                }
                return expr;
            }
            default:
                return expr;
        }
    }

    @SuppressWarnings("unchecked")
    private String buildErrorStatement(Map<String, Object> errorStatement) {
        List<Map<String, Object>> clauses = (List<Map<String, Object>>) errorStatement.get("ifThenClauses");
        String elseClause = getStr(errorStatement, "elseClause", null);

        if (clauses == null || clauses.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            Map<String, Object> clause = clauses.get(i);
            Map<String, Object> condition = (Map<String, Object>) clause.get("ifCondition");
            String thenClause = getStr(clause, "thenClause", "null");
            String condValue = condition != null ? getStr(condition, "value", "true") : "true";

            if (i == 0) {
                sb.append(String.format("if %s then '%s'", condValue, thenClause));
            } else {
                sb.append(String.format("\n  else if %s then '%s'", condValue, thenClause));
            }
        }

        if (elseClause != null && !elseClause.isEmpty()) {
            sb.append(String.format("\n  else '%s'", elseClause));
        } else {
            sb.append("\n  else null");
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void collectValueSetsFromTree(Map<String, Object> node, Set<String> valueSets) {
        if (node == null) return;

        List<Map<String, Object>> fields = (List<Map<String, Object>>) node.get("fields");
        if (fields != null) {
            for (Map<String, Object> field : fields) {
                Object value = field.get("value");
                if (value instanceof Map) {
                    Map<String, Object> vsVal = (Map<String, Object>) value;
                    String vsName = (String) vsVal.get("name");
                    if (vsName != null) valueSets.add(vsName);
                }
                List<Map<String, Object>> vsRefs = (List<Map<String, Object>>) field.get("valueSets");
                if (vsRefs != null) {
                    for (Map<String, Object> vs : vsRefs) {
                        String vsName = (String) vs.get("name");
                        if (vsName != null) valueSets.add(vsName);
                    }
                }
            }
        }

        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("childInstances");
        if (children != null) {
            for (Map<String, Object> child : children) {
                collectValueSetsFromTree(child, valueSets);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasSubpopulationRefs(Map<String, Object> recommendation) {
        List<Map<String, Object>> spRefs = (List<Map<String, Object>>) recommendation.get("subpopulations");
        return spRefs != null && !spRefs.isEmpty();
    }

    private String getStr(Map<String, Object> map, String key, String defaultVal) {
        if (map == null) return defaultVal;
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    @SuppressWarnings("unchecked")
    private String getFieldValue(List<Map<String, Object>> fields, String fieldId, String defaultVal) {
        if (fields == null) return defaultVal;
        for (Map<String, Object> field : fields) {
            if (fieldId.equals(field.get("id"))) {
                Object val = field.get("value");
                return val != null ? val.toString() : defaultVal;
            }
        }
        return defaultVal;
    }
}
