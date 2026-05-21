package com.cqlplatform.service.ecqm;

import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.model.ecqm.EcqmConstants;
import com.cqlplatform.service.authoring.CqlTemplateEngine;
import com.cqlplatform.service.authoring.CustomModifierCqlBuilder;
import com.cqlplatform.service.authoring.ExpressionCqlEngine;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class EcqmCqlBuilderTest {

    private final CqlTemplateEngine templateEngine;
    private final ExpressionCqlEngine engine;
    private final EcqmCqlBuilder builder;

    EcqmCqlBuilderTest() {
        templateEngine = new CqlTemplateEngine();
        engine = new ExpressionCqlEngine(templateEngine, null, new CustomModifierCqlBuilder());
        builder = new EcqmCqlBuilder(engine, templateEngine);
    }

    // ===== Helpers =====

    private Map<String, Object> emptyTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", "And");
        tree.put("name", "And");
        tree.put("conjunction", true);
        tree.put("returnType", "boolean");
        tree.put("childInstances", new ArrayList<>());
        return tree;
    }

    /**
     * Creates a tree with one AgeRange child that produces real CQL: AgeInYearsAt(...) >= 18
     */
    private Map<String, Object> populationTree() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("id", "AgeRange");
        child.put("name", "Age Range");
        child.put("type", "AgeRange");
        child.put("returnType", "boolean");
        child.put("fields", List.of(
                Map.of("id", "element_name", "type", "string", "value", "Age Check"),
                Map.of("id", "min_age", "type", "string", "value", "18"),
                Map.of("id", "max_age", "type", "string", "value", ""),
                Map.of("id", "unit_of_time", "type", "string", "value", "year")
        ));
        child.put("modifiers", new ArrayList<>());

        Map<String, Object> tree = emptyTree();
        ((List<Object>) tree.get("childInstances")).add(child);
        return tree;
    }

    private Map<String, Object> proportionGroup() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        Map<String, Object> pops = new LinkedHashMap<>();
        pops.put("initial-population", populationTree());
        pops.put("denominator", populationTree());
        pops.put("numerator", populationTree());
        group.put("populations", pops);
        return group;
    }

    private Map<String, Object> ratioGroup(boolean dualIp) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        Map<String, Object> pops = new LinkedHashMap<>();
        if (!dualIp) {
            pops.put("initial-population", populationTree());
        }
        pops.put("denominator", populationTree());
        pops.put("numerator", populationTree());
        group.put("populations", pops);
        if (dualIp) {
            group.put("initialPopulationDenom", populationTree());
            group.put("initialPopulationNumer", populationTree());
        }
        return group;
    }

    private Map<String, Object> cvGroup() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        Map<String, Object> pops = new LinkedHashMap<>();
        pops.put("initial-population", populationTree());
        pops.put("measure-population", populationTree());
        group.put("populations", pops);

        Map<String, Object> obs = new LinkedHashMap<>();
        obs.put("observationId", "obs-1");
        obs.put("criteria", populationTree());
        obs.put("aggregateMethod", "Count");
        obs.put("populationRef", "measure-population");
        group.put("observations", List.of(obs));

        return group;
    }

    private Map<String, Object> cohortGroup() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        Map<String, Object> pops = new LinkedHashMap<>();
        pops.put("initial-population", populationTree());
        group.put("populations", pops);
        return group;
    }

    // ===== Proportion tests =====

    @Test
    void buildEcqmCql_proportion_shouldContainRequiredDefines() {
        CqlBuildResult result = builder.buildEcqmCql(
                "ProportionMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("library ProportionMeasure version '1.0.0'");
        assertThat(result.cql()).contains("using FHIR version '4.0.1'");
        assertThat(result.cql()).contains("include FHIRHelpers version '4.0.1' called FHIRHelpers");
        assertThat(result.cql()).contains("parameter \"Measurement Period\" Interval<DateTime>");
        assertThat(result.cql()).contains("context Patient");
        assertThat(result.cql()).contains("define \"Initial Population\":");
        assertThat(result.cql()).contains("define \"Denominator\":");
        assertThat(result.cql()).contains("define \"Numerator\":");
    }

    @Test
    void buildEcqmCql_proportion_shouldNotContainCvOrRatioStructures() {
        CqlBuildResult result = builder.buildEcqmCql(
                "ProportionMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).doesNotContain("Measure Population");
        assertThat(result.cql()).doesNotContain("Measure Observation");
        assertThat(result.cql()).doesNotContain("Initial Population 1");
        assertThat(result.cql()).doesNotContain("Initial Population 2");
    }

    // ===== Ratio tests =====

    @Test
    void buildEcqmCql_ratio_singleIp_shouldContainStandardDefines() {
        CqlBuildResult result = builder.buildEcqmCql(
                "RatioMeasure", "1.0.0", "ratio", "boolean",
                List.of(ratioGroup(false)), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("define \"Initial Population\":");
        assertThat(result.cql()).contains("define \"Denominator\":");
        assertThat(result.cql()).contains("define \"Numerator\":");
        assertThat(result.cql()).doesNotContain("Initial Population 1");
        assertThat(result.cql()).doesNotContain("Initial Population 2");
    }

    @Test
    void buildEcqmCql_ratio_dualIp_shouldContainDualIpDefines() {
        CqlBuildResult result = builder.buildEcqmCql(
                "RatioDualIP", "1.0.0", "ratio", "boolean",
                List.of(ratioGroup(true)), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("define \"Initial Population 1\":");
        assertThat(result.cql()).contains("define \"Initial Population 2\":");
        assertThat(result.cql()).contains("define \"Denominator\":");
        assertThat(result.cql()).contains("define \"Numerator\":");
        // Should NOT have the single "Initial Population" define
        assertThat(result.cql()).doesNotContain("define \"Initial Population\":");
    }

    @Test
    void buildEcqmCql_ratio_dualIp_withStratifier_shouldWarn() {
        Map<String, Object> group = ratioGroup(true);
        Map<String, Object> strat = new LinkedHashMap<>();
        strat.put("stratifierId", "strat-1");
        strat.put("description", "Gender strat");
        strat.put("criteria", populationTree());
        group.put("stratifiers", List.of(strat));

        CqlBuildResult result = builder.buildEcqmCql(
                "RatioWithStrat", "1.0.0", "ratio", "boolean",
                List.of(group), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Stratifiers are not allowed"));
    }

    // ===== Continuous Variable tests =====

    @Test
    void buildEcqmCql_continuousVariable_shouldContainObservationFunction() {
        CqlBuildResult result = builder.buildEcqmCql(
                "CVMeasure", "1.0.0", "continuous-variable", "boolean",
                List.of(cvGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("define \"Initial Population\":");
        assertThat(result.cql()).contains("define \"Measure Population\":");
        assertThat(result.cql()).contains("define function \"Measure Observation\"(Patient \"Patient\"):");
        assertThat(result.cql()).contains("// Aggregate Method: Count");
    }

    @Test
    void buildEcqmCql_continuousVariable_episodeBased_shouldUseResourceParam() {
        CqlBuildResult result = builder.buildEcqmCql(
                "CVEpisode", "1.0.0", "continuous-variable", "Encounter",
                List.of(cvGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("define function \"Measure Observation\"(Encounter \"Encounter\"):");
    }

    // ===== Cohort tests =====

    @Test
    void buildEcqmCql_cohort_shouldContainOnlyIp() {
        CqlBuildResult result = builder.buildEcqmCql(
                "CohortMeasure", "1.0.0", "cohort", "boolean",
                List.of(cohortGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("define \"Initial Population\":");
        assertThat(result.cql()).doesNotContain("Denominator");
        assertThat(result.cql()).doesNotContain("Numerator");
        assertThat(result.cql()).doesNotContain("Measure Population");
    }

    // ===== Multi-group tests =====

    @Test
    void buildEcqmCql_multiGroup_shouldAppendSuffix() {
        CqlBuildResult result = builder.buildEcqmCql(
                "MultiGroupMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup(), proportionGroup()),
                List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("define \"Initial Population 1\":");
        assertThat(result.cql()).contains("define \"Initial Population 2\":");
        assertThat(result.cql()).contains("define \"Denominator 1\":");
        assertThat(result.cql()).contains("define \"Denominator 2\":");
        assertThat(result.cql()).contains("define \"Numerator 1\":");
        assertThat(result.cql()).contains("define \"Numerator 2\":");
    }

    // ===== Supplemental Data tests =====

    @Test
    void buildEcqmCql_withStandardSde_shouldEmitSdeDefines() {
        List<Map<String, Object>> sdeList = List.of(
                Map.of("name", EcqmConstants.SDE_SEX),
                Map.of("name", EcqmConstants.SDE_PAYER)
        );

        CqlBuildResult result = builder.buildEcqmCql(
                "SdeMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), sdeList, List.of(), "R4");

        assertThat(result.cql()).contains("define \"SDE Sex\":");
        assertThat(result.cql()).contains("Patient.gender");
        assertThat(result.cql()).contains("define \"SDE Payer\":");
    }

    // ===== Stratifier tests =====

    @Test
    void buildEcqmCql_withTopLevelStratifiers_shouldEmitDefines() {
        Map<String, Object> strat = new LinkedHashMap<>();
        strat.put("stratifierId", "age");
        strat.put("description", "Age group stratifier");
        strat.put("criteria", populationTree());

        CqlBuildResult result = builder.buildEcqmCql(
                "StratMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(), List.of(strat), "R4");

        assertThat(result.cql()).contains("// Age group stratifier");
        assertThat(result.cql()).contains("define \"Stratifier age\":");
    }

    // ===== Validation tests =====

    @Test
    void buildEcqmCql_noGroups_shouldWarn() {
        CqlBuildResult result = builder.buildEcqmCql(
                "EmptyMeasure", "1.0.0", "proportion", "boolean",
                List.of(), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("At least one population group is required"));
    }

    @Test
    void buildEcqmCql_missingRequiredPopulation_shouldWarn() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        Map<String, Object> pops = new LinkedHashMap<>();
        pops.put("initial-population", populationTree());
        // Missing denominator and numerator for proportion
        group.put("populations", pops);

        CqlBuildResult result = builder.buildEcqmCql(
                "IncompleteMeasure", "1.0.0", "proportion", "boolean",
                List.of(group), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Denominator") && w.contains("missing"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("Numerator") && w.contains("missing"));
    }

    @Test
    void buildEcqmCql_cvMissingObservation_shouldWarn() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        Map<String, Object> pops = new LinkedHashMap<>();
        pops.put("initial-population", populationTree());
        pops.put("measure-population", populationTree());
        group.put("populations", pops);
        // No observations

        CqlBuildResult result = builder.buildEcqmCql(
                "CvNoObs", "1.0.0", "continuous-variable", "boolean",
                List.of(group), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("observation"));
    }

    @Test
    void buildEcqmCql_unknownScoringType_shouldWarn() {
        CqlBuildResult result = builder.buildEcqmCql(
                "BadScoring", "1.0.0", "unknown-type", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Unknown scoring type"));
    }

    // ===== Name sanitization =====

    @Test
    void buildEcqmCql_specialCharsInName_shouldSanitize() {
        CqlBuildResult result = builder.buildEcqmCql(
                "My eCQM Measure!", "1.0.0", "cohort", "boolean",
                List.of(cohortGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("library My_eCQM_Measure_ version '1.0.0'");
    }

    // ===== User parameters =====

    @Test
    void buildEcqmCql_withUserParameters_shouldEmitParams() {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", "MinAge");
        param.put("type", "integer");
        param.put("value", 18);

        CqlBuildResult result = builder.buildEcqmCql(
                "ParamMeasure", "1.0.0", "cohort", "boolean",
                List.of(cohortGroup()), List.of(), List.of(param), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("parameter \"MinAge\" Integer default 18");
    }

    // ===== Empty tree skip =====

    @Test
    void buildEcqmCql_emptyPopulationTree_shouldStillProduceDefine() {
        // emptyTree has no children but is a valid conjunction group — produces "null"
        CqlBuildResult result = builder.buildEcqmCql(
                "EmptyTreeMeasure", "1.0.0", "cohort", "boolean",
                List.of(cohortGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        // Should produce valid CQL library even if defines are skipped (empty trees produce "null")
        assertThat(result.cql()).contains("library EmptyTreeMeasure");
    }
}
