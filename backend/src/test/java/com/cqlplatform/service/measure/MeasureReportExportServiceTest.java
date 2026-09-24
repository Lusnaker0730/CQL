package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.GroupResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.PopulationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.StratifierResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * PAT-233 — the FHIR MeasureReport export groups strata under their stratifier. The platform
 * keeps one row per (stratifier, stratum); the export used to emit one code-less
 * {@code stratifier} per row, so a gender stratifier came out as two stratifiers nobody could
 * tell apart.
 */
@ExtendWith(MockitoExtension.class)
class MeasureReportExportServiceTest {

    @Mock private MeasureReportService reportService;
    @Mock private QrdaExportService qrdaExportService;
    @Mock private NormalizedMeasureReportReader reportReader;
    @Mock private FhirCanonicalResolver canonical;
    @InjectMocks private MeasureReportExportService exportService;

    private static StratifierResult stratum(String id, String value, int ip, int denom, int numer, Double score) {
        return StratifierResult.builder().strataId(id).strataValue(value).measureScore(score)
                .populations(List.of(
                        PopulationResult.builder().populationType("initial-population").count(ip).build(),
                        PopulationResult.builder().populationType("denominator").count(denom).build(),
                        PopulationResult.builder().populationType("numerator").count(numer).build()))
                .build();
    }

    @Test
    void fhirExport_groupsStrataUnderOneStratifierPerId_withTheStratifierCode() throws Exception {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .groups(List.of(GroupResult.builder().groupId("group-1").measureScore(60.0)
                        .populations(List.of(PopulationResult.builder().populationType("initial-population").count(7).build()))
                        .stratifiers(List.of(
                                stratum("sex", "female", 4, 3, 1, 33.33),
                                stratum("age", "18-49", 2, 0, 0, null),
                                stratum("sex", "male", 3, 2, 2, 100.0),
                                stratum("age", "65+", 3, 3, 3, 100.0)))
                        .build()))
                .build();
        MeasureReportEntity report = MeasureReportEntity.builder().id(42L).measureName("Demo")
                .periodStart(LocalDate.of(2022, 1, 1)).periodEnd(LocalDate.of(2022, 6, 30)).build();
        when(reportService.getReport(42L)).thenReturn(Optional.of(report));
        when(reportReader.reconstruct(42L)).thenReturn(Optional.of(result));

        byte[] body = exportService.exportReport(42L, "fhir").getBody();
        JsonNode measureReport = new ObjectMapper().readTree(body);

        JsonNode stratifiers = measureReport.path("group").get(0).path("stratifier");
        assertThat(stratifiers).hasSize(2);
        assertThat(stratifiers.get(0).path("code").path("text").asText()).isEqualTo("sex");
        assertThat(stratifiers.get(1).path("code").path("text").asText()).isEqualTo("age");

        JsonNode sexStrata = stratifiers.get(0).path("stratum");
        assertThat(sexStrata).hasSize(2);
        assertThat(sexStrata.get(0).path("value").path("text").asText()).isEqualTo("female");
        assertThat(sexStrata.get(0).path("measureScore").path("value").asDouble()).isEqualTo(33.33);
        assertThat(sexStrata.get(0).path("population").get(1).path("code").path("coding").get(0).path("code").asText())
                .isEqualTo("denominator");
        assertThat(sexStrata.get(0).path("population").get(1).path("code").path("coding").get(0).path("system").asText())
                .isEqualTo(com.cqlplatform.model.fhir.FhirCodeSystemConstants.CS_MEASURE_POPULATION);
        assertThat(sexStrata.get(0).path("population").get(1).path("count").asInt()).isEqualTo(3);
        assertThat(sexStrata.get(1).path("value").path("text").asText()).isEqualTo("male");

        JsonNode ageStrata = stratifiers.get(1).path("stratum");
        assertThat(ageStrata).hasSize(2);
        assertThat(ageStrata.get(0).path("value").path("text").asText()).isEqualTo("18-49");
        assertThat(ageStrata.get(0).has("measureScore")).as("a stratum without a score carries none").isFalse();
        assertThat(measureReport.has("extension")).as("no supplemental data → no extension").isFalse();
    }

    // PAT-234 — supplemental data / risk adjustment distributions travel as a platform extension
    // (a summary MeasureReport has no standard element for them) and as a CSV section.
    @Test
    void fhirAndCsvExports_carryTheSupplementalDataDistributions() throws Exception {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .groups(List.of(GroupResult.builder().groupId("group-1").populations(List.of()).build()))
                .supplementalDataResults(List.of(
                        MeasureEvaluationResult.SupplementalDataResult.builder()
                                .definition("SDE Sex").usage("supplemental-data").patientsWithoutValue(1)
                                .values(List.of(
                                        MeasureEvaluationResult.ValueCount.builder().value("female").count(4).build(),
                                        MeasureEvaluationResult.ValueCount.builder().value("male").count(3).build()))
                                .build(),
                        MeasureEvaluationResult.SupplementalDataResult.builder()
                                .definition("RAF Age Band").usage("risk-adjustment-factor").description("Age at period end")
                                .patientsWithoutValue(0)
                                .values(List.of(MeasureEvaluationResult.ValueCount.builder().value("65+").count(3).build()))
                                .build()))
                .build();
        MeasureReportEntity report = MeasureReportEntity.builder().id(43L).measureName("Demo")
                .periodStart(LocalDate.of(2022, 1, 1)).periodEnd(LocalDate.of(2022, 6, 30)).build();
        when(reportService.getReport(43L)).thenReturn(Optional.of(report));
        when(reportReader.reconstruct(43L)).thenReturn(Optional.of(result));
        when(canonical.getBase()).thenReturn("https://quality.example.tw/fhir");

        JsonNode measureReport = new ObjectMapper().readTree(exportService.exportReport(43L, "fhir").getBody());
        JsonNode extensions = measureReport.path("extension");
        assertThat(extensions).hasSize(2);
        assertThat(extensions.get(0).path("url").asText())
                .isEqualTo("https://quality.example.tw/fhir/StructureDefinition/measurereport-supplemental-data");
        JsonNode sex = extensions.get(0).path("extension");
        assertThat(sex.get(0).path("url").asText() + "=" + sex.get(0).path("valueString").asText()).isEqualTo("definition=SDE Sex");
        assertThat(sex.get(1).path("valueCode").asText()).isEqualTo("supplemental-data");
        assertThat(sex.get(2).path("url").asText()).isEqualTo("value");
        assertThat(sex.get(2).path("extension").get(0).path("valueString").asText()).isEqualTo("female");
        assertThat(sex.get(2).path("extension").get(1).path("valueInteger").asInt()).isEqualTo(4);
        assertThat(sex.get(4).path("url").asText() + "=" + sex.get(4).path("valueInteger").asInt()).isEqualTo("patientsWithoutValue=1");
        JsonNode raf = extensions.get(1).path("extension");
        assertThat(raf.get(1).path("valueCode").asText()).isEqualTo("risk-adjustment-factor");
        assertThat(raf.get(2).path("url").asText() + "=" + raf.get(2).path("valueString").asText()).isEqualTo("description=Age at period end");

        String csv = new String(exportService.exportReport(43L, "csv").getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Supplemental Data and Risk Adjustment Factors")
                .contains("SDE Sex,supplemental-data,female,4")
                .contains("SDE Sex,supplemental-data,(no value),1")
                .contains("RAF Age Band,risk-adjustment-factor,65+,3");
    }

    // PAT-235 — a multi-component stratum carries stratum.component[] next to the combined value.
    @Test
    void fhirExport_writesStratumComponents() throws Exception {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .groups(List.of(GroupResult.builder().groupId("group-1").populations(List.of())
                        .stratifiers(List.of(
                                StratifierResult.builder().strataId("sex-age").strataValue("female | 65+")
                                        .components(List.of(
                                                MeasureEvaluationResult.StratumComponent.builder().code("sex").value("female").build(),
                                                MeasureEvaluationResult.StratumComponent.builder().code("age").value("65+").build()))
                                        .populations(List.of(PopulationResult.builder().populationType("initial-population").count(1).build()))
                                        .build(),
                                stratum("gender", "true", 3, 2, 2, 100.0)))
                        .build()))
                .build();
        MeasureReportEntity report = MeasureReportEntity.builder().id(44L).measureName("Demo")
                .periodStart(LocalDate.of(2022, 1, 1)).periodEnd(LocalDate.of(2022, 6, 30)).build();
        when(reportService.getReport(44L)).thenReturn(Optional.of(report));
        when(reportReader.reconstruct(44L)).thenReturn(Optional.of(result));

        JsonNode measureReport = new ObjectMapper().readTree(exportService.exportReport(44L, "fhir").getBody());
        JsonNode stratifiers = measureReport.path("group").get(0).path("stratifier");
        JsonNode combined = stratifiers.get(0).path("stratum").get(0);
        assertThat(combined.path("value").path("text").asText()).isEqualTo("female | 65+");
        assertThat(combined.path("component")).hasSize(2);
        assertThat(combined.path("component").get(0).path("code").path("text").asText()).isEqualTo("sex");
        assertThat(combined.path("component").get(0).path("value").path("text").asText()).isEqualTo("female");
        assertThat(combined.path("component").get(1).path("value").path("text").asText()).isEqualTo("65+");
        assertThat(stratifiers.get(1).path("stratum").get(0).has("component")).isFalse();
    }
}
