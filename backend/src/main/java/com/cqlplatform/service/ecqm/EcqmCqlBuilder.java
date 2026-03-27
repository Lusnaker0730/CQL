package com.cqlplatform.service.ecqm;

import com.cqlplatform.model.authoring.AuthoringConstants;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.model.ecqm.EcqmConstants;
import com.cqlplatform.service.authoring.CqlTemplateEngine;
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
    private static final String C3F_LIBRARY = "CDSConnectCommonsForFHIRv401";
    private static final Set<String> VALID_DURATION_UNITS = Set.of(
            "years", "months", "weeks", "days", "hours", "minutes");

    private final ExpressionCqlEngine engine;
    private final CqlTemplateEngine templateEngine;

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

        BuildContext ctx = new BuildContext(baseElements, parameters);

        String resolvedFhirVersion = engine.resolveFhirVersion(fhirVersion != null ? fhirVersion : "R4");
        String resolvedHelpersVersion = engine.resolveHelpersVersion(fhirVersion != null ? fhirVersion : "R4");
        boolean isEpisodeBased = populationBasis != null && !"boolean".equalsIgnoreCase(populationBasis);

        // Validate scoring type
        List<String> validationErrors = validateScoringPopulations(scoringType, populationGroups);
        for (String err : validationErrors) {
            ctx.warn(err);
        }

        // ── Collect declarations ──────────────────────────────────────────
        Set<String> valueSets = new LinkedHashSet<>();
        Map<String, String> codeSystems = new LinkedHashMap<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<String> includes = new LinkedHashSet<>();

        includes.add(String.format("include %s version '%s' called FHIRHelpers", FHIR_HELPERS, resolvedHelpersVersion));
        includes.add(String.format("include %s version '2.1.0' called C3F", C3F_LIBRARY));

        collectAllDeclarations(populationGroups, baseElements, supplementalData, stratifiers,
                valueSets, codeSystems, codes, includes);

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

        List<Map<String, String>> codeSystemEntries = new ArrayList<>();
        for (var csEntry : codeSystems.entrySet()) {
            codeSystemEntries.add(Map.of(
                    "name", engine.escapeCqlIdentifier(csEntry.getValue()),
                    "id", engine.escapeCqlString(csEntry.getKey())));
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

        // Base Elements
        List<Map<String, String>> baseElementModels = new ArrayList<>();
        if (baseElements != null) {
            for (Map<String, Object> be : baseElements) {
                String beName = engine.escapeCqlIdentifier(engine.getStr(be, "name", "BaseElement"));
                String beExpr = engine.buildExpression(be, ctx);
                baseElementModels.add(Map.of("name", beName, "expression", beExpr));
            }
        }
        dataModel.put("baseElements", baseElementModels);

        // Population Groups — pre-render each group's CQL block
        List<String> groupBlocks = new ArrayList<>();
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

                StringBuilder block = new StringBuilder();

                // Dual IP (ratio only)
                Map<String, Object> ipDenom = (Map<String, Object>) group.get("initialPopulationDenom");
                Map<String, Object> ipNumer = (Map<String, Object>) group.get("initialPopulationNumer");
                boolean dualIp = EcqmConstants.SCORING_RATIO.equals(scoringType)
                        && ipDenom != null && ipNumer != null;

                if (dualIp) {
                    appendPopulationDefine(block, EcqmConstants.INITIAL_POPULATION_1 + suffix, ipDenom, ctx);
                    appendPopulationDefine(block, EcqmConstants.INITIAL_POPULATION_2 + suffix, ipNumer, ctx);
                }

                // Population defines in canonical order
                boolean isCvEpisode = EcqmConstants.SCORING_CONTINUOUS_VARIABLE.equals(scoringType) && isEpisodeBased;
                List<String> requiredPops = EcqmConstants.REQUIRED_POPULATIONS.getOrDefault(scoringType, List.of());
                for (String popKey : EcqmConstants.ALL_POPULATION_KEYS) {
                    if (dualIp && "initial-population".equals(popKey)) continue;
                    String defineName = EcqmConstants.POPULATION_KEY_TO_DEFINE.get(popKey);
                    if (defineName == null) continue;

                    Object popTree = populations.get(popKey);
                    boolean isEmpty = popTree == null || isEmptyTree((Map<String, Object>) popTree);

                    if (isEmpty) {
                        // Required populations with empty trees inherit from parent population
                        String parentDefine = EcqmConstants.POPULATION_PARENT.get(defineName);
                        if (parentDefine != null && requiredPops.contains(defineName)) {
                            block.append(String.format("define \"%s%s\":\n  \"%s%s\"\n\n",
                                    defineName, suffix, parentDefine, suffix));
                        }
                        continue;
                    }

                    // Episode-based CV: Measure Population should return resource list, not boolean
                    if (isCvEpisode && "measure-population".equals(popKey)) {
                        ctx.preserveListReturn = true;
                        ctx.episodeResourceType = populationBasis;
                    }
                    appendPopulationDefine(block, defineName + suffix, (Map<String, Object>) popTree, ctx);
                    ctx.preserveListReturn = false;
                    ctx.episodeResourceType = null;
                }

                // Observations (continuous variable)
                List<Map<String, Object>> observations = (List<Map<String, Object>>) group.get("observations");
                if (observations != null) {
                    for (Map<String, Object> obs : observations) {
                        appendObservationFunction(block, obs, suffix, ctx, isEpisodeBased, populationBasis);
                    }
                    if (!observations.isEmpty()) {
                        appendObservationWrapper(block, suffix, isEpisodeBased);
                    }
                }

                // Group-level stratifiers
                List<Map<String, Object>> groupStratifiers = (List<Map<String, Object>>) group.get("stratifiers");
                if (groupStratifiers != null && !groupStratifiers.isEmpty()) {
                    if (dualIp) {
                        ctx.warn("Stratifiers are not allowed when using dual Initial Populations (ratio). Skipping.");
                    } else {
                        for (Map<String, Object> strat : groupStratifiers) {
                            appendStratifier(block, strat, suffix, ctx);
                        }
                    }
                }

                if (block.length() > 0) {
                    groupBlocks.add(block.toString());
                }
            }
        }
        dataModel.put("groupBlocks", groupBlocks);

        // Top-level stratifiers
        List<Map<String, String>> topStratModels = new ArrayList<>();
        if (stratifiers != null) {
            for (Map<String, Object> strat : stratifiers) {
                String stratId = engine.escapeCqlIdentifier(engine.getStr(strat, "stratifierId", "strat"));
                String desc = engine.getStr(strat, "description", "");
                Map<String, Object> criteria = (Map<String, Object>) strat.get("criteria");
                if (criteria != null) {
                    String stratExpr = engine.buildConjunctionExpression(criteria, ctx);
                    Map<String, String> sm = new HashMap<>();
                    sm.put("id", stratId);
                    sm.put("description", engine.escapeCqlIdentifier(desc));
                    sm.put("expression", stratExpr);
                    topStratModels.add(sm);
                }
            }
        }
        dataModel.put("topStratifiers", topStratModels);

        // Supplemental Data Elements
        List<String> supplementalDefines = new ArrayList<>();
        if (supplementalData != null) {
            for (Map<String, Object> sde : supplementalData) {
                String sdeName = engine.getStr(sde, "name", null);
                if (sdeName == null) continue;
                String oid = EcqmConstants.SDE_VALUE_SET_OIDS.get(sdeName);
                if (oid != null) {
                    valueSets.add(oid);
                    supplementalDefines.add(buildStandardSde(sdeName, oid));
                } else {
                    Map<String, Object> criteria = (Map<String, Object>) sde.get("criteria");
                    if (criteria != null) {
                        String expr = engine.buildConjunctionExpression(criteria, ctx);
                        if (!"null".equals(expr)) {
                            supplementalDefines.add(String.format("define \"%s\":\n  %s\n",
                                    engine.escapeCqlIdentifier(sdeName), expr));
                        }
                    }
                }
            }
        }
        dataModel.put("supplementalDefines", supplementalDefines);

        // ── Render ───────────────────────────────────────────────────────
        String cql = templateEngine.render("ecqm-artifact.ftl", dataModel);
        return new CqlBuildResult(cql, List.copyOf(ctx.warnings));
    }

    // ── Private helpers ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private boolean isEmptyTree(Map<String, Object> tree) {
        if (tree == null) return true;
        List<Map<String, Object>> children = (List<Map<String, Object>>) tree.get("childInstances");
        return children == null || children.isEmpty();
    }

    private void appendPopulationDefine(StringBuilder block, String defineName,
            Map<String, Object> tree, BuildContext ctx) {
        String expr = engine.buildConjunctionExpression(tree, ctx);
        if (!"null".equals(expr)) {
            block.append(String.format("define \"%s\":\n  %s\n\n", defineName, expr));
        }
    }

    @SuppressWarnings("unchecked")
    private void appendObservationFunction(StringBuilder block, Map<String, Object> obs,
            String suffix, BuildContext ctx,
            boolean isEpisodeBased, String populationBasis) {
        String aggregateMethod = engine.getStr(obs, "aggregateMethod", "Count");
        // Default to "criteria" for backward compatibility with observations saved before observationType was added
        String observationType = engine.getStr(obs, "observationType", "criteria");

        String funcName = EcqmConstants.MEASURE_OBSERVATION + suffix;
        String paramType = isEpisodeBased ? populationBasis : "Patient";
        String paramName = isEpisodeBased ? populationBasis : "Patient";

        // Sanitize for CQL comment: strip newlines to prevent injection
        String safeAggMethod = aggregateMethod.replace("\n", " ").replace("\r", " ");
        block.append(String.format("// Aggregate Method: %s\n", safeAggMethod));
        block.append(String.format("define function \"%s\"(%s \"%s\"):\n", funcName, paramName, paramType));

        switch (observationType) {
            case "duration" -> {
                String unit = engine.getStr(obs, "observationUnit", "days");
                String property = engine.getStr(obs, "observationProperty", "period");
                if (!VALID_DURATION_UNITS.contains(unit)) {
                    ctx.warn(String.format("Invalid duration unit '%s', defaulting to 'days'", unit));
                    unit = "days";
                }
                String safeProperty = property.replaceAll("[^a-zA-Z0-9.]", "");
                block.append(String.format("  duration in %s of %s.%s\n\n", unit, paramName, safeProperty));
            }
            case "quantity" -> {
                String property = engine.getStr(obs, "observationProperty", "value");
                String safeProperty = property.replaceAll("[^a-zA-Z0-9.]", "");
                block.append(String.format("  (%s.%s as Quantity).value\n\n", paramName, safeProperty));
            }
            default -> {
                Map<String, Object> criteria = (Map<String, Object>) obs.get("criteria");
                if (criteria != null) {
                    String expr = engine.buildConjunctionExpression(criteria, ctx);
                    block.append(!"null".equals(expr) ? String.format("  %s\n\n", expr) : "  1\n\n");
                } else {
                    block.append("  1\n\n");
                }
            }
        }
    }

    private void appendObservationWrapper(StringBuilder block, String suffix, boolean isEpisodeBased) {
        String funcName = EcqmConstants.MEASURE_OBSERVATION + suffix;
        String mpName = EcqmConstants.MEASURE_POPULATION + suffix;
        if (isEpisodeBased) {
            block.append(String.format("define \"%s%s\":\n  (\"%s\") MP return \"%s\"(MP)\n\n",
                    "Measure Observation Values", suffix, mpName, funcName));
        } else {
            block.append(String.format("define \"%s%s\":\n  if \"%s\" then \"%s\"(Patient) else null\n\n",
                    "Measure Observation Value", suffix, mpName, funcName));
        }
    }

    @SuppressWarnings("unchecked")
    private void appendStratifier(StringBuilder block, Map<String, Object> strat,
            String suffix, BuildContext ctx) {
        String stratId = engine.escapeCqlIdentifier(engine.getStr(strat, "stratifierId", "strat"));
        String desc = engine.getStr(strat, "description", "");
        Map<String, Object> criteria = (Map<String, Object>) strat.get("criteria");
        if (criteria != null) {
            String expr = engine.buildConjunctionExpression(criteria, ctx);
            if (!"null".equals(expr)) {
                if (!desc.isEmpty()) {
                    // Sanitize description for CQL comment: strip newlines to prevent injection
                    String safeDesc = desc.replace("\n", " ").replace("\r", " ");
                    block.append(String.format("// %s\n", safeDesc));
                }
                block.append(String.format("define \"Stratifier %s%s\":\n  %s\n\n", stratId, suffix, expr));
            }
        }
    }

    private String buildStandardSde(String sdeName, String oid) {
        String sdeType;
        if (EcqmConstants.SDE_ETHNICITY.equals(sdeName)) sdeType = "ethnicity";
        else if (EcqmConstants.SDE_RACE.equals(sdeName)) sdeType = "race";
        else if (EcqmConstants.SDE_SEX.equals(sdeName)) sdeType = "sex";
        else if (EcqmConstants.SDE_PAYER.equals(sdeName)) sdeType = "payer";
        else sdeType = "unknown";

        return templateEngine.render("ecqm/standard-sde.ftl",
                Map.of("sdeName", sdeName, "sdeType", sdeType, "oid", oid));
    }

    @SuppressWarnings("unchecked")
    private void collectAllDeclarations(
            List<Map<String, Object>> populationGroups,
            List<Map<String, Object>> baseElements,
            List<Map<String, Object>> supplementalData,
            List<Map<String, Object>> stratifiers,
            Set<String> valueSets, Map<String, String> codeSystems,
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
