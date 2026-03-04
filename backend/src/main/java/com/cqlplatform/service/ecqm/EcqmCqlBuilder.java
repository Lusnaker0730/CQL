package com.cqlplatform.service.ecqm;

import com.cqlplatform.model.authoring.AuthoringConstants;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.model.ecqm.EcqmConstants;
import com.cqlplatform.service.authoring.ExpressionCqlEngine;
import com.cqlplatform.service.authoring.ExpressionCqlEngine.BuildContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generates eCQM-structured CQL from visual expression trees.
 * Supports proportion, ratio, continuous-variable, and cohort scoring types
 * per CMS 2026 eCQM Logic and Implementation Guidance v9.0.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EcqmCqlBuilder {

    private static final String FHIR_HELPERS = "FHIRHelpers";

    private final ExpressionCqlEngine engine;

    /**
     * Build eCQM CQL from population groups and related data.
     */
    @SuppressWarnings("unchecked")
    public CqlBuildResult buildEcqmCql(
            String name, String version, String scoringType,
            String populationBasis,
            List<Map<String, Object>> populationGroups,
            List<Map<String, Object>> baseElements,
            List<Map<String, Object>> parameters,
            List<Map<String, Object>> supplementalData,
            List<Map<String, Object>> stratifiers,
            String fhirVersion) {

        BuildContext ctx = new BuildContext(baseElements);

        String resolvedFhirVersion = engine.resolveFhirVersion(fhirVersion != null ? fhirVersion : "R4");
        String resolvedHelpersVersion = engine.resolveHelpersVersion(fhirVersion != null ? fhirVersion : "R4");
        boolean isEpisodeBased = populationBasis != null && !"boolean".equalsIgnoreCase(populationBasis);

        StringBuilder cql = new StringBuilder();
        Set<String> valueSets = new LinkedHashSet<>();
        Set<String> codeSystems = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();

        includes.add(String.format("include %s version '%s' called FHIRHelpers", FHIR_HELPERS, resolvedHelpersVersion));

        // Validate scoring type
        List<String> validationErrors = validateScoringPopulations(scoringType, populationGroups);
        for (String err : validationErrors) {
            ctx.warn(err);
        }

        // Collect declarations from all trees
        collectAllDeclarations(populationGroups, baseElements, supplementalData, stratifiers,
                valueSets, codeSystems, codes, includes);

        // ── Library header ────────────────────────────────────────────────
        String safeName = name.replaceAll("[^a-zA-Z0-9_]", "_");
        cql.append(String.format("library %s version '%s'%n", safeName,
                version != null ? version : AuthoringConstants.DEFAULT_VERSION));
        cql.append("\n");
        cql.append(String.format("using FHIR version '%s'%n%n", resolvedFhirVersion));

        engine.emitIncludes(cql, includes);
        engine.emitValueSets(cql, valueSets);
        engine.emitCodeSystems(cql, codeSystems);
        engine.emitCodes(cql, codes);

        // ── Measurement Period parameter ──────────────────────────────────
        cql.append("parameter \"Measurement Period\" Interval<DateTime>\n");
        cql.append("  default Interval[@2025-01-01T00:00:00.0, @2025-12-31T23:59:59.999]\n\n");

        // ── User parameters ───────────────────────────────────────────────
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

        // ── Context ───────────────────────────────────────────────────────
        cql.append("context Patient\n\n");

        // ── Base Elements ─────────────────────────────────────────────────
        if (baseElements != null) {
            for (Map<String, Object> be : baseElements) {
                String beName = engine.getStr(be, "name", "BaseElement");
                String beExpr = engine.buildExpression(be, ctx);
                cql.append(String.format("define \"%s\":%n  %s%n%n", beName, beExpr));
            }
        }

        // ── Population Groups ─────────────────────────────────────────────
        boolean multiGroup = populationGroups != null && populationGroups.size() > 1;

        if (populationGroups != null) {
            for (int g = 0; g < populationGroups.size(); g++) {
                Map<String, Object> group = populationGroups.get(g);
                String suffix = multiGroup ? " " + (g + 1) : "";
                Map<String, Object> populations = (Map<String, Object>) group.get("populations");

                if (populations == null) {
                    ctx.warn(String.format("Population group %d has no populations defined", g + 1));
                    continue;
                }

                // Check for dual IP (ratio only)
                Map<String, Object> ipDenom = (Map<String, Object>) group.get("initialPopulationDenom");
                Map<String, Object> ipNumer = (Map<String, Object>) group.get("initialPopulationNumer");
                boolean dualIp = EcqmConstants.SCORING_RATIO.equals(scoringType)
                        && ipDenom != null && ipNumer != null;

                if (dualIp) {
                    emitDualInitialPopulations(cql, ipDenom, ipNumer, suffix, ctx);
                }

                // Emit population defines in canonical order
                for (String popKey : EcqmConstants.ALL_POPULATION_KEYS) {
                    // Skip IP if dual IP mode
                    if (dualIp && "initial-population".equals(popKey)) continue;

                    Object popTree = populations.get(popKey);
                    if (popTree == null) continue;

                    String defineName = EcqmConstants.POPULATION_KEY_TO_DEFINE.get(popKey);
                    if (defineName == null) continue;

                    emitPopulationDefine(cql, defineName + suffix, (Map<String, Object>) popTree, ctx);
                }

                // Observations (continuous variable)
                List<Map<String, Object>> observations = (List<Map<String, Object>>) group.get("observations");
                if (observations != null) {
                    for (Map<String, Object> obs : observations) {
                        emitObservationFunction(cql, obs, suffix, ctx, isEpisodeBased, populationBasis);
                    }
                }

                // Group-level stratifiers
                List<Map<String, Object>> groupStratifiers = (List<Map<String, Object>>) group.get("stratifiers");
                if (groupStratifiers != null) {
                    emitGroupStratifiers(cql, groupStratifiers, suffix, ctx, dualIp);
                }
            }
        }

        // ── Top-level stratifiers ─────────────────────────────────────────
        if (stratifiers != null && !stratifiers.isEmpty()) {
            for (Map<String, Object> strat : stratifiers) {
                String stratId = engine.getStr(strat, "stratifierId", "strat");
                String desc = engine.getStr(strat, "description", "");
                Map<String, Object> criteria = (Map<String, Object>) strat.get("criteria");
                if (criteria != null) {
                    String stratExpr = engine.buildConjunctionExpression(criteria, ctx);
                    if (!desc.isEmpty()) {
                        cql.append(String.format("// %s%n", engine.escapeCqlString(desc)));
                    }
                    cql.append(String.format("define \"Stratifier %s\":%n  %s%n%n", stratId, stratExpr));
                }
            }
        }

        // ── Supplemental Data Elements ────────────────────────────────────
        emitSupplementalDataDefines(cql, supplementalData, valueSets);

        return new CqlBuildResult(cql.toString(), List.copyOf(ctx.warnings));
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void emitPopulationDefine(StringBuilder cql, String defineName,
            Map<String, Object> tree, BuildContext ctx) {
        String expr = engine.buildConjunctionExpression(tree, ctx);
        if ("null".equals(expr)) {
            return; // empty tree → skip
        }
        cql.append(String.format("define \"%s\":%n  %s%n%n", defineName, expr));
    }

    private void emitDualInitialPopulations(StringBuilder cql,
            Map<String, Object> ipDenom, Map<String, Object> ipNumer,
            String suffix, BuildContext ctx) {
        String denomExpr = engine.buildConjunctionExpression(ipDenom, ctx);
        String numerExpr = engine.buildConjunctionExpression(ipNumer, ctx);

        if (!"null".equals(denomExpr)) {
            cql.append(String.format("define \"%s%s\":%n  %s%n%n",
                    EcqmConstants.INITIAL_POPULATION_1, suffix, denomExpr));
        }
        if (!"null".equals(numerExpr)) {
            cql.append(String.format("define \"%s%s\":%n  %s%n%n",
                    EcqmConstants.INITIAL_POPULATION_2, suffix, numerExpr));
        }
    }

    @SuppressWarnings("unchecked")
    private void emitObservationFunction(StringBuilder cql, Map<String, Object> obs,
            String suffix, BuildContext ctx,
            boolean isEpisodeBased, String populationBasis) {
        String obsId = engine.getStr(obs, "observationId", "obs");
        String aggregateMethod = engine.getStr(obs, "aggregateMethod", "Count");
        Map<String, Object> criteria = (Map<String, Object>) obs.get("criteria");

        String funcName = EcqmConstants.MEASURE_OBSERVATION + suffix;
        String paramType = isEpisodeBased ? populationBasis : "Patient";
        String paramName = isEpisodeBased ? populationBasis : "Patient";

        cql.append(String.format("// Aggregate Method: %s%n", engine.escapeCqlString(aggregateMethod)));
        cql.append(String.format("define function \"%s\"(%s \"%s\"):%n", funcName, paramName, paramType));

        if (criteria != null) {
            String expr = engine.buildConjunctionExpression(criteria, ctx);
            if (!"null".equals(expr)) {
                cql.append(String.format("  %s%n%n", expr));
            } else {
                cql.append("  1\n\n");
            }
        } else {
            cql.append("  1\n\n");
        }
    }

    private void emitGroupStratifiers(StringBuilder cql,
            List<Map<String, Object>> groupStratifiers, String suffix,
            BuildContext ctx, boolean dualIp) {
        if (dualIp) {
            ctx.warn("Stratifiers are not allowed when using dual Initial Populations (ratio). Skipping.");
            return;
        }

        for (Map<String, Object> strat : groupStratifiers) {
            emitStratifier(cql, strat, suffix, ctx);
        }
    }

    @SuppressWarnings("unchecked")
    private void emitStratifier(StringBuilder cql, Map<String, Object> strat,
            String suffix, BuildContext ctx) {
        String stratId = engine.getStr(strat, "stratifierId", "strat");
        String desc = engine.getStr(strat, "description", "");
        Map<String, Object> criteria = (Map<String, Object>) strat.get("criteria");
        if (criteria != null) {
            String expr = engine.buildConjunctionExpression(criteria, ctx);
            if (!"null".equals(expr)) {
                if (!desc.isEmpty()) {
                    cql.append(String.format("// %s%n", engine.escapeCqlString(desc)));
                }
                cql.append(String.format("define \"Stratifier %s%s\":%n  %s%n%n", stratId, suffix, expr));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void emitSupplementalDataDefines(StringBuilder cql,
            List<Map<String, Object>> supplementalData, Set<String> valueSets) {
        if (supplementalData == null || supplementalData.isEmpty()) return;

        for (Map<String, Object> sde : supplementalData) {
            String sdeName = engine.getStr(sde, "name", null);
            if (sdeName == null) continue;

            // Check if this is a standard SDE
            String oid = EcqmConstants.SDE_VALUE_SET_OIDS.get(sdeName);
            if (oid != null) {
                // Standard SDE — emit FHIR-based retrieval
                emitStandardSde(cql, sdeName, oid, valueSets);
            } else {
                // Custom SDE with expression tree
                Map<String, Object> criteria = (Map<String, Object>) sde.get("criteria");
                if (criteria != null) {
                    BuildContext sdeCtx = new BuildContext(null);
                    String expr = engine.buildConjunctionExpression(criteria, sdeCtx);
                    if (!"null".equals(expr)) {
                        cql.append(String.format("define \"%s\":%n  %s%n%n", sdeName, expr));
                    }
                }
            }
        }
    }

    private void emitStandardSde(StringBuilder cql, String sdeName, String oid, Set<String> valueSets) {
        valueSets.add(oid);
        switch (sdeName) {
            case EcqmConstants.SDE_ETHNICITY:
                cql.append(String.format("define \"%s\":%n", sdeName));
                cql.append("  SDE.\"SDE Ethnicity\"\n\n");
                break;
            case EcqmConstants.SDE_RACE:
                cql.append(String.format("define \"%s\":%n", sdeName));
                cql.append("  SDE.\"SDE Race\"\n\n");
                break;
            case EcqmConstants.SDE_SEX:
                cql.append(String.format("define \"%s\":%n", sdeName));
                cql.append("  Patient.gender\n\n");
                break;
            case EcqmConstants.SDE_PAYER:
                cql.append(String.format("define \"%s\":%n", sdeName));
                cql.append(String.format("  [Coverage: \"%s\"]%n%n", oid));
                break;
            default:
                cql.append(String.format("define \"%s\":%n  null%n%n", sdeName));
        }
    }

    @SuppressWarnings("unchecked")
    private void collectAllDeclarations(
            List<Map<String, Object>> populationGroups,
            List<Map<String, Object>> baseElements,
            List<Map<String, Object>> supplementalData,
            List<Map<String, Object>> stratifiers,
            Set<String> valueSets, Set<String> codeSystems,
            Set<String> codes, Set<String> includes) {

        // From base elements
        if (baseElements != null) {
            for (Map<String, Object> be : baseElements) {
                engine.collectDeclarations(be, valueSets, codeSystems, codes, includes);
            }
        }

        // From population groups
        if (populationGroups != null) {
            for (Map<String, Object> group : populationGroups) {
                Map<String, Object> populations = (Map<String, Object>) group.get("populations");
                if (populations != null) {
                    for (Object popTree : populations.values()) {
                        if (popTree instanceof Map) {
                            engine.collectDeclarations((Map<String, Object>) popTree,
                                    valueSets, codeSystems, codes, includes);
                        }
                    }
                }
                // Dual IP trees
                Map<String, Object> ipDenom = (Map<String, Object>) group.get("initialPopulationDenom");
                Map<String, Object> ipNumer = (Map<String, Object>) group.get("initialPopulationNumer");
                if (ipDenom != null) engine.collectDeclarations(ipDenom, valueSets, codeSystems, codes, includes);
                if (ipNumer != null) engine.collectDeclarations(ipNumer, valueSets, codeSystems, codes, includes);

                // Observations
                List<Map<String, Object>> observations = (List<Map<String, Object>>) group.get("observations");
                if (observations != null) {
                    for (Map<String, Object> obs : observations) {
                        Map<String, Object> criteria = (Map<String, Object>) obs.get("criteria");
                        if (criteria != null) {
                            engine.collectDeclarations(criteria, valueSets, codeSystems, codes, includes);
                        }
                    }
                }

                // Group stratifiers
                List<Map<String, Object>> groupStrats = (List<Map<String, Object>>) group.get("stratifiers");
                if (groupStrats != null) {
                    for (Map<String, Object> strat : groupStrats) {
                        Map<String, Object> criteria = (Map<String, Object>) strat.get("criteria");
                        if (criteria != null) {
                            engine.collectDeclarations(criteria, valueSets, codeSystems, codes, includes);
                        }
                    }
                }
            }
        }

        // From top-level stratifiers
        if (stratifiers != null) {
            for (Map<String, Object> strat : stratifiers) {
                Map<String, Object> criteria = (Map<String, Object>) strat.get("criteria");
                if (criteria != null) {
                    engine.collectDeclarations(criteria, valueSets, codeSystems, codes, includes);
                }
            }
        }

        // From supplemental data
        if (supplementalData != null) {
            for (Map<String, Object> sde : supplementalData) {
                Map<String, Object> criteria = (Map<String, Object>) sde.get("criteria");
                if (criteria != null) {
                    engine.collectDeclarations(criteria, valueSets, codeSystems, codes, includes);
                }
            }
        }
    }

    /**
     * Validate that required populations are present for the given scoring type.
     */
    @SuppressWarnings("unchecked")
    private List<String> validateScoringPopulations(String scoringType,
            List<Map<String, Object>> populationGroups) {
        List<String> errors = new ArrayList<>();
        if (scoringType == null) {
            errors.add("Scoring type is required");
            return errors;
        }

        List<String> required = EcqmConstants.REQUIRED_POPULATIONS.get(scoringType);
        if (required == null) {
            errors.add(String.format("Unknown scoring type: '%s'", scoringType));
            return errors;
        }

        if (populationGroups == null || populationGroups.isEmpty()) {
            errors.add("At least one population group is required");
            return errors;
        }

        for (int g = 0; g < populationGroups.size(); g++) {
            Map<String, Object> group = populationGroups.get(g);
            Map<String, Object> populations = (Map<String, Object>) group.get("populations");

            if (populations == null) {
                errors.add(String.format("Group %d: populations map is missing", g + 1));
                continue;
            }

            // Check for dual IP in ratio
            Map<String, Object> ipDenom = (Map<String, Object>) group.get("initialPopulationDenom");
            Map<String, Object> ipNumer = (Map<String, Object>) group.get("initialPopulationNumer");
            boolean dualIp = EcqmConstants.SCORING_RATIO.equals(scoringType)
                    && ipDenom != null && ipNumer != null;

            for (String reqPop : required) {
                // Skip IP check if dual IP
                if (dualIp && EcqmConstants.INITIAL_POPULATION.equals(reqPop)) continue;

                // For continuous variable, Measure Observation is checked separately
                if (EcqmConstants.MEASURE_OBSERVATION.equals(reqPop)) continue;

                String popKey = EcqmConstants.DEFINE_TO_POPULATION_KEY.get(reqPop);
                if (popKey == null) continue;

                Object popTree = populations.get(popKey);
                if (popTree == null) {
                    errors.add(String.format("Group %d: required population '%s' is missing for scoring type '%s'",
                            g + 1, reqPop, scoringType));
                }
            }

            // Validate continuous variable has observations
            if (EcqmConstants.SCORING_CONTINUOUS_VARIABLE.equals(scoringType)) {
                List<Map<String, Object>> observations = (List<Map<String, Object>>) group.get("observations");
                if (observations == null || observations.isEmpty()) {
                    errors.add(String.format("Group %d: continuous-variable scoring requires at least one observation", g + 1));
                }
            }

            // Validate dual IP consistency
            if (dualIp) {
                List<Map<String, Object>> groupStratifiers = (List<Map<String, Object>>) group.get("stratifiers");
                if (groupStratifiers != null && !groupStratifiers.isEmpty()) {
                    errors.add(String.format("Group %d: stratifiers are not allowed with dual Initial Populations", g + 1));
                }
            }
        }

        return errors;
    }
}
