package com.cqlplatform.service.measure;

import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.FhirLibraryService;
import com.cqlplatform.service.measure.FhirMeasureBundleImportService.BundleImportResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-229 — importing a package must give the measure its logic. Before, the imported measure
 * had populations but no CQL, so it could not be evaluated.
 */
@ExtendWith(MockitoExtension.class)
class FhirMeasureBundleImportServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MAIN_CQL = "library DiabetesHbA1cControl version '1.2.0'\ndefine \"Initial Population\": true";
    private static final String DEP_CQL = "library HospitalCommon version '2.0.0'";

    @Mock private FhirMeasureService fhirMeasureService;
    @Mock private FhirLibraryService fhirLibraryService;
    @Mock private CqlLibraryService cqlLibraryService;
    @Mock private com.cqlplatform.service.terminology.PlatformValueSetService platformValueSets;
    @InjectMocks private FhirMeasureBundleImportService service;

    @BeforeEach
    void setUp() {
        lenient().when(cqlLibraryService.getLibraryByNameAndVersion(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(platformValueSets.importFhirIfAbsent(any(JsonNode.class))).thenReturn(
                new com.cqlplatform.service.terminology.PlatformValueSetService.ImportOutcome(
                        com.cqlplatform.model.terminology.PlatformValueSet.builder().id(5L).url("https://x/ValueSet/a").version("1.0.0").build(), true));
        lenient().when(fhirMeasureService.importFhirMeasure(any(JsonNode.class), any()))
                .thenReturn(MeasureDefinition.builder().id(1L).name("DiabetesHbA1cControl").version("1.2.0").build());
    }

    private static String library(String name, String version, String url, String cql) {
        return """
                {"resource":{"resourceType":"Library","id":"%s","name":"%s","version":"%s"%s,
                 "content":[{"contentType":"text/cql","data":"%s"}]}}"""
                .formatted(name, name, version, url != null ? ",\"url\":\"" + url + "\"" : "",
                        Base64.getEncoder().encodeToString(cql.getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonNode bundle(String measureLibraryRef, String... libraries) throws Exception {
        String measure = "{\"resource\":{\"resourceType\":\"Measure\",\"name\":\"DiabetesHbA1cControl\",\"version\":\"1.2.0\""
                + (measureLibraryRef != null ? ",\"library\":[\"" + measureLibraryRef + "\"]" : "") + "}}";
        return MAPPER.readTree("{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + String.join(",", libraries) + "," + measure
                + ",{\"resource\":{\"resourceType\":\"ValueSet\",\"url\":\"https://x/ValueSet/a\"}}]}");
    }

    @Test
    void primaryLibraryIsFoundByCanonicalUrl_andItsCqlBecomesTheMeasuresLogic() throws Exception {
        String base = "https://hospital.example.tw/fhir/Library/";
        // The dependency comes FIRST in the bundle: position must not decide which one is primary.
        JsonNode bundle = bundle(base + "DiabetesHbA1cControl|1.2.0",
                library("HospitalCommon", "2.0.0", base + "HospitalCommon", DEP_CQL),
                library("DiabetesHbA1cControl", "1.2.0", base + "DiabetesHbA1cControl", MAIN_CQL));

        BundleImportResult result = service.importBundle(bundle);

        verify(fhirMeasureService).importFhirMeasure(any(JsonNode.class), eq(MAIN_CQL));
        // Only the dependency is stored as a shared library; the primary one lives in the measure.
        ArgumentCaptor<JsonNode> imported = ArgumentCaptor.forClass(JsonNode.class);
        verify(fhirLibraryService, times(1)).importFhirLibrary(imported.capture());
        assertThat(imported.getValue().path("name").asText()).isEqualTo("HospitalCommon");
        assertThat(result.librariesImported()).isEqualTo(1);
        assertThat(result.valueSetsFound()).isEqualTo(1);
        // PAT-230: the package's value set is stored, not just counted.
        assertThat(result.valueSetsImported()).isEqualTo(1);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void valueSetAlreadyHere_orWithoutCodes_isSkippedWithAReason_andTheMeasureStillImports() throws Exception {
        when(platformValueSets.importFhirIfAbsent(any(JsonNode.class)))
                .thenReturn(new com.cqlplatform.service.terminology.PlatformValueSetService.ImportOutcome(
                        com.cqlplatform.model.terminology.PlatformValueSet.builder().id(5L).url("https://x/ValueSet/a").version("1.0.0").build(), false));
        BundleImportResult existing = service.importBundle(bundle(null, library("DiabetesHbA1cControl", "1.2.0", null, MAIN_CQL)));
        assertThat(existing.valueSetsSkipped()).isEqualTo(1);
        assertThat(existing.warnings()).singleElement().asString().contains("already exists here").contains("https://x/ValueSet/a");

        when(platformValueSets.importFhirIfAbsent(any(JsonNode.class)))
                .thenThrow(new com.cqlplatform.exception.ValidationException("ValueSet cannot be imported",
                        java.util.List.of("The definition is rule-based and the resource carries no expansion.")));
        BundleImportResult stub = service.importBundle(bundle(null, library("DiabetesHbA1cControl", "1.2.0", null, MAIN_CQL)));
        assertThat(stub.valueSetsImported()).isZero();
        assertThat(stub.warnings()).singleElement().asString().contains("was not imported").contains("rule-based");
        assertThat(stub.measure()).isNotNull();
    }

    @Test
    void primaryLibraryIsFoundByName_whenThePackageHasNoLibraryUrls() throws Exception {
        JsonNode bundle = bundle("Library/DiabetesHbA1cControl",
                library("HospitalCommon", "2.0.0", null, DEP_CQL),
                library("DiabetesHbA1cControl", "1.2.0", null, MAIN_CQL));

        service.importBundle(bundle);

        verify(fhirMeasureService).importFhirMeasure(any(JsonNode.class), eq(MAIN_CQL));
    }

    @Test
    void singleLibraryWithoutAReference_isThePrimaryOne() throws Exception {
        service.importBundle(bundle(null, library("DiabetesHbA1cControl", "1.2.0", null, MAIN_CQL)));

        verify(fhirMeasureService).importFhirMeasure(any(JsonNode.class), eq(MAIN_CQL));
        verify(fhirLibraryService, never()).importFhirLibrary(any());
    }

    @Test
    void severalLibrariesAndNoReference_importsThemButDoesNotGuessThePrimary() throws Exception {
        service.importBundle(bundle(null,
                library("HospitalCommon", "2.0.0", null, DEP_CQL),
                library("DiabetesHbA1cControl", "1.2.0", null, MAIN_CQL)));

        verify(fhirMeasureService).importFhirMeasure(any(JsonNode.class), isNull());
        verify(fhirLibraryService, times(2)).importFhirLibrary(any());
    }

    @Test
    void dependencyAlreadyStoredHere_isSkipped() throws Exception {
        when(cqlLibraryService.getLibraryByNameAndVersion("HospitalCommon", "2.0.0"))
                .thenReturn(Optional.of(CqlLibrary.builder().id("c1").name("HospitalCommon").version("2.0.0").build()));
        String base = "https://hospital.example.tw/fhir/Library/";

        BundleImportResult result = service.importBundle(bundle(base + "DiabetesHbA1cControl|1.2.0",
                library("HospitalCommon", "2.0.0", base + "HospitalCommon", DEP_CQL),
                library("DiabetesHbA1cControl", "1.2.0", base + "DiabetesHbA1cControl", MAIN_CQL)));

        verify(fhirLibraryService, never()).importFhirLibrary(any());
        assertThat(result.librariesSkipped()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ PAT-261 packages

    private static byte[] zip(java.util.Map<String, String> entries) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (var e : entries.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void packageZip_importsTheTopLevelBundle_andAsVersionRewritesMeasureVersion() throws Exception {
        String bundle = bundle(null, library("DiabetesHbA1cControl", "1.2.0", null, MAIN_CQL)).toString();
        java.util.Map<String, String> entries = new java.util.LinkedHashMap<>();
        entries.put("conformance.json", "{\"profiles\":[],\"issues\":[]}");
        entries.put("resources/measure-DiabetesHbA1cControl-1.2.0.json", "{\"resourceType\":\"Measure\",\"name\":\"DiabetesHbA1cControl\"}");
        entries.put("cql/DiabetesHbA1cControl-1.2.0.cql", MAIN_CQL);
        entries.put("DM-A1C-v1.2.0-FHIR.json", bundle);
        entries.put("DM-A1C-v1.2.0-FHIR.xml", "<Bundle xmlns=\"http://hl7.org/fhir\"/>");

        BundleImportResult result = service.importPackage(zip(entries), "DM-A1C-v1.2.0-FHIR.zip", "1.3.0-imported");

        assertThat(result.measure()).isNotNull();
        ArgumentCaptor<JsonNode> measure = ArgumentCaptor.forClass(JsonNode.class);
        verify(fhirMeasureService).importFhirMeasure(measure.capture(), eq(MAIN_CQL));
        assertThat(measure.getValue().path("version").asText()).isEqualTo("1.3.0-imported");
    }

    @Test
    void packageZip_withTheBundleOnlyInsideAFolder_stillFindsIt_andKeepsTheSendersVersionWithoutAsVersion() throws Exception {
        String bundle = bundle(null, library("DiabetesHbA1cControl", "1.2.0", null, MAIN_CQL)).toString();
        byte[] zip = zip(java.util.Map.of("export/nested/bundle.json", bundle, "readme.txt", "hello"));

        service.importPackage(zip, "whatever.zip", "   ");

        ArgumentCaptor<JsonNode> measure = ArgumentCaptor.forClass(JsonNode.class);
        verify(fhirMeasureService).importFhirMeasure(measure.capture(), eq(MAIN_CQL));
        assertThat(measure.getValue().path("version").asText()).isEqualTo("1.2.0");
    }

    @Test
    void packageWithoutABundle_orEmpty_isRefused() throws Exception {
        byte[] noBundle = zip(java.util.Map.of("resources/measure-x.json", "{\"resourceType\":\"Measure\"}", "notes.json", "not json"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importPackage(noBundle, "x.zip", null))
                .isInstanceOf(com.cqlplatform.exception.ValidationException.class)
                .hasMessageContaining("no FHIR Bundle");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importPackage(new byte[0], "x.zip", null))
                .isInstanceOf(com.cqlplatform.exception.ValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importPackage("<xml/>".getBytes(StandardCharsets.UTF_8), "bundle.xml", null))
                .isInstanceOf(com.cqlplatform.exception.ValidationException.class)
                .hasMessageContaining("neither a zip nor valid JSON");
    }

    @Test
    void plainBundleJsonUpload_takesTheJsonPath() throws Exception {
        String bundle = bundle(null, library("DiabetesHbA1cControl", "1.2.0", null, MAIN_CQL)).toString();

        BundleImportResult result = service.importPackage(bundle.getBytes(StandardCharsets.UTF_8), "bundle.json", "2.0.0");

        assertThat(result.measure()).isNotNull();
        ArgumentCaptor<JsonNode> measure = ArgumentCaptor.forClass(JsonNode.class);
        verify(fhirMeasureService).importFhirMeasure(measure.capture(), eq(MAIN_CQL));
        assertThat(measure.getValue().path("version").asText()).isEqualTo("2.0.0");
    }
}
