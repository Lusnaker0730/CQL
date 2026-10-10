package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.fhir.CqfmConstants;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureExportConformance;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.DataRequirementExtractor;
import com.cqlplatform.service.measure.FhirMeasureService.MeasureExport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PAT-229 — what {@link FhirMeasureService} decides to export, and what an import stores. */
@ExtendWith(MockitoExtension.class)
class FhirMeasureServiceExportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ELM = "{\"library\":{\"identifier\":{\"id\":\"AdultCohortLogic\",\"version\":\"3.1.0\"}}}";

    /** A Measure as another tool would send it, with things this platform does not model. */
    private static final String IMPORTED = """
            {"resourceType":"Measure","id":"ext-1","name":"AdultCohort","version":"1.0.0","title":"Adults",
             "description":"Adult patients","status":"active",
             "meta":{"profile":["http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cohort-measure-cqfm"]},
             "topic":[{"text":"Population health"}],
             "library":["https://other.example.org/fhir/Library/AdultCohortLogic|3.1.0"],
             "scoring":{"coding":[{"system":"http://terminology.hl7.org/CodeSystem/measure-scoring","code":"cohort"}]},
             "group":[{"id":"main","extension":[{"url":"http://example.org/unknown-extension","valueString":"kept"}],
               "population":[{"id":"ip","code":{"coding":[{"code":"initial-population"}]},
                 "criteria":{"language":"text/cql-identifier","expression":"Initial Population"}}]}]}""";

    @Mock private MeasureDefinitionService definitionService;
    @Mock private CqlTranslationService translationService;
    @Mock private DataRequirementExtractor dataRequirementExtractor;

    private FhirMeasureService service;

    @BeforeEach
    void setUp() {
        FhirCanonicalResolver canonical = new FhirCanonicalResolver("https://hospital.example.tw/fhir", "");
        CqfmLibraryBuilder libraryBuilder = new CqfmLibraryBuilder(canonical);
        service = new FhirMeasureService(definitionService, translationService, dataRequirementExtractor,
                new CqfmMeasureBuilder(canonical, libraryBuilder), libraryBuilder);

        lenient().when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(
                CqlTranslationResponse.builder().success(true).elmJson(ELM)
                        .metadata(CqlTranslationResponse.TranslationMetadata.builder()
                                .libraryId("AdultCohortLogic").libraryVersion("3.1.0").build())
                        .build());
        lenient().when(dataRequirementExtractor.extract(anyString())).thenReturn(List.of());
    }

    private MeasureDefinition asImported() throws Exception {
        // What parse + create leave in the database for IMPORTED: forced to draft, CQL attached.
        MeasureDefinition parsed = service.parseFhirMeasure(MAPPER.readTree(IMPORTED));
        parsed.setId(3L);
        parsed.setStatus("draft");
        parsed.setFhirMeasureJson(IMPORTED);
        parsed.setCqlContent("library AdultCohortLogic version '3.1.0'");
        parsed.setUpdatedAt(LocalDateTime.of(2026, 9, 10, 8, 0));
        return parsed;
    }

    @Test
    void platformAuthoredMeasure_isBuilt_andNamesItsLibraryAfterTheCqlLibrary() {
        when(definitionService.getById(1L)).thenReturn(Optional.of(MeasureDefinition.builder()
                .id(1L).name("AdultCohort").version("1.0.0").title("Adults").description("Adult patients")
                .status("draft").scoringType(ScoringTypeConstants.COHORT)
                .cqlContent("library AdultCohortLogic version '3.1.0'")
                .groupDefinitions(List.of(GroupDefinition.builder().groupId("group-1").populations(List.of(
                        PopulationDefinition.builder().populationType("initial-population")
                                .criteriaExpression("Initial Population").build())).build()))
                .build()));

        MeasureExport export = service.export(1L);

        // The CQL library's declared name / version, not the measure's.
        assertThat(export.measure().path("library").get(0).asText())
                .isEqualTo("https://hospital.example.tw/fhir/Library/AdultCohortLogic|3.1.0");
        assertThat(export.conformance().getProfiles()).contains(CqfmConstants.PROFILE_COMPUTABLE_MEASURE,
                CqfmConstants.PROFILE_COHORT_MEASURE);
        assertThat(export.elmJson()).isEqualTo(ELM);
    }

    @Test
    void importedAndUnchanged_isExportedAsTheOriginalResource_withThisInstallationsStatus() throws Exception {
        when(definitionService.getById(3L)).thenReturn(Optional.of(asImported()));

        MeasureExport export = service.export(3L);
        JsonNode measure = export.measure();

        // Kept exactly: their id, their library canonical, an extension we have never heard of.
        assertThat(measure.path("id").asText()).isEqualTo("ext-1");
        assertThat(measure.path("library").get(0).asText()).startsWith("https://other.example.org/");
        assertThat(measure.path("group").get(0).path("extension").get(0).path("valueString").asText()).isEqualTo("kept");
        assertThat(measure.path("topic").get(0).path("text").asText()).isEqualTo("Population health");
        // …except the status: it arrived active, but here it is a draft until it is approved here.
        assertThat(measure.path("status").asText()).isEqualTo("draft");
        assertThat(export.conformance().getProfiles())
                .containsExactly("http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cohort-measure-cqfm");
        assertThat(export.conformance().getIssues()).anyMatch(i -> i.getMessage().contains("Unchanged since it was imported"));
    }

    @Test
    void importedThenEdited_isRebuiltFromTheDefinition_keepingRootElementsWeDoNotModel() throws Exception {
        MeasureDefinition edited = asImported();
        edited.setTitle("Adults (revised)");
        when(definitionService.getById(3L)).thenReturn(Optional.of(edited));

        JsonNode measure = service.export(3L).measure();

        assertThat(measure.path("title").asText()).isEqualTo("Adults (revised)");
        assertThat(measure.path("url").asText()).isEqualTo("https://hospital.example.tw/fhir/Measure/AdultCohort");
        assertThat(measure.path("topic").get(0).path("text").asText()).isEqualTo("Population health");
        assertThat(measure.path("group").get(0).path("id").asText()).isEqualTo("main");
    }

    @Test
    void cqlThatNoLongerTranslates_isAnErrorInTheReport_notAnException() {
        when(definitionService.getById(1L)).thenReturn(Optional.of(MeasureDefinition.builder()
                .id(1L).name("Broken").version("1.0.0").status("draft").scoringType(ScoringTypeConstants.COHORT)
                .cqlContent("library Broken version '1.0.0'\ndefine oops")
                .groupDefinitions(List.of()).build()));
        when(translationService.translate(any(CqlTranslationRequest.class)))
                .thenReturn(CqlTranslationResponse.builder().success(false).build());

        MeasureExport export = service.export(1L);

        assertThat(export.elmJson()).isNull();
        assertThat(export.conformance().isExchangeReady()).isFalse();
        assertThat(export.conformance().getIssues()).anyMatch(i -> MeasureExportConformance.ERROR.equals(i.getSeverity())
                && "Library.content".equals(i.getElement()));
    }

    @Test
    void importWithCql_storesTheLogicAndTheOriginalResource() throws Exception {
        when(definitionService.create(any(MeasureDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        service.importFhirMeasure(MAPPER.readTree(IMPORTED), "library AdultCohortLogic version '3.1.0'");

        ArgumentCaptor<MeasureDefinition> created = ArgumentCaptor.forClass(MeasureDefinition.class);
        verify(definitionService).create(created.capture());
        assertThat(created.getValue().getCqlContent()).isEqualTo("library AdultCohortLogic version '3.1.0'");
        assertThat(created.getValue().getCqlLibraryId()).isEqualTo("AdultCohortLogic");
        assertThat(created.getValue().getGroupDefinitions().get(0).getGroupId()).isEqualTo("main");
        assertThat(created.getValue().getFhirMeasureJson()).contains("\"ext-1\"");
    }
}
