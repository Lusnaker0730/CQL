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
    private final CqlTemplateEngine templateEngine;

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

        ExpressionCqlEngine.BuildContext ctx = new ExpressionCqlEngine.BuildContext(baseElements, parameters);

        String resolvedFhirVersion = engine.resolveFhirVersion(fhirVersion);
        String resolvedHelpersVersion = engine.resolveHelpersVersion(fhirVersion);

        // ── Collect declarations ──────────────────────────────────────────
        Set<String> valueSets = new LinkedHashSet<>();
        Set<String> codeSystems = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();

        includes.add(String.format("include %s version '%s' called FHIRHelpers", FHIR_HELPERS, resolvedHelpersVersion));
        includes.add(String.format("include %s version '2.1.0' called C3F", C3F_LIBRARY));

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

        // ── Build data model ─────────────────────────────────────────────
        Map<String, Object> dataModel = new HashMap<>();
        String safeName = name.replaceAll("[^a-zA-Z0-9_]", "_");
        dataModel.put("safeName", safeName);
        dataModel.put("version", engine.escapeCqlString(version != null ? version : AuthoringConstants.DEFAULT_VERSION));
        dataModel.put("fhirVersion", resolvedFhirVersion);
        dataModel.put("includes", includes);

        // Escape value set names for use in quoted identifiers and string literals in template
        List<Map<String, String>> escapedValueSets = new ArrayList<>();
        for (String vs : valueSets) {
            escapedValueSets.add(Map.of("identifier", engine.escapeCqlIdentifier(vs), "uri", engine.escapeCqlString(vs)));
        }
        dataModel.put("valueSets", escapedValueSets);

        // Code systems: convert URL → {name, id} with escaping
        List<Map<String, String>> codeSystemEntries = new ArrayList<>();
        for (String cs : codeSystems) {
            codeSystemEntries.add(Map.of(
                    "name", engine.escapeCqlIdentifier(engine.getCodeSystemDisplayName(cs)),
                    "id", engine.escapeCqlString(cs)));
        }
        dataModel.put("codeSystemEntries", codeSystemEntries);
        dataModel.put("codes", codes);

        // Parameters
        List<Map<String, String>> paramModels = new ArrayList<>();
        if (parameters != null) {
            for (Map<String, Object> param : parameters) {
                String pName = engine.getStr(param, "name", "Param");
                String pType = engine.getStr(param, "type", "boolean");
                String cqlType = engine.mapParameterType(pType);
                String formattedDefault = engine.formatParameterDefault(pType, param.get("value"));
                Map<String, String> pm = new HashMap<>();
                pm.put("name", engine.escapeCqlIdentifier(pName));
                pm.put("cqlType", cqlType);
                pm.put("formattedDefault", formattedDefault != null ? formattedDefault : "");
                paramModels.add(pm);
            }
        }
        dataModel.put("params", paramModels);

        // Base Elements — emit non-arithmetic first, then arithmetic (which may reference other base elements)
        List<Map<String, String>> baseElementModels = new ArrayList<>();
        if (baseElements != null) {
            List<Map<String, Object>> nonArithmetic = new ArrayList<>();
            List<Map<String, Object>> arithmetic = new ArrayList<>();
            for (Map<String, Object> be : baseElements) {
                if ("arithmeticExpression".equals(engine.getStr(be, "type", ""))) {
                    arithmetic.add(be);
                } else {
                    nonArithmetic.add(be);
                }
            }
            for (Map<String, Object> be : nonArithmetic) {
                String beName = engine.escapeCqlIdentifier(engine.getStr(be, "name", "BaseElement"));
                String beExpr;
                if (Boolean.TRUE.equals(be.get("conjunction"))) {
                    beExpr = engine.buildConjunctionExpression(be, ctx);
                } else {
                    beExpr = engine.buildExpression(be, ctx);
                }
                baseElementModels.add(Map.of("name", beName, "expression", beExpr));
            }
            for (Map<String, Object> be : arithmetic) {
                String beName = engine.escapeCqlIdentifier(engine.getStr(be, "name", "BaseElement"));
                String beExpr = engine.buildExpression(be, ctx);
                baseElementModels.add(Map.of("name", beName, "expression", beExpr));
            }
        }
        dataModel.put("baseElements", baseElementModels);

        // Inclusion / Exclusion
        String inclusionExpr = engine.buildConjunctionExpression(expTreeInclude, ctx);
        String exclusionExpr = engine.buildConjunctionExpression(expTreeExclude, ctx);
        if ("null".equals(exclusionExpr)) {
            exclusionExpr = "false";
        }
        dataModel.put("inclusionExpr", inclusionExpr);
        dataModel.put("exclusionExpr", exclusionExpr);
        dataModel.put("defMeetsInclusion", AuthoringConstants.DEF_MEETS_INCLUSION);
        dataModel.put("defMeetsExclusion", AuthoringConstants.DEF_MEETS_EXCLUSION);
        dataModel.put("defInPopulation", AuthoringConstants.DEF_IN_POPULATION);

        // Subpopulations
        List<Map<String, String>> subpopModels = new ArrayList<>();
        if (subpopulations != null) {
            for (Map<String, Object> sp : subpopulations) {
                if (Boolean.TRUE.equals(sp.get("special"))) continue;
                String spName = engine.escapeCqlIdentifier(engine.getStr(sp, "subpopulationName", "Subpopulation"));
                String spExpr = engine.buildConjunctionExpression(sp, ctx);
                subpopModels.add(Map.of("name", spName, "expression", spExpr));
            }
        }
        dataModel.put("subpopulations", subpopModels);

        // Recommendations
        List<Map<String, String>> recModels = new ArrayList<>();
        if (recommendations != null && !recommendations.isEmpty()) {
            for (int i = 0; i < recommendations.size(); i++) {
                Map<String, Object> rec = recommendations.get(i);
                String defName = recommendations.size() == 1
                        ? AuthoringConstants.DEF_RECOMMENDATION
                        : AuthoringConstants.DEF_RECOMMENDATION + " " + (i + 1);
                String condition = buildRecommendationCondition(rec);
                String value;
                if (Boolean.TRUE.equals(rec.get("cdsCardMode"))) {
                    value = buildCdsCardTuple(rec);
                } else {
                    String recText = engine.getStr(rec, "text", "Consider action");
                    value = String.format("'%s'", engine.escapeCqlString(recText));
                }
                recModels.add(Map.of("defName", defName, "condition", condition, "value", value));
            }
        }
        dataModel.put("recommendations", recModels);

        // Error statement
        String errorExpr = (errorStatement != null && !errorStatement.isEmpty())
                ? buildErrorStatement(errorStatement) : null;
        dataModel.put("errorExpr", errorExpr != null ? errorExpr : "");
        dataModel.put("defErrors", AuthoringConstants.DEF_ERRORS);

        // ── Render ───────────────────────────────────────────────────────
        String cql = templateEngine.render("artifact.ftl", dataModel);
        return new CqlBuildResult(cql, List.copyOf(ctx.warnings));
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
                    conditions.add(String.format("\"%s\" and \"%s\"", AuthoringConstants.DEF_IN_POPULATION, engine.escapeCqlIdentifier(spName)));
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
        String summary = engine.escapeCqlString(engine.getStr(rec, "text", "Consider action"));
        String detail = engine.getStr(rec, "detail", null);
        String indicator = engine.escapeCqlString(engine.getStr(rec, "indicator", "info"));
        String sourceLabel = engine.getStr(rec, "sourceLabel", null);
        String selectionBehavior = engine.getStr(rec, "selectionBehavior", null);
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) rec.get("suggestions");

        Map<String, Object> model = new HashMap<>();
        model.put("summary", summary);
        model.put("detail", detail != null && !detail.isEmpty() ? engine.escapeCqlString(detail) : "");
        model.put("indicator", indicator);
        model.put("sourceLabel", sourceLabel != null && !sourceLabel.isEmpty() ? engine.escapeCqlString(sourceLabel) : "");
        model.put("selectionBehavior", selectionBehavior != null && !selectionBehavior.isEmpty() ? engine.escapeCqlString(selectionBehavior) : "");

        if (suggestions != null && !suggestions.isEmpty()) {
            List<Map<String, Object>> sugModels = new ArrayList<>();
            for (Map<String, Object> sug : suggestions) {
                Map<String, Object> sugModel = new HashMap<>();
                sugModel.put("label", engine.escapeCqlString(engine.getStr(sug, "label", "")));
                sugModel.put("isRecommended", Boolean.TRUE.equals(sug.get("isRecommended")));
                List<Map<String, Object>> actions = (List<Map<String, Object>>) sug.get("actions");
                if (actions != null && !actions.isEmpty()) {
                    List<Map<String, Object>> actModels = new ArrayList<>();
                    for (Map<String, Object> act : actions) {
                        Map<String, Object> actModel = new HashMap<>();
                        actModel.put("type", engine.escapeCqlString(engine.getStr(act, "type", "create")));
                        String desc = engine.getStr(act, "description", "");
                        actModel.put("description", !desc.isEmpty() ? engine.escapeCqlString(desc) : "");
                        actModels.add(actModel);
                    }
                    sugModel.put("actions", actModels);
                } else {
                    sugModel.put("actions", List.of());
                }
                sugModels.add(sugModel);
            }
            model.put("suggestions", sugModels);
        } else {
            model.put("suggestions", List.of());
        }

        return templateEngine.render("fragments/cds-card.ftl", model);
    }

    @SuppressWarnings("unchecked")
    private String buildErrorStatement(Map<String, Object> errorStatement) {
        List<Map<String, Object>> rawClauses = (List<Map<String, Object>>) errorStatement.get("ifThenClauses");
        String elseClause = engine.getStr(errorStatement, "elseClause", null);

        if (rawClauses == null || rawClauses.isEmpty())
            return null;

        List<Map<String, Object>> clauseModels = new ArrayList<>();
        for (Map<String, Object> clause : rawClauses) {
            Map<String, Object> condition = (Map<String, Object>) clause.get("ifCondition");
            String thenClause = engine.escapeCqlString(engine.getStr(clause, "thenClause", "null"));
            String condValue = condition != null ? engine.getStr(condition, "value", "true") : "true";
            clauseModels.add(Map.of("condition", mapErrorConditionToCql(condValue), "thenClause", thenClause));
        }

        Map<String, Object> model = new HashMap<>();
        model.put("clauses", clauseModels);
        model.put("elseClause", elseClause != null && !elseClause.isEmpty() ? engine.escapeCqlString(elseClause) : "");
        return templateEngine.render("fragments/error-statement.ftl", model);
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
