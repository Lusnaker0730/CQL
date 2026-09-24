package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.StrictErrorHandler;
import com.cqlplatform.model.fhir.CqfmConstants;
import com.cqlplatform.model.measure.DataRequirementInfo;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureExportConformance;
import com.cqlplatform.model.measure.ObservationDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.model.measure.StratifierDefinition;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.DataRequirementExtractor;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ElmDependencies;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.Include;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ValueSetRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hl7.fhir.r4.model.Measure;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * PAT-229 — the exported Measure follows the HL7 Quality Measure IG 5.0.0 / CRMI 2.0.0, claims
 * only what it satisfies, and reads back into the same definition.
 */
class CqfmMeasureBuilderTest {

    private static final String BASE = "https://hospital.example.tw/fhir";
    private static final String QM = "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/";

    private final FhirCanonicalResolver canonical = new FhirCanonicalResolver(BASE, "");
    private final CqfmLibraryBuilder libraryBuilder = new CqfmLibraryBuilder(canonical);
    private final CqfmMeasureBuilder builder = new CqfmMeasureBuilder(canonical, libraryBuilder);

    // ===== fixtures =====

    private static PopulationDefinition pop(String type, String expression) {
        return PopulationDefinition.builder().populationType(type).criteriaExpression(expression).build();
    }

    private static MeasureDefinition.MeasureDefinitionBuilder proportion() {
        return MeasureDefinition.builder()
                .id(7L).name("DiabetesHbA1cControl").version("1.2.0")
                .title("Diabetes: HbA1c control").description("Share of diabetic patients with HbA1c below 7%.")
                .status("active").scoringType(ScoringTypeConstants.PROPORTION).improvementNotation("increase")
                .steward("Example Hospital Quality Office").rationale("Glycaemic control reduces complications.")
                .clinicalGuidance("Exclude hospice patients.").copyright("(c) Example Hospital")
                .disclaimer("For quality improvement only.").rateAggregation("none")
                .riskAdjustmentDescription("Adjusted for age band.")
                .developers(List.of("QI team"))
                .references(List.of(MeasureDefinition.MeasureReference.builder().type("CITATION").reference("ADA 2026 Standards").build()))
                .supplementalData(List.of(MeasureDefinition.SupplementalDataDef.builder().definition("SDE Sex").description("Administrative sex").build()))
                .riskAdjustments(List.of(MeasureDefinition.RiskAdjustmentDef.builder().definition("RAF Age Band").description("Age band").build()))
                .createdAt(LocalDateTime.of(2026, 1, 5, 9, 0)).updatedAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .cqlContent("library DiabetesHbA1cControl version '1.2.0'")
                .groupDefinitions(List.of(GroupDefinition.builder()
                        .groupId("group-1").description("Overall rate").populationBasis("boolean").scoringUnit("%")
                        .populations(List.of(pop("initial-population", "Initial Population"),
                                pop("denominator", "Denominator"), pop("denominator-exclusion", "Denominator Exclusions"),
                                pop("numerator", "Numerator")))
                        .stratifiers(List.of(StratifierDefinition.builder().stratifierId("strat-elderly")
                                .criteriaExpression("Stratifier 1").description("Age 65+").build()))
                        .build()));
    }

    private static MeasureDefinition cvMeasure() {
        return MeasureDefinition.builder()
                .id(8L).name("InpatientLengthOfStay").version("1.0.0").title("Inpatient length of stay")
                .description("Average length of inpatient stays.").status("draft")
                .scoringType(ScoringTypeConstants.CONTINUOUS_VARIABLE).improvementNotation("decrease")
                .cqlContent("library InpatientLengthOfStay version '1.0.0'")
                .updatedAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .groupDefinitions(List.of(GroupDefinition.builder()
                        .groupId("group-1").populationBasis("Encounter")
                        .populations(List.of(pop("initial-population", "Initial Population"),
                                pop("measure-population", "Measure Population")))
                        .observations(List.of(ObservationDefinition.builder().criteriaExpression("Measure Observation 1")
                                .aggregateMethod("Average").populationRef("measure-population").build()))
                        .build()))
                .build();
    }

    private static ElmDependencies deps() {
        return new ElmDependencies(
                List.of(new Include("FHIRHelpers", "FHIRHelpers", "4.0.1"), new Include("Common", "HospitalCommon", "2.0.0")),
                List.of(new ValueSetRef("Diabetes", "https://twcore.mohw.gov.tw/ig/twcore/ValueSet/diabetes", null)),
                List.of(), List.of("Measurement Period"));
    }

    private ObjectNode build(MeasureDefinition def, MeasureExportConformance report) {
        DataRequirementInfo requirement = new DataRequirementInfo();
        requirement.setType("Condition");
        return builder.build(def, def.getName(), def.getVersion(), deps(), List.of(requirement), null, report);
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static JsonNode extension(JsonNode element, String url) {
        for (JsonNode ext : element.path("extension")) {
            if (url.equals(ext.path("url").asText())) return ext;
        }
        throw new AssertionError("no extension " + url + " on " + element);
    }

    // ===== what a complete measure exports =====

    @Test
    void completeProportionMeasure_claimsShareablePublishableComputableAndProportion() {
        MeasureExportConformance report = new MeasureExportConformance();
        ObjectNode measure = build(proportion().build(), report);

        assertThat(texts(measure.path("meta").path("profile"))).containsExactly(
                CqfmConstants.PROFILE_CRMI_SHAREABLE_MEASURE, CqfmConstants.PROFILE_CRMI_PUBLISHABLE_MEASURE,
                CqfmConstants.PROFILE_COMPUTABLE_MEASURE, CqfmConstants.PROFILE_PROPORTION_MEASURE);
        assertThat(report.getProfiles()).isEqualTo(texts(measure.path("meta").path("profile")));
        assertThat(report.isExchangeReady()).isTrue();
        assertThat(report.getIssues()).noneMatch(i -> MeasureExportConformance.WARNING.equals(i.getSeverity()));

        assertThat(measure.path("url").asText()).isEqualTo(BASE + "/Measure/DiabetesHbA1cControl");
        assertThat(measure.path("status").asText()).isEqualTo("active");
        assertThat(measure.path("date").asText()).isEqualTo("2026-09-01");
        assertThat(measure.path("publisher").asText()).isEqualTo("Example Hospital Quality Office");
        // Only the primary library, canonical and pinned to its version (QM IG requirement 3.2).
        assertThat(texts(measure.path("library"))).containsExactly(BASE + "/Library/DiabetesHbA1cControl|1.2.0");
        assertThat(measure.path("improvementNotation").path("coding").get(0).path("code").asText()).isEqualTo("increase");
        assertThat(measure.path("improvementNotation").path("coding").get(0).path("system").asText())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/measure-improvement-notation");
        assertThat(measure.path("riskAdjustment").asText()).isEqualTo("Adjusted for age band.");
        assertThat(measure.path("guidance").asText()).isEqualTo("Exclude hospice patients.");
        assertThat(measure.path("author").get(0).path("name").asText()).isEqualTo("QI team");
        assertThat(measure.path("relatedArtifact").get(0).path("type").asText()).isEqualTo("citation");
        assertThat(measure.path("text").path("div").asText()).startsWith("<div xmlns=\"http://www.w3.org/1999/xhtml\">");
    }

    @Test
    void groupsCarryIdsBasisScoringUnitAndCqlIdentifierCriteria() {
        ObjectNode measure = build(proportion().build(), new MeasureExportConformance());
        JsonNode group = measure.path("group").get(0);

        assertThat(group.path("id").asText()).isEqualTo("group-1");
        assertThat(extension(group, QM + "cqfm-populationBasis").path("valueCode").asText()).isEqualTo("boolean");
        assertThat(extension(group, QM + "cqfm-scoringUnit").path("valueCodeableConcept").path("text").asText()).isEqualTo("%");
        JsonNode numerator = group.path("population").get(3);
        assertThat(numerator.path("id").asText()).isEqualTo("group-1-numerator");
        assertThat(numerator.path("criteria").path("language").asText()).isEqualTo("text/cql-identifier");
        assertThat(numerator.path("criteria").path("expression").asText()).isEqualTo("Numerator");
        assertThat(group.path("stratifier").get(0).path("code").path("text").asText()).isEqualTo("strat-elderly");
    }

    @Test
    void supplementalDataAndRiskFactorsUseTheTwoUsageCodes() {
        ObjectNode measure = build(proportion().build(), new MeasureExportConformance());
        JsonNode sde = measure.path("supplementalData").get(0);
        JsonNode raf = measure.path("supplementalData").get(1);

        assertThat(sde.path("criteria").path("expression").asText()).isEqualTo("SDE Sex");
        assertThat(sde.path("usage").get(0).path("coding").get(0).path("code").asText()).isEqualTo("supplemental-data");
        assertThat(raf.path("criteria").path("expression").asText()).isEqualTo("RAF Age Band");
        assertThat(raf.path("usage").get(0).path("coding").get(0).path("code").asText()).isEqualTo("risk-adjustment-factor");
        assertThat(raf.path("usage").get(0).path("coding").get(0).path("system").asText())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/measure-data-usage");
    }

    @Test
    void observationIsAMeasureObservationPopulation_withAggregateMethodAndCriteriaReference() {
        MeasureExportConformance report = new MeasureExportConformance();
        ObjectNode measure = build(cvMeasure(), report);
        JsonNode observation = measure.path("group").get(0).path("population").get(2);

        assertThat(observation.path("code").path("coding").get(0).path("code").asText()).isEqualTo("measure-observation");
        assertThat(observation.path("criteria").path("expression").asText()).isEqualTo("Measure Observation 1");
        assertThat(extension(observation, QM + "cqfm-aggregateMethod").path("valueCode").asText()).isEqualTo("average");
        // criteriaReference holds the ID of the population the observation is computed over.
        assertThat(extension(observation, QM + "cqfm-criteriaReference").path("valueString").asText())
                .isEqualTo("group-1-measure-population");
        assertThat(report.getProfiles()).contains(CqfmConstants.PROFILE_CV_MEASURE);
    }

    @Test
    void dataRequirementsLiveInAContainedModuleDefinitionLibrary_notOnTheMeasure() {
        ObjectNode measure = build(proportion().build(), new MeasureExportConformance());

        // R4 Measure has no dataRequirement element — the previous export put one there.
        assertThat(measure.has("dataRequirement")).isFalse();
        JsonNode contained = measure.path("contained").get(0);
        assertThat(contained.path("id").asText()).isEqualTo("effective-data-requirements");
        assertThat(contained.path("type").path("coding").get(0).path("code").asText()).isEqualTo("module-definition");
        assertThat(contained.path("dataRequirement").get(0).path("type").asText()).isEqualTo("Condition");
        assertThat(extension(measure, "http://hl7.org/fhir/uv/crmi/StructureDefinition/crmi-effectiveDataRequirements")
                .path("valueCanonical").asText()).isEqualTo("#effective-data-requirements");

        List<String> dependsOn = StreamSupport.stream(contained.path("relatedArtifact").spliterator(), false)
                .map(a -> a.path("resource").asText()).toList();
        assertThat(dependsOn).containsExactly(
                "http://hl7.org/fhir/uv/cql/Library/FHIRHelpers|4.0.1",
                BASE + "/Library/HospitalCommon|2.0.0",
                "https://twcore.mohw.gov.tw/ig/twcore/ValueSet/diabetes");
    }

    // ===== nothing is invented =====

    @Test
    void missingTitle_isReported_andTheShareableProfileIsNotClaimed() {
        MeasureExportConformance report = new MeasureExportConformance();
        ObjectNode measure = build(proportion().title(null).build(), report);

        assertThat(measure.has("title")).isFalse();
        assertThat(report.getProfiles()).doesNotContain(CqfmConstants.PROFILE_CRMI_SHAREABLE_MEASURE,
                CqfmConstants.PROFILE_CRMI_PUBLISHABLE_MEASURE);
        assertThat(report.getProfiles()).contains(CqfmConstants.PROFILE_COMPUTABLE_MEASURE);
        assertThat(report.getIssues()).anyMatch(i -> "Measure.title".equals(i.getElement()));
    }

    @Test
    void missingImprovementNotation_isNotDefaulted_soTheMeasureIsNotComputable() {
        MeasureExportConformance report = new MeasureExportConformance();
        ObjectNode measure = build(proportion().improvementNotation(null).build(), report);

        assertThat(measure.has("improvementNotation")).isFalse();
        assertThat(report.getProfiles()).doesNotContain(CqfmConstants.PROFILE_COMPUTABLE_MEASURE,
                CqfmConstants.PROFILE_PROPORTION_MEASURE);
        assertThat(report.getIssues()).anyMatch(i -> i.getMessage().contains("cmp-4"));
    }

    @Test
    void cohortMeasureNeedsNoImprovementNotation() {
        MeasureDefinition cohort = MeasureDefinition.builder().name("AdultCohort").version("1.0.0")
                .title("Adults").description("Adult patients").status("draft").scoringType(ScoringTypeConstants.COHORT)
                .cqlContent("library AdultCohort version '1.0.0'")
                .groupDefinitions(List.of(GroupDefinition.builder().groupId("group-1")
                        .populations(List.of(pop("initial-population", "Initial Population"))).build()))
                .build();
        MeasureExportConformance report = new MeasureExportConformance();
        build(cohort, report);

        assertThat(report.getProfiles()).contains(CqfmConstants.PROFILE_COMPUTABLE_MEASURE, CqfmConstants.PROFILE_COHORT_MEASURE);
        // Draft → shareable but not publishable.
        assertThat(report.getProfiles()).doesNotContain(CqfmConstants.PROFILE_CRMI_PUBLISHABLE_MEASURE);
    }

    @Test
    void measureWithoutCql_isAnError_andHasNoLibraryReference() {
        MeasureExportConformance report = new MeasureExportConformance();
        MeasureDefinition def = proportion().cqlContent(null).build();
        ObjectNode measure = builder.build(def, null, null, ElmDependencies.empty(), List.of(), null, report);

        assertThat(measure.has("library")).isFalse();
        assertThat(measure.has("contained")).isFalse();
        assertThat(report.isExchangeReady()).isFalse();
        assertThat(report.getProfiles()).doesNotContain(CqfmConstants.PROFILE_COMPUTABLE_MEASURE);
    }

    @Test
    void platformInReviewStatus_isExportedAsDraft() {
        ObjectNode measure = build(proportion().status("in-review").build(), new MeasureExportConformance());
        assertThat(measure.path("status").asText()).isEqualTo("draft");
    }

    @Test
    void nonBooleanPopulationBasis_warnsThatThisPlatformCountsPerPatient() {
        MeasureExportConformance report = new MeasureExportConformance();
        build(cvMeasure(), report);
        assertThat(report.getIssues()).anyMatch(i -> MeasureExportConformance.WARNING.equals(i.getSeverity())
                && i.getMessage().contains("counts populations per patient"));
    }

    @Test
    void unconfiguredCanonicalBase_isFlagged() {
        FhirCanonicalResolver placeholder = new FhirCanonicalResolver("", "");
        CqfmMeasureBuilder b = new CqfmMeasureBuilder(placeholder, new CqfmLibraryBuilder(placeholder));
        MeasureExportConformance report = new MeasureExportConformance();
        MeasureDefinition def = proportion().build();
        ObjectNode measure = b.build(def, def.getName(), def.getVersion(), deps(), List.of(), null, report);

        assertThat(measure.path("url").asText()).startsWith("http://localhost:8080/fhir/Measure/");
        assertThat(report.isCanonicalBaseConfigured()).isFalse();
        assertThat(report.getIssues()).anyMatch(i -> i.getMessage().contains("FHIR_CANONICAL_BASE"));
    }

    @Test
    void appBaseUrlIsUsedWhenNoCanonicalBaseIsSet() {
        FhirCanonicalResolver fromAppUrl = new FhirCanonicalResolver(" ", "https://cql.example.tw/");
        assertThat(fromAppUrl.isConfigured()).isTrue();
        assertThat(fromAppUrl.measureUrl("LOS (days)")).isEqualTo("https://cql.example.tw/fhir/Measure/LOS-days");
    }

    // ===== an independent referee: HAPI's strict parser =====

    @Test
    void exportedMeasure_isStructurallyValidR4_accordingToHapiStrictParser() {
        for (MeasureDefinition def : List.of(proportion().build(), cvMeasure())) {
            String json = build(def, new MeasureExportConformance()).toString();
            // Throws on an unknown element (the old Measure.dataRequirement) or an invalid code
            // (the old status "in-review").
            Measure parsed = FhirContext.forR4Cached().newJsonParser()
                    .setParserErrorHandler(new StrictErrorHandler())
                    .parseResource(Measure.class, json);
            assertThat(parsed.getGroupFirstRep().getPopulation()).isNotEmpty();
            assertThat(parsed.getLibrary()).hasSize(1);
        }
    }

    // ===== round trip: what is exported reads back =====

    @Test
    void exportedMeasure_readsBackIntoTheSameDefinition() {
        FhirMeasureService service = new FhirMeasureService(mock(MeasureDefinitionService.class),
                mock(CqlTranslationService.class), mock(DataRequirementExtractor.class), builder, libraryBuilder);

        MeasureDefinition original = proportion().build();
        MeasureDefinition readBack = service.parseFhirMeasure(build(original, new MeasureExportConformance()));

        assertThat(readBack.getName()).isEqualTo(original.getName());
        assertThat(readBack.getVersion()).isEqualTo("1.2.0");
        assertThat(readBack.getTitle()).isEqualTo(original.getTitle());
        assertThat(readBack.getStatus()).isEqualTo("active");
        assertThat(readBack.getCqlLibraryId()).isEqualTo("DiabetesHbA1cControl");
        assertThat(readBack.getImprovementNotation()).isEqualTo("increase");
        assertThat(readBack.getSteward()).isEqualTo(original.getSteward());
        assertThat(readBack.getRationale()).isEqualTo(original.getRationale());
        assertThat(readBack.getClinicalGuidance()).isEqualTo(original.getClinicalGuidance());
        assertThat(readBack.getRiskAdjustmentDescription()).isEqualTo(original.getRiskAdjustmentDescription());
        assertThat(readBack.getDevelopers()).containsExactly("QI team");
        assertThat(readBack.getReferences().get(0).getReference()).isEqualTo("ADA 2026 Standards");
        assertThat(readBack.getSupplementalData().get(0).getDefinition()).isEqualTo("SDE Sex");
        assertThat(readBack.getRiskAdjustments().get(0).getDefinition()).isEqualTo("RAF Age Band");

        GroupDefinition group = readBack.getGroupDefinitions().get(0);
        assertThat(group.getGroupId()).isEqualTo("group-1");
        assertThat(group.getPopulationBasis()).isEqualTo("boolean");
        assertThat(group.getScoringUnit()).isEqualTo("%");
        assertThat(group.getPopulations()).extracting(PopulationDefinition::getPopulationType)
                .containsExactly("initial-population", "denominator", "denominator-exclusion", "numerator");
        assertThat(group.getStratifiers().get(0).getStratifierId()).isEqualTo("strat-elderly");
    }

    // PAT-235 — a multi-component stratifier is Measure.group.stratifier.component[] with one
    // criteria per component and no stratifier-level criteria; it reads back as components.
    @Test
    void componentStratifier_exportsAsComponents_andReadsBack() {
        FhirMeasureService service = new FhirMeasureService(mock(MeasureDefinitionService.class),
                mock(CqlTranslationService.class), mock(DataRequirementExtractor.class), builder, libraryBuilder);
        MeasureDefinition original = proportion().build();
        original.getGroupDefinitions().get(0).setStratifiers(List.of(StratifierDefinition.builder()
                .stratifierId("sex-age").description("Sex by age band")
                .components(List.of(
                        StratifierDefinition.Component.builder().code("sex").criteriaExpression("Stratifier sex-age sex").kind("value").build(),
                        StratifierDefinition.Component.builder().code("age").criteriaExpression("Stratifier sex-age age").description("Age band").build()))
                .build()));

        ObjectNode measure = build(original, new MeasureExportConformance());
        JsonNode strat = measure.path("group").get(0).path("stratifier").get(0);
        assertThat(strat.path("code").path("text").asText()).isEqualTo("sex-age");
        assertThat(strat.has("criteria")).isFalse();
        assertThat(strat.path("component")).hasSize(2);
        assertThat(strat.path("component").get(0).path("code").path("text").asText()).isEqualTo("sex");
        assertThat(strat.path("component").get(0).path("criteria").path("expression").asText()).isEqualTo("Stratifier sex-age sex");
        assertThat(strat.path("component").get(0).path("criteria").path("language").asText()).isEqualTo("text/cql-identifier");
        assertThat(strat.path("component").get(1).path("description").asText()).isEqualTo("Age band");
        assertThat(strat.path("component").get(1).path("id").asText()).isNotBlank();

        StratifierDefinition readBack = service.parseFhirMeasure(measure).getGroupDefinitions().get(0).getStratifiers().get(0);
        assertThat(readBack.getStratifierId()).isEqualTo("sex-age");
        assertThat(readBack.getCriteriaExpression()).isNull();
        assertThat(readBack.getComponents()).extracting(c -> c.getCode() + "=" + c.getCriteriaExpression())
                .containsExactly("sex=Stratifier sex-age sex", "age=Stratifier sex-age age");
        assertThat(readBack.getComponents().get(1).getDescription()).isEqualTo("Age band");
    }

    @Test
    void observation_readsBackWithThePopulationTypeItRefersTo() {
        FhirMeasureService service = new FhirMeasureService(mock(MeasureDefinitionService.class),
                mock(CqlTranslationService.class), mock(DataRequirementExtractor.class), builder, libraryBuilder);

        MeasureDefinition readBack = service.parseFhirMeasure(build(cvMeasure(), new MeasureExportConformance()));
        GroupDefinition group = readBack.getGroupDefinitions().get(0);

        // The observation is not a population of the group…
        assertThat(group.getPopulations()).extracting(PopulationDefinition::getPopulationType)
                .containsExactly("initial-population", "measure-population");
        // …and its criteriaReference (a population ID) comes back as the population TYPE.
        ObservationDefinition observation = group.getObservations().get(0);
        assertThat(observation.getCriteriaExpression()).isEqualTo("Measure Observation 1");
        assertThat(observation.getAggregateMethod()).isEqualTo("average");
        assertThat(observation.getPopulationRef()).isEqualTo("measure-population");
        assertThat(group.getPopulationBasis()).isEqualTo("Encounter");
    }
}
