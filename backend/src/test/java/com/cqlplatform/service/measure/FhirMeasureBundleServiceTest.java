package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureExportConformance;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.fhir.FhirImplementationGuideService;
import com.cqlplatform.service.fhir.VsacService;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ElmDependencies;
import com.cqlplatform.service.measure.FhirMeasureBundleService.BundleExport;
import com.cqlplatform.service.measure.FhirMeasureService.MeasureExport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * PAT-229 — the package: Measure + primary Library + every included Library (walked through the
 * ELM) + value sets as full definitions when resolvable, flagged stubs when not.
 */
@ExtendWith(MockitoExtension.class)
class FhirMeasureBundleServiceTest {

    private static final String BASE = "https://hospital.example.tw/fhir";
    private static final String VS_KNOWN = "https://twcore.mohw.gov.tw/ig/twcore/ValueSet/diabetes";
    private static final String VS_UNKNOWN = "https://example.org/fhir/ValueSet/not-loaded-here";

    /** Main library: includes FHIRHelpers + HospitalCommon, uses one value set. */
    private static final String MAIN_ELM = """
            {"library":{"identifier":{"id":"DiabetesHbA1cControl","version":"1.2.0"},
             "includes":{"def":[{"localIdentifier":"FHIRHelpers","path":"FHIRHelpers","version":"4.0.1"},
                                {"localIdentifier":"Common","path":"HospitalCommon","version":"2.0.0"}]},
             "valueSets":{"def":[{"name":"Diabetes","id":"%s"}]},
             "parameters":{"def":[{"name":"Measurement Period"}]}}}""".formatted(VS_KNOWN);
    /** HospitalCommon includes a further library and brings its own value set. */
    private static final String COMMON_ELM = """
            {"library":{"identifier":{"id":"HospitalCommon","version":"2.0.0"},
             "includes":{"def":[{"localIdentifier":"Deep","path":"DeepHelpers","version":"1.0.0"}]},
             "valueSets":{"def":[{"name":"Not loaded","id":"%s"}]}}}""".formatted(VS_UNKNOWN);

    @Mock private FhirMeasureService fhirMeasureService;
    @Mock private CqlLibraryService cqlLibraryService;
    @Mock private CqlTranslationService translationService;
    @Mock private MeasureDefinitionService definitionService;
    @Mock private VsacService vsacService;
    @Mock private FhirImplementationGuideService igService;
    @Mock private ObjectProvider<FhirImplementationGuideService> igProvider;
    @Mock private com.cqlplatform.service.terminology.PlatformValueSetService platformValueSets;

    private FhirMeasureBundleService service;
    private CqfmLibraryBuilder libraryBuilder;

    @BeforeEach
    void setUp() {
        FhirCanonicalResolver canonical = new FhirCanonicalResolver(BASE, "");
        libraryBuilder = new CqfmLibraryBuilder(canonical);
        CqfmMeasureBuilder measureBuilder = new CqfmMeasureBuilder(canonical, libraryBuilder);
        service = new FhirMeasureBundleService(fhirMeasureService, cqlLibraryService, translationService,
                definitionService, libraryBuilder, canonical, FhirContext.forR4Cached(), vsacService, igProvider, platformValueSets);
        lenient().when(platformValueSets.resolveForCaller(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        MeasureDefinition definition = MeasureDefinition.builder()
                .id(7L).name("DiabetesHbA1cControl").version("1.2.0").title("Diabetes: HbA1c control")
                .description("Share of diabetic patients with HbA1c below 7%.").status("active")
                .scoringType(ScoringTypeConstants.PROPORTION).improvementNotation("increase")
                .updatedAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .cqlContent("library DiabetesHbA1cControl version '1.2.0'")
                .groupDefinitions(List.of(GroupDefinition.builder().groupId("group-1").populations(List.of(
                        pop("initial-population"), pop("denominator"), pop("numerator"))).build()))
                .build();
        ElmDependencies deps = libraryBuilder.readDependencies(MAIN_ELM);
        MeasureExportConformance report = new MeasureExportConformance();
        ObjectNode measure = measureBuilder.build(definition, "DiabetesHbA1cControl", "1.2.0", deps, List.of(), null, report);
        when(fhirMeasureService.export(7L)).thenReturn(new MeasureExport(definition, measure, report, MAIN_ELM,
                "DiabetesHbA1cControl", "1.2.0", deps, List.of()));

        lenient().when(cqlLibraryService.getLibraryByNameAndVersion(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(cqlLibraryService.getLatestLibrary(anyString())).thenReturn(Optional.empty());
        lenient().when(cqlLibraryService.getLibraryByNameAndVersion("HospitalCommon", "2.0.0"))
                .thenReturn(Optional.of(CqlLibrary.builder().id("c1").name("HospitalCommon").version("2.0.0")
                        .cqlContent("library HospitalCommon version '2.0.0'").elmJson(COMMON_ELM).build()));

        ValueSet diabetes = new ValueSet();
        diabetes.setUrl(VS_KNOWN).setName("Diabetes");
        diabetes.getCompose().addInclude().setSystem("http://hl7.org/fhir/sid/icd-10-cm").addConcept().setCode("E11.9");
        lenient().when(igProvider.getIfAvailable()).thenReturn(igService);
        lenient().when(igService.isLoaded()).thenReturn(true);
        lenient().when(igService.getValueSetByUrl(VS_KNOWN)).thenReturn(diabetes);
        lenient().when(vsacService.isConfigured()).thenReturn(false);
    }

    private static PopulationDefinition pop(String type) {
        return PopulationDefinition.builder().populationType(type).criteriaExpression(type).build();
    }

    private static List<JsonNode> resources(ObjectNode bundle, String type) {
        return StreamSupport.stream(bundle.path("entry").spliterator(), false)
                .map(e -> e.path("resource")).filter(r -> type.equals(r.path("resourceType").asText())).toList();
    }

    @Test
    void packageContainsMeasure_primaryLibrary_andTheIncludedLibraryFoundHere() {
        ObjectNode bundle = service.exportAsBundle(7L);

        assertThat(resources(bundle, "Measure")).hasSize(1);
        List<JsonNode> libraries = resources(bundle, "Library");
        assertThat(libraries).extracting(l -> l.path("name").asText())
                .containsExactly("DiabetesHbA1cControl", "HospitalCommon");

        JsonNode primary = libraries.get(0);
        assertThat(primary.path("url").asText()).isEqualTo(BASE + "/Library/DiabetesHbA1cControl");
        assertThat(primary.path("version").asText()).isEqualTo("1.2.0");
        assertThat(StreamSupport.stream(primary.path("meta").path("profile").spliterator(), false).map(JsonNode::asText))
                .containsExactly("http://hl7.org/fhir/uv/cql/StructureDefinition/cql-library",
                        "http://hl7.org/fhir/uv/cql/StructureDefinition/elm-json-library",
                        "http://hl7.org/fhir/uv/crmi/StructureDefinition/crmi-shareablelibrary");
        assertThat(new String(Base64.getDecoder().decode(primary.path("content").get(0).path("data").asText()),
                StandardCharsets.UTF_8)).startsWith("library DiabetesHbA1cControl");
        assertThat(primary.path("content").get(1).path("contentType").asText()).isEqualTo("application/elm+json");
        assertThat(primary.path("parameter").get(0).path("name").asText()).isEqualTo("Measurement Period");
        // The Measure references exactly this library by canonical URL.
        assertThat(resources(bundle, "Measure").get(0).path("library").get(0).asText())
                .isEqualTo(primary.path("url").asText() + "|1.2.0");
    }

    @Test
    void everyEntryIsAddressedByItsCanonicalUrl() {
        ObjectNode bundle = service.exportAsBundle(7L);
        for (JsonNode entry : bundle.path("entry")) {
            assertThat(entry.path("fullUrl").asText()).isEqualTo(entry.path("resource").path("url").asText());
        }
    }

    @Test
    void valueSetResolvedHere_isPackagedInFull_theOtherOneIsAFlaggedStub() {
        BundleExport export = service.exportWithConformance(7L);
        List<JsonNode> valueSets = resources(export.bundle(), "ValueSet");

        // Both the main library's value set and the one only the INCLUDED library uses are there,
        // keyed by their real URLs (the old export used the CQL value set NAME as the url).
        assertThat(valueSets).extracting(v -> v.path("url").asText()).containsExactly(VS_KNOWN, VS_UNKNOWN);
        assertThat(valueSets.get(0).path("compose").path("include").get(0).path("concept").get(0).path("code").asText())
                .isEqualTo("E11.9");
        assertThat(valueSets.get(1).has("compose")).isFalse();
        assertThat(valueSets.get(1).path("status").asText()).isEqualTo("unknown");

        MeasureExportConformance report = export.conformance();
        assertThat(report.getValueSets()).extracting(MeasureExportConformance.ValueSetStatus::isIncluded)
                .containsExactly(true, false);
        assertThat(report.getValueSets().get(0).getSource()).isEqualTo("ig");
        assertThat(report.getIssues()).anyMatch(i -> "ValueSet".equals(i.getElement()) && i.getMessage().contains(VS_UNKNOWN));
    }

    @Test
    void includedLibraryThatIsNotStoredHere_isReported_butFhirHelpersIsNot() {
        MeasureExportConformance report = service.exportConformance(7L);

        assertThat(report.getIssues()).anyMatch(i -> i.getMessage().contains("'DeepHelpers'"));
        assertThat(report.getIssues()).noneMatch(i -> i.getMessage().contains("FHIRHelpers"));
        assertThat(report.getLibraryProfiles()).contains("http://hl7.org/fhir/uv/cql/StructureDefinition/cql-library");
    }

    @Test
    void xmlExportIsRealFhirXml() {
        String xml = service.exportAsBundleXml(7L);

        assertThat(xml).contains("<Bundle xmlns=\"http://hl7.org/fhir\">");
        assertThat(xml).contains("<library value=\"" + BASE + "/Library/DiabetesHbA1cControl|1.2.0\"/>");
        // The old hand-written XML only had id / name / version / status / url per resource.
        assertThat(xml).contains("<scoring>").contains("<population id=\"group-1-numerator\">").contains("<compose>");
    }
}
