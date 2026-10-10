package com.cqlplatform.service.measure;

import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.BatchTestCaseImportResult;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.model.measure.TestCaseExpectedValues;
import com.cqlplatform.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PAT-247 — MADiE-compatible test case exchange: export writes one collection Bundle per test case
 * with a {@code test-case-cqfm} MeasureReport carrying the expectation; import reads the report back
 * into the structured expectation (and copes with MADiE's own files, bad JSON, and expectations
 * the measure rejects).
 */
@ExtendWith(MockitoExtension.class)
class TestCaseBundleServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private TestCaseRepository repository;
    @Mock private MeasureDefinitionService definitionService;
    @Mock private TestCaseService testCaseService;

    private final FhirCanonicalResolver canonical = new FhirCanonicalResolver("https://cql.example.org/fhir", "");
    private TestCaseBundleService service;

    @BeforeEach
    void setUp() {
        service = new TestCaseBundleService(repository, definitionService, testCaseService, canonical);
    }

    private MeasureDefinition measure() {
        return MeasureDefinition.builder()
                .id(10L).name("HbA1cControl").version("1.2.0").status("active").ownerUsername("user")
                .measurementPeriodStart(java.time.LocalDate.of(2025, 1, 1))
                .measurementPeriodEnd(java.time.LocalDate.of(2025, 12, 31))
                .groupDefinitions(List.of(GroupDefinition.builder().groupId("group-1")
                        .populations(List.of(
                                PopulationDefinition.builder().populationType("initial-population").criteriaExpression("IP").build(),
                                PopulationDefinition.builder().populationType("denominator").criteriaExpression("D").build(),
                                PopulationDefinition.builder().populationType("numerator").criteriaExpression("N").build()))
                        .build()))
                .build();
    }

    private static String patientBundle(String patientId, String given, String family) {
        return "{\"resourceType\":\"Bundle\",\"type\":\"transaction\",\"entry\":["
                + "{\"fullUrl\":\"Patient/" + patientId + "\",\"resource\":{\"resourceType\":\"Patient\",\"id\":\"" + patientId + "\","
                + "\"name\":[{\"family\":\"" + family + "\",\"given\":[\"" + given + "\"]}],\"gender\":\"female\",\"birthDate\":\"1960-05-01\"},"
                + "\"request\":{\"method\":\"PUT\",\"url\":\"Patient/" + patientId + "\"}},"
                + "{\"resource\":{\"resourceType\":\"Encounter\",\"id\":\"enc-1\",\"status\":\"finished\",\"subject\":{\"reference\":\"Patient/" + patientId + "\"}}}]}";
    }

    private static TestCaseEntity structuredCase() {
        TestCaseEntity e = TestCaseEntity.builder()
                .id(1L).measureDefinitionId(10L).title("Mei controlled").description("HbA1c 6.8 within period")
                .series("adults").patientBundleJson(patientBundle("p-mei", "Mei", "Chen"))
                .expectedValues("{\"groups\":[{\"groupId\":\"group-1\",\"populations\":{\"initial-population\":1,\"denominator\":1,\"numerator\":1},"
                        + "\"observations\":[6.8,7],\"stratifiers\":{\"gender\":\"female\"}}]}")
                .build();
        e.getExpectedPopulationMap().put("initial-population", true);
        e.getExpectedPopulationMap().put("denominator", true);
        e.getExpectedPopulationMap().put("numerator", true);
        return e;
    }

    private static TestCaseEntity legacyCase() {
        TestCaseEntity e = TestCaseEntity.builder()
                .id(2L).measureDefinitionId(10L).title("Wei excluded")
                .patientBundleJson(patientBundle("p-wei", "Wei", "Lin"))
                .build();
        e.getExpectedPopulationMap().put("initial-population", true);
        e.getExpectedPopulationMap().put("denominator", false);
        e.getExpectedPopulationMap().put("numerator", false);
        return e;
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) out.put(entry.getName(), in.readAllBytes());
        }
        return out;
    }

    private static JsonNode reportOf(JsonNode bundle) {
        for (JsonNode entry : bundle.path("entry")) {
            if ("MeasureReport".equals(entry.path("resource").path("resourceType").asText())) return entry.path("resource");
        }
        return null;
    }

    @Test
    void exportZip_writesOneBundlePerTestCase_withTheTestCaseMeasureReport() throws Exception {
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure()));
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(structuredCase(), legacyCase()));

        Map<String, byte[]> files = unzip(service.exportZip(10L, null));

        assertThat(files.keySet()).containsExactly(
                "p-mei/HbA1cControl-v1.2.0-adults-Meicontrolled.json",
                "p-wei/HbA1cControl-v1.2.0-Weiexcluded.json",
                "README.txt");

        JsonNode mei = MAPPER.readTree(files.get("p-mei/HbA1cControl-v1.2.0-adults-Meicontrolled.json"));
        assertThat(mei.path("type").asText()).isEqualTo("collection");
        assertThat(mei.path("entry")).hasSize(3);
        assertThat(mei.path("entry").path(0).has("request")).as("transaction request lines are dropped").isFalse();
        JsonNode report = reportOf(mei);
        assertThat(report).isNotNull();
        assertThat(report.path("meta").path("profile").path(0).asText()).isEqualTo(TestCaseBundleService.PROFILE_TEST_CASE);
        assertThat(report.path("modifierExtension").path(0).path("url").asText()).isEqualTo(TestCaseBundleService.EXT_IS_TEST_CASE);
        assertThat(report.path("modifierExtension").path(0).path("valueBoolean").asBoolean()).isTrue();
        assertThat(report.path("status").asText()).isEqualTo("complete");
        assertThat(report.path("type").asText()).isEqualTo("individual");
        assertThat(report.path("measure").asText()).isEqualTo("https://cql.example.org/fhir/Measure/HbA1cControl|1.2.0");
        assertThat(report.path("period").path("start").asText()).isEqualTo("2025-01-01");
        assertThat(report.path("period").path("end").asText()).isEqualTo("2025-12-31");
        // the subject lives in the contained Parameters the cqf-inputParameters extension points at
        assertThat(report.path("contained").path(0).path("parameter").path(0).path("valueString").asText()).isEqualTo("p-mei");
        assertThat(report.path("extension").path(0).path("url").asText()).isEqualTo(TestCaseBundleService.EXT_INPUT_PARAMETERS);
        assertThat(report.path("extension").path(0).path("valueReference").path("reference").asText()).startsWith("#");
        assertThat(report.path("extension").path(1).path("valueMarkdown").asText()).isEqualTo("HbA1c 6.8 within period");

        JsonNode group = report.path("group").path(0);
        assertThat(group.path("id").asText()).isEqualTo("group-1");
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<Double> observations = new ArrayList<>();
        for (JsonNode population : group.path("population")) {
            String code = population.path("code").path("coding").path(0).path("code").asText();
            assertThat(population.path("code").path("coding").path(0).path("system").asText()).isEqualTo(TestCaseBundleService.MEASURE_POPULATION_SYSTEM);
            if (TestCaseBundleService.CODE_MEASURE_OBSERVATION.equals(code)) {
                observations.add(population.path("extension").path(0).path("valueDecimal").asDouble());
            } else {
                counts.put(code, population.path("count").asInt());
            }
        }
        assertThat(counts).containsExactly(Map.entry("initial-population", 1), Map.entry("denominator", 1), Map.entry("numerator", 1));
        assertThat(observations).containsExactly(6.8, 7.0);
        assertThat(group.path("stratifier").path(0).path("id").asText()).isEqualTo("gender");
        assertThat(group.path("stratifier").path(0).path("stratum").path(0).path("value").path("text").asText()).isEqualTo("female");
        assertThat(report.path("evaluatedResource")).hasSize(2);

        // the legacy boolean map becomes 1 / 0 counts on the first group
        JsonNode weiReport = reportOf(MAPPER.readTree(files.get("p-wei/HbA1cControl-v1.2.0-Weiexcluded.json")));
        Map<String, Integer> weiCounts = new LinkedHashMap<>();
        for (JsonNode population : weiReport.path("group").path(0).path("population")) {
            weiCounts.put(population.path("code").path("coding").path(0).path("code").asText(), population.path("count").asInt());
        }
        assertThat(weiCounts).containsExactly(Map.entry("initial-population", 1), Map.entry("denominator", 0), Map.entry("numerator", 0));
        assertThat(weiReport.path("extension").path(1).path("valueMarkdown").asText()).as("description falls back to the title").isEqualTo("Wei excluded");
    }

    @Test
    void roundTrip_importRestoresTheExpectation_namesFromThePatient_andLeavesTheReportOutOfTheBundle() throws Exception {
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure()));
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(structuredCase()));
        byte[] zip = service.exportZip(10L, null);

        AtomicLong ids = new AtomicLong(100);
        when(testCaseService.create(eq(10L), any())).thenAnswer(inv -> {
            TestCase tc = inv.getArgument(1);
            tc.setId(ids.incrementAndGet());
            return tc;
        });

        BatchTestCaseImportResult result = service.importFile(10L, zip, "HbA1cControl-test-cases.zip");

        assertThat(result.getTotalReceived()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
        ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseService).create(eq(10L), captor.capture());
        TestCase imported = captor.getValue();
        assertThat(imported.getTitle()).as("MADiE derives the title from the Patient's given name").isEqualTo("Mei");
        assertThat(imported.getSeries()).as("… and the series from the family name").isEqualTo("Chen");
        assertThat(imported.getDescription()).isEqualTo("HbA1c 6.8 within period");
        TestCaseExpectedValues expected = imported.getExpectedValues();
        assertThat(expected.getGroups()).hasSize(1);
        TestCaseExpectedValues.GroupValues group = expected.getGroups().get(0);
        assertThat(group.getGroupId()).isEqualTo("group-1");
        assertThat(group.getPopulations()).containsExactly(Map.entry("initial-population", 1), Map.entry("denominator", 1), Map.entry("numerator", 1));
        assertThat(group.getObservations()).containsExactly(6.8, 7.0);
        assertThat(group.getStratifiers()).containsExactly(Map.entry("gender", "female"));
        assertThat(imported.getExpectedPopulations()).containsExactly(Map.entry("initial-population", true), Map.entry("denominator", true), Map.entry("numerator", true));
        JsonNode stored = MAPPER.readTree(imported.getPatientBundleJson());
        assertThat(stored.path("entry")).as("the MeasureReport does not become patient data").hasSize(2);
        assertThat(reportOf(stored)).isNull();
    }

    @Test
    void importFile_readsAMadieStyleBundle_matchingGroupsByPositionAndDecimalsFromCount() {
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure()));
        when(testCaseService.create(eq(10L), any())).thenAnswer(inv -> inv.getArgument(1));
        // MADiE: group id is a UUID that means nothing to us, no platform extension on the observation,
        // the stratifier carries its true/false pair (no single expectation), and the measure URL is theirs.
        String madie = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":["
                + "{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"abc\",\"name\":[{\"family\":\"Series1\",\"given\":[\"Case\",\"One\"]}]}},"
                + "{\"resource\":{\"resourceType\":\"MeasureReport\",\"id\":\"mr\",\"meta\":{\"profile\":[\"" + TestCaseBundleService.PROFILE_TEST_CASE + "\"]},"
                + "\"modifierExtension\":[{\"url\":\"" + TestCaseBundleService.EXT_IS_TEST_CASE + "\",\"valueBoolean\":true}],"
                + "\"status\":\"complete\",\"type\":\"individual\",\"measure\":\"https://madie.cms.gov/Measure/CMS122FHIR|1.0.000\","
                + "\"group\":[{\"id\":\"64b0e9f2c1d2a3b4c5d6e7f8\",\"population\":["
                + "{\"code\":{\"coding\":[{\"system\":\"" + TestCaseBundleService.MEASURE_POPULATION_SYSTEM + "\",\"code\":\"initial-population\"}]},\"count\":1},"
                + "{\"code\":{\"coding\":[{\"system\":\"" + TestCaseBundleService.MEASURE_POPULATION_SYSTEM + "\",\"code\":\"denominator\"}]},\"count\":1},"
                + "{\"code\":{\"coding\":[{\"system\":\"" + TestCaseBundleService.MEASURE_POPULATION_SYSTEM + "\",\"code\":\"numerator\"}]},\"count\":0},"
                + "{\"code\":{\"coding\":[{\"system\":\"" + TestCaseBundleService.MEASURE_POPULATION_SYSTEM + "\",\"code\":\"measure-observation\"}]},\"count\":3}],"
                + "\"stratifier\":[{\"id\":\"strat-1\",\"stratum\":[{\"value\":{\"text\":\"true\"}},{\"value\":{\"text\":\"false\"}}]}]}]}}]}";

        BatchTestCaseImportResult result = service.importFile(10L, madie.getBytes(StandardCharsets.UTF_8), "CMS122FHIR-v1.0.000-Series1-CaseOne.json");

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings().get(0)).contains("written for measure https://madie.cms.gov/Measure/CMS122FHIR|1.0.000");
        ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseService).create(eq(10L), captor.capture());
        TestCase imported = captor.getValue();
        assertThat(imported.getTitle()).isEqualTo("Case One");
        assertThat(imported.getSeries()).isEqualTo("Series1");
        TestCaseExpectedValues.GroupValues group = imported.getExpectedValues().getGroups().get(0);
        assertThat(group.getGroupId()).as("an unknown group id falls back to the measure's group at that position").isEqualTo("group-1");
        assertThat(group.getPopulations()).containsEntry("numerator", 0).containsEntry("denominator", 1);
        assertThat(group.getObservations()).containsExactly(3.0);
        assertThat(group.getStratifiers()).as("a true/false stratum pair carries no single expectation").isNull();
        assertThat(imported.getExpectedPopulations()).containsEntry("numerator", false);
    }

    @Test
    void importFile_dropsAnExpectationTheMeasureRejects_andKeepsTheTestCase_withAWarning() {
        MeasureDefinition measure = measure();
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure));
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(structuredCase()));
        byte[] zip = service.exportZip(10L, null);
        when(testCaseService.create(eq(10L), any())).thenAnswer(inv -> {
            TestCase tc = inv.getArgument(1);
            if (tc.getExpectedValues() != null) {
                throw new ValidationException("Invalid expected values", List.of("Unknown stratifier 'gender' in group 'group-1'"));
            }
            return tc;
        });

        BatchTestCaseImportResult result = service.importFile(10L, zip, "x.zip");

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings().get(0)).contains("'Mei'").contains("expected values dropped").contains("Unknown stratifier 'gender'");
        verify(testCaseService, times(2)).create(eq(10L), any());
    }

    @Test
    void importFile_takesAnArrayOrOneBundle_reportsNonBundlesAndBadJson_asErrorsNotFailures() {
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure()));
        when(testCaseService.create(eq(10L), any())).thenAnswer(inv -> inv.getArgument(1));
        String array = "[" + patientBundle("a", "A", "X") + ",{\"resourceType\":\"Patient\",\"id\":\"loose\"}," + patientBundle("b", "B", "Y") + "]";

        BatchTestCaseImportResult result = service.importFile(10L, array.getBytes(StandardCharsets.UTF_8), "two.json");

        assertThat(result.getTotalReceived()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors().get(0)).startsWith("two-2: ").contains("not a FHIR Bundle");
        ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseService, times(2)).create(eq(10L), captor.capture());
        assertThat(captor.getAllValues()).extracting(TestCase::getTitle).containsExactly("A", "B");
        assertThat(captor.getAllValues().get(0).getExpectedValues()).as("no MeasureReport → no expectation").isNull();
        assertThat(captor.getAllValues().get(0).getPatientBundleJson()).as("transaction bundles become collections").contains("\"type\":\"collection\"").doesNotContain("\"request\"");

        assertThatBadJsonIsRefused();
    }

    private void assertThatBadJsonIsRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importFile(10L, "{not json".getBytes(StandardCharsets.UTF_8), "bad.json"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void exportZip_refusesAMeasureWithoutTestCases_andFiltersByIds() throws Exception {
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure()));
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.exportZip(10L, null))
                .isInstanceOf(ValidationException.class);

        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(structuredCase(), legacyCase()));
        Map<String, byte[]> files = unzip(service.exportZip(10L, List.of(2L)));
        assertThat(files.keySet()).containsExactly("p-wei/HbA1cControl-v1.2.0-Weiexcluded.json", "README.txt");
    }
}
