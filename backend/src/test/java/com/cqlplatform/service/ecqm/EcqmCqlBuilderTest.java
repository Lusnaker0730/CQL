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

    // PAT-233 — value stratifiers: the define returns the stratum itself.

    /** The FreeMarker templates carry the working copy's line endings; compare on LF. */
    private static String lf(CqlBuildResult result) {
        return result.cql().replace("\r\n", "\n");
    }

    private static Map<String, Object> valueStratifier(String id, String source, List<Map<String, Object>> bands) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("source", source);
        if (bands != null) value.put("bands", bands);
        Map<String, Object> strat = new LinkedHashMap<>();
        strat.put("stratifierId", id);
        strat.put("kind", "value");
        strat.put("value", value);
        return strat;
    }

    private static Map<String, Object> band(String label, Object min, Object max) {
        Map<String, Object> band = new LinkedHashMap<>();
        band.put("label", label);
        if (min != null) band.put("min", min);
        if (max != null) band.put("max", max);
        return band;
    }

    @Test
    void valueStratifier_gender_returnsThePatientGenderString() {
        CqlBuildResult result = builder.buildEcqmCql(
                "StratMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(),
                List.of(valueStratifier("sex", "gender", null)), "R4");

        assertThat(lf(result)).contains("define \"Stratifier sex\":\n  Patient.gender.value");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void valueStratifier_ageBands_isACaseOverTheMeasurementPeriodAge() {
        List<Map<String, Object>> bands = List.of(
                band("18-49", 18, 49), band("50-64", "50", "64"), band("65+", 65, null), band("child", null, 17));
        CqlBuildResult result = builder.buildEcqmCql(
                "StratMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(),
                List.of(valueStratifier("age", "ageBands", bands)), "R4");

        String age = "AgeInYearsAt(end of \"Measurement Period\")";
        assertThat(lf(result)).contains("define \"Stratifier age\":\n  case\n"
                + "    when " + age + " >= 18 and " + age + " <= 49 then '18-49'\n"
                + "    when " + age + " >= 50 and " + age + " <= 64 then '50-64'\n"
                + "    when " + age + " >= 65 then '65+'\n"
                + "    when " + age + " <= 17 then 'child'\n"
                + "    else null\n  end");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void valueStratifier_isAlsoEmittedForAGroupLevelStratifier_withTheGroupSuffix() {
        Map<String, Object> group1 = proportionGroup();
        group1.put("stratifiers", List.of(valueStratifier("sex", "gender", null)));
        CqlBuildResult result = builder.buildEcqmCql(
                "StratMeasure", "1.0.0", "proportion", "boolean",
                List.of(group1, proportionGroup()), List.of(), List.of(), List.of(), List.of(), "R4");

        assertThat(lf(result)).contains("define \"Stratifier sex 1\":\n  Patient.gender.value");
    }

    // PAT-235 — a multi-component stratifier is one define per component, "Stratifier <id> <code>".
    @Test
    void componentStratifier_emitsOneDefinePerComponent_andDropsTheWholeStratifierOnABadComponent() {
        Map<String, Object> sexByAge = new LinkedHashMap<>();
        sexByAge.put("stratifierId", "sex-age");
        sexByAge.put("description", "Sex by age band");
        sexByAge.put("components", List.of(
                Map.of("code", "sex", "kind", "value", "value", Map.of("source", "gender")),
                Map.of("code", "age", "kind", "value", "value", Map.of("source", "ageBands", "bands", List.of(band("65+", 65, null)))),
                Map.of("code", "elderly", "criteria", populationTree())));
        Map<String, Object> group1 = proportionGroup();
        group1.put("stratifiers", List.of(sexByAge));

        CqlBuildResult top = builder.buildEcqmCql("M", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(), List.of(sexByAge), "R4");
        assertThat(lf(top)).contains("// Sex by age band")
                .contains("define \"Stratifier sex-age sex\":\n  Patient.gender.value")
                .contains("define \"Stratifier sex-age age\":\n  case\n")
                .contains("define \"Stratifier sex-age elderly\":\n  ")
                .doesNotContain("define \"Stratifier sex-age\":");
        assertThat(top.warnings()).isEmpty();

        CqlBuildResult grouped = builder.buildEcqmCql("M", "1.0.0", "proportion", "boolean",
                List.of(group1, proportionGroup()), List.of(), List.of(), List.of(), List.of(), "R4");
        assertThat(lf(grouped)).contains("define \"Stratifier sex-age sex 1\":").contains("define \"Stratifier sex-age age 1\":");

        for (Map<String, Object> bad : List.of(
                Map.of("code", "a<b", "kind", "value", "value", Map.of("source", "gender")),   // code not plain
                Map.of("code", "sex", "kind", "value", "value", Map.of("source", "gender")),   // duplicate code
                Map.of("code", "zip", "kind", "value", "value", Map.of("source", "postal")))) { // unbuildable
            Map<String, Object> strat = new LinkedHashMap<>(sexByAge);
            strat.put("components", List.of(Map.of("code", "sex", "kind", "value", "value", Map.of("source", "gender")), bad));
            CqlBuildResult result = builder.buildEcqmCql("M", "1.0.0", "proportion", "boolean",
                    List.of(proportionGroup()), List.of(), List.of(), List.of(), List.of(strat), "R4");
            assertThat(result.cql()).as("bad " + bad).doesNotContain("define \"Stratifier sex-age");
            assertThat(result.warnings()).as("bad " + bad).anyMatch(w -> w.contains("Stratifier sex-age"));
        }
    }

    // PAT-234 — a custom SDE element can be a value expression too (a risk adjustment factor
    // such as an age band), and a RAF define should be named "RAF …" (QM IG 3.19).
    @Test
    void supplementalData_valueKindAndRafNaming() {
        Map<String, Object> raf = new LinkedHashMap<>();
        raf.put("name", "RAF Age Band");
        raf.put("usage", "risk-adjustment-factor");
        raf.put("kind", "value");
        raf.put("value", Map.of("source", "ageBands", "bands", List.of(band("65+", 65, null))));
        Map<String, Object> badName = new LinkedHashMap<>();
        badName.put("name", "Diabetes");
        badName.put("usage", "risk-adjustment-factor");
        badName.put("criteria", populationTree());
        Map<String, Object> plainSde = new LinkedHashMap<>();
        plainSde.put("name", "Sex Value");
        plainSde.put("kind", "value");
        plainSde.put("value", Map.of("source", "gender"));

        CqlBuildResult result = builder.buildEcqmCql(
                "SdeMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(raf, badName, plainSde), List.of(), "R4");

        String age = "AgeInYearsAt(end of \"Measurement Period\")";
        assertThat(lf(result)).contains("define \"RAF Age Band\":\n  case\n    when " + age + " >= 65 then '65+'\n    else null\n  end");
        assertThat(lf(result)).contains("define \"Diabetes\":\n  ");
        assertThat(lf(result)).contains("define \"Sex Value\":\n  Patient.gender.value");
        assertThat(result.warnings()).singleElement().asString()
                .contains("Risk adjustment factor 'Diabetes'").contains("RAF");
    }

    @Test
    void valueStratifier_rejectsBadBandsAndUnknownSources_withoutEmittingADefine() {
        List<List<Map<String, Object>>> bad = List.of(
                List.of(),                                              // no bands
                List.of(band("a<b", 1, 2)),                             // label not plain text
                List.of(band("x", null, null)),                         // no bound at all
                List.of(band("x", 60, 40)),                             // min > max
                List.of(band("x", 18, 1000)),                           // beyond a human age
                List.of(band("x", 18.5, 40)),                           // not whole years
                List.of(band("x", "abc", 40)),                          // not a number
                List.of(band("same", 0, 17), band("same", 18, 64)));    // duplicate label → one stratum
        for (List<Map<String, Object>> bands : bad) {
            CqlBuildResult result = builder.buildEcqmCql(
                    "StratMeasure", "1.0.0", "proportion", "boolean",
                    List.of(proportionGroup()), List.of(), List.of(), List.of(),
                    List.of(valueStratifier("age", "ageBands", bands)), "R4");
            assertThat(result.cql()).as("bands " + bands).doesNotContain("define \"Stratifier age\"");
            assertThat(result.warnings()).as("bands " + bands).anyMatch(w -> w.contains("Stratifier age"));
        }

        CqlBuildResult unknown = builder.buildEcqmCql(
                "StratMeasure", "1.0.0", "proportion", "boolean",
                List.of(proportionGroup()), List.of(), List.of(), List.of(),
                List.of(valueStratifier("zip", "postalCode", null)), "R4");
        assertThat(unknown.cql()).doesNotContain("define \"Stratifier zip\"");
        assertThat(unknown.warnings()).anyMatch(w -> w.contains("unknown value source 'postalCode'"));
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

    // ===== PAT-237: library function call inside a population =====

    @Test
    @SuppressWarnings("unchecked")
    void buildEcqmCql_functionCallInPopulation_emitsIncludeAndCall_withMeasurementPeriodArgument() {
        Map<String, Object> hba1c = new LinkedHashMap<>();
        hba1c.put("uniqueId", "be_hba1c");
        hba1c.put("name", "HbA1c Results");
        hba1c.put("returnType", "boolean");
        // a base element is itself an element the engine can render — here an AgeRange, the
        // simplest one; what matters for the call is the name the index resolves the id to
        hba1c.put("type", "AgeRange");
        hba1c.put("fields", List.of(
                Map.of("id", "element_name", "type", "string", "value", "HbA1c Results"),
                Map.of("id", "min_age", "type", "string", "value", "18"),
                Map.of("id", "max_age", "type", "string", "value", ""),
                Map.of("id", "unit_of_time", "type", "string", "value", "year")));
        hba1c.put("modifiers", new ArrayList<>());

        List<Map<String, Object>> args = List.of(
                Map.of("name", "observations", "mode", "element", "operand_id", "be_hba1c"),
                Map.of("name", "period", "mode", "measurementPeriod"),
                Map.of("name", "threshold", "mode", "literal", "literal_type", "Decimal", "literal_value", "7.0"));
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("uniqueId", "fn-1");
        call.put("type", "externalCqlFunctionCall");
        call.put("name", "Controlled");
        call.put("returnType", "boolean");
        call.put("fields", List.of(
                Map.of("id", "element_name", "type", "string", "value", "Controlled"),
                Map.of("id", "library_name", "type", "string", "value", "HospitalCommon", "static", true),
                Map.of("id", "library_version", "type", "string", "value", "2.0.0", "static", true),
                Map.of("id", "function_name", "type", "string", "value", "Most Recent Below", "static", true),
                Map.of("id", "arguments", "type", "functionArguments", "value", args)));
        call.put("modifiers", new ArrayList<>());
        Map<String, Object> tree = emptyTree();
        ((List<Object>) tree.get("childInstances")).add(call);
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        Map<String, Object> pops = new LinkedHashMap<>();
        pops.put("initial-population", populationTree());
        pops.put("denominator", populationTree());
        pops.put("numerator", tree);
        group.put("populations", pops);

        CqlBuildResult result = builder.buildEcqmCql(
                "FnMeasure", "1.0.0", "proportion", "boolean",
                List.of(group), List.of(hba1c), List.of(), List.of(), List.of(), "R4");

        assertThat(result.cql()).contains("include HospitalCommon version '2.0.0' called HospitalCommon");
        assertThat(result.cql()).contains(
                "\"HospitalCommon\".\"Most Recent Below\"(\"HbA1c Results\", \"Measurement Period\", 7.0)");
        assertThat(result.warnings()).isEmpty();
    }
}
