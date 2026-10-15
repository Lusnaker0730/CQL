package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureExportConformance;
import com.cqlplatform.service.measure.FhirMeasureBundleService.BundleExport;
import com.cqlplatform.service.measure.MeasurePackageService.MeasurePackage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** PAT-261: the exchange package zip in MADiE's layout. */
@ExtendWith(MockitoExtension.class)
class MeasurePackageServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CQL = "library DiabetesHbA1cControl version '1.2.0'\n\ndefine \"IP\": true\n";

    @Mock private FhirMeasureBundleService bundleService;
    private MeasurePackageService service;
    private MeasureDefinition definition;

    @BeforeEach
    void setUp() {
        service = new MeasurePackageService(bundleService, new HumanReadableService(), FhirContext.forR4Cached());
        definition = MeasureDefinition.builder().id(7L).name("DiabetesHbA1cControl").version("1.2.0")
                .ecqmTitle("DM-A1C").title("Diabetes: HbA1c control").status("active").scoringType("proportion")
                .cqlContent(CQL).build();

        ObjectNode bundle = MAPPER.createObjectNode();
        bundle.put("resourceType", "Bundle").put("type", "collection");
        ArrayNode entries = bundle.putArray("entry");
        ObjectNode measure = entries.addObject().putObject("resource");
        measure.put("resourceType", "Measure").put("id", "DiabetesHbA1cControl").put("name", "DiabetesHbA1cControl")
                .put("version", "1.2.0").put("status", "active").put("url", "https://h.example.tw/fhir/Measure/DiabetesHbA1cControl");
        ObjectNode library = entries.addObject().putObject("resource");
        library.put("resourceType", "Library").put("id", "DiabetesHbA1cControl").put("name", "DiabetesHbA1cControl")
                .put("version", "1.2.0").put("status", "active");
        ArrayNode content = library.putArray("content");
        content.addObject().put("contentType", "text/cql")
                .put("data", Base64.getEncoder().encodeToString(CQL.getBytes(StandardCharsets.UTF_8)));
        content.addObject().put("contentType", "application/elm+json")
                .put("data", Base64.getEncoder().encodeToString("{\"library\":{}}".getBytes(StandardCharsets.UTF_8)));
        ObjectNode valueSet = entries.addObject().putObject("resource");
        valueSet.put("resourceType", "ValueSet").put("id", "diabetes").put("version", "2.0")
                .put("url", "https://h.example.tw/fhir/ValueSet/diabetes").put("status", "active");

        MeasureExportConformance report = new MeasureExportConformance();
        report.getProfiles().add("http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/computable-measure-cqfm");
        lenient().when(bundleService.exportWithConformance(7L)).thenReturn(new BundleExport(bundle, report, definition));
    }

    @Test
    void zipHasMadiesLayout_bundleJsonXmlHtml_cqlFiles_andPerResourceFiles() throws IOException {
        MeasurePackage pkg = service.exportPackage(7L);

        assertThat(pkg.fileName()).isEqualTo("DM-A1C-v1.2.0-FHIR");
        Map<String, byte[]> entries = unzip(pkg.zip());
        assertThat(entries.keySet()).containsExactlyInAnyOrder(
                "DM-A1C-v1.2.0-FHIR.json",
                "DM-A1C-v1.2.0-FHIR.xml",
                "DM-A1C-v1.2.0-FHIR.html",
                "conformance.json",
                "cql/DiabetesHbA1cControl-1.2.0.cql",
                "resources/measure-DiabetesHbA1cControl-1.2.0.json",
                "resources/measure-DiabetesHbA1cControl-1.2.0.xml",
                "resources/library-DiabetesHbA1cControl-1.2.0.json",
                "resources/library-DiabetesHbA1cControl-1.2.0.xml",
                "resources/valueset-diabetes-2.0.json");

        assertThat(text(entries, "cql/DiabetesHbA1cControl-1.2.0.cql")).isEqualTo(CQL);
        assertThat(text(entries, "DM-A1C-v1.2.0-FHIR.xml")).startsWith("<Bundle xmlns=\"http://hl7.org/fhir\">");
        assertThat(text(entries, "resources/measure-DiabetesHbA1cControl-1.2.0.xml")).startsWith("<Measure xmlns=\"http://hl7.org/fhir\">");
        assertThat(text(entries, "DM-A1C-v1.2.0-FHIR.html")).contains("<!DOCTYPE html>").contains("Diabetes: HbA1c control");
        assertThat(MAPPER.readTree(entries.get("DM-A1C-v1.2.0-FHIR.json")).path("entry")).hasSize(3);
        assertThat(MAPPER.readTree(entries.get("conformance.json")).path("profiles").get(0).asText()).endsWith("computable-measure-cqfm");
        assertThat(MAPPER.readTree(entries.get("resources/library-DiabetesHbA1cControl-1.2.0.json")).path("resourceType").asText()).isEqualTo("Library");
    }

    @Test
    void fileName_fallsBackToTheMeasureName_andKeepsOnlyFileSystemSafeCharacters() {
        assertThat(MeasurePackageService.baseName(MeasureDefinition.builder().name("Smoke/Measure: v1").version("1.0.0").build()))
                .isEqualTo("Smoke_Measure_v1-v1.0.0-FHIR");
        assertThat(MeasurePackageService.baseName(MeasureDefinition.builder().name("X").ecqmTitle("  ").build()))
                .isEqualTo("X-v1.0.0-FHIR");
        assertThat(MeasurePackageService.safe("///")).isEqualTo("measure");
    }

    @Test
    void looksLikeZip_byNameOrSignature() {
        assertThat(MeasurePackageService.looksLikeZip("{}".getBytes(StandardCharsets.UTF_8), "bundle.json")).isFalse();
        assertThat(MeasurePackageService.looksLikeZip("{}".getBytes(StandardCharsets.UTF_8), "package.ZIP")).isTrue();
        assertThat(MeasurePackageService.looksLikeZip(new byte[]{'P', 'K', 3, 4, 0, 0}, null)).isTrue();
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                out.put(entry.getName(), in.readAllBytes());
            }
        }
        return out;
    }

    private static String text(Map<String, byte[]> entries, String name) {
        return new String(entries.get(name), StandardCharsets.UTF_8);
    }
}
