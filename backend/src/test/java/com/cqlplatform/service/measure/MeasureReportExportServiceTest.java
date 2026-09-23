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
    }
}
