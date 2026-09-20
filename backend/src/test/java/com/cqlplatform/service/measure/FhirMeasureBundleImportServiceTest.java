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
    @InjectMocks private FhirMeasureBundleImportService service;

    @BeforeEach
    void setUp() {
        lenient().when(cqlLibraryService.getLibraryByNameAndVersion(anyString(), anyString())).thenReturn(Optional.empty());
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
}
