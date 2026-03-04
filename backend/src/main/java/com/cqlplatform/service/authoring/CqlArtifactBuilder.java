package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.AuthoringConstants;
import com.cqlplatform.model.authoring.CqlBuildResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Converts CDS artifact JSON expression trees into CQL source code.
 * Delegates shared expression logic to {@link ExpressionCqlEngine}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CqlArtifactBuilder {

    private static final String FHIR_HELPERS = "FHIRHelpers";
    private static final String C3F_LIBRARY = "CDSConnectCommonsForFHIRv401";

    private final ExpressionCqlEngine engine;

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

    public CqlBuildResult buildCql(String name, String version, Map<String, Object> expTreeInclude,
            Map<String, Object> expTreeExclude,
            List<Map<String, Object>> subpopulations,
            List<Map<String, Object>> baseElements,
            List<Map<String, Object>> parameters,
            Map<String, Object> errorStatement,
            List<Map<String, Object>> recommendations,
            String fhirVersion) {

        ExpressionCqlEngine.BuildContext ctx = new ExpressionCqlEngine.BuildContext(baseElements);

        String resolvedFhirVersion = engine.resolveFhirVersion(fhirVersion);
        String resolvedHelpersVersion = engine.resolveHelpersVersion(fhirVersion);

        StringBuilder cql = new StringBuilder();
        Set<String> valueSets = new LinkedHashSet<>();
        Set<String> codeSystems = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();

        includes.add(String.format("include %s version '%s' called FHIRHelpers", FHIR_HELPERS, resolvedHelpersVersion));
        includes.add(String.format("include %s version '2.1.0' called C3F", C3F_LIBRARY));

        // Collect value sets, codes, and external library includes from trees
        engine.collectDeclarations(expTreeInclude, valueSets, codeSystems, codes, includes);
        engine.collectDeclarations(expTreeExclude, valueSets, codeSystems, codes, includes);
        if (baseElements != null) {
            for (Map<String, Object> be : baseElements) {
                engine.collectDeclarations(be, valueSets, codeSystems, codes, includes);
            }
        }
        if (subpopulations != null) {
            for (Map<String, Object> sp : subpopulations) {
                engine.collectDeclarations(sp, valueSets, codeSystems, codes, includes);
            }
        }

        // Library header
        String safeName = name.replaceAll("[^a-zA-Z0-9_]", "_");
        cql.append(String.format("library %s version '%s'%n", safeName, version != null ? version : AuthoringConstants.DEFAULT_VERSION));
        cql.append("\n");
        cql.append(String.format("using FHIR version '%s'%n%n", resolvedFhirVersion));

        // Includes
        engine.emitIncludes(cql, includes);

        // Value Sets
        engine.emitValueSets(cql, valueSets);

        // Code Systems
        engine.emitCodeSystems(cql, codeSystems);

        // Codes
        engine.emitCodes(cql, codes);

        // Parameters
        if (parameters != null) {
            for (Map<String, Object> param : parameters) {
                String pName = engine.getStr(param, "name", "Param");
                String pType = engine.getStr(param, "type", "boolean");
                String cqlType = engine.mapParameterType(pType);
                Object pDefault = param.get("value");
                String formattedDefault = engine.formatParameterDefault(pType, pDefault);
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
                String beName = engine.getStr(be, "name", "BaseElement");
                String beExpr = engine.buildExpression(be, ctx);
                cql.append(String.format("define \"%s\":%n  %s%n%n", beName, beExpr));
            }
        }

        // Inclusion (MeetsInclusionCriteria)
        String inclusionExpr = engine.buildConjunctionExpression(expTreeInclude, ctx);
        cql.append(String.format("define \"%s\":%n  %s%n%n", AuthoringConstants.DEF_MEETS_INCLUSION, inclusionExpr));

        // Exclusion (MeetsExclusionCriteria)
        String exclusionExpr = engine.buildConjunctionExpression(expTreeExclude, ctx);
        if ("null".equals(exclusionExpr)) {
            exclusionExpr = "false";
        }
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
                String spName = engine.getStr(sp, "subpopulationName", "Subpopulation");
                String spExpr = engine.buildConjunctionExpression(sp, ctx);
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

                cql.append(String.format("define \"%s\":%n  if %s then ", defName, condition));
                if (isCdsCard) {
                    cql.append(buildCdsCardTuple(rec));
                } else {
                    String recText = engine.getStr(rec, "text", "Consider action");
                    cql.append(String.format("'%s'", engine.escapeCqlString(recText)));
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

    // ── CDS-specific methods (not shared with eCQM) ─────────────────────

    @SuppressWarnings("unchecked")
    private String buildRecommendationCondition(Map<String, Object> rec) {
        List<Map<String, Object>> spRefs = (List<Map<String, Object>>) rec.get("subpopulations");
        if (spRefs != null && !spRefs.isEmpty()) {
            List<String> conditions = new ArrayList<>();
            for (Map<String, Object> spRef : spRefs) {
                String spId = engine.getStr(spRef, "uniqueId", "");
                String spName = engine.getStr(spRef, "subpopulationName", "");
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

    @SuppressWarnings("unchecked")
    private String buildCdsCardTuple(Map<String, Object> rec) {
        String summary = engine.getStr(rec, "text", "Consider action");
        String detail = engine.getStr(rec, "detail", null);
        String indicator = engine.getStr(rec, "indicator", "info");
        String sourceLabel = engine.getStr(rec, "sourceLabel", null);
        String selectionBehavior = engine.getStr(rec, "selectionBehavior", null);
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) rec.get("suggestions");

        StringBuilder sb = new StringBuilder("Tuple {\n");
        sb.append(String.format("    summary: '%s'", engine.escapeCqlString(summary)));
        if (detail != null && !detail.isEmpty()) {
            sb.append(String.format(",\n    detail: '%s'", engine.escapeCqlString(detail)));
        }
        sb.append(String.format(",\n    indicator: '%s'", engine.escapeCqlString(indicator)));
        if (sourceLabel != null && !sourceLabel.isEmpty()) {
            sb.append(String.format(",\n    sourceLabel: '%s'", engine.escapeCqlString(sourceLabel)));
        }
        if (selectionBehavior != null && !selectionBehavior.isEmpty()) {
            sb.append(String.format(",\n    selectionBehavior: '%s'", engine.escapeCqlString(selectionBehavior)));
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

    @SuppressWarnings("unchecked")
    private String buildSuggestionTuple(Map<String, Object> sug) {
        String label = engine.getStr(sug, "label", "");
        Boolean isRecommended = (Boolean) sug.get("isRecommended");
        List<Map<String, Object>> actions = (List<Map<String, Object>>) sug.get("actions");

        StringBuilder sb = new StringBuilder("Tuple { ");
        sb.append(String.format("label: '%s'", engine.escapeCqlString(label)));
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

    private String buildActionTuple(Map<String, Object> action) {
        String type = engine.getStr(action, "type", "create");
        String description = engine.getStr(action, "description", "");
        StringBuilder sb = new StringBuilder("Tuple { ");
        sb.append(String.format("type: '%s'", engine.escapeCqlString(type)));
        if (!description.isEmpty()) {
            sb.append(String.format(", description: '%s'", engine.escapeCqlString(description)));
        }
        sb.append(" }");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String buildErrorStatement(Map<String, Object> errorStatement) {
        List<Map<String, Object>> clauses = (List<Map<String, Object>>) errorStatement.get("ifThenClauses");
        String elseClause = engine.getStr(errorStatement, "elseClause", null);

        if (clauses == null || clauses.isEmpty())
            return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            Map<String, Object> clause = clauses.get(i);
            Map<String, Object> condition = (Map<String, Object>) clause.get("ifCondition");
            String thenClause = engine.getStr(clause, "thenClause", "null");
            String condValue = condition != null ? engine.getStr(condition, "value", "true") : "true";

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
}
