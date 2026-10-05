package com.cqlplatform.service.measure;

import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.StratifierDefinition;
import com.cqlplatform.repository.TestCaseRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * PAT-248 — the test case suite as a workbook: KEY sheet, one sheet per group, expected next to
 * actual per population / observation / stratum, mismatches highlighted, legacy map as 1 / 0.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseExcelExportServiceTest {

    @Mock private TestCaseRepository repository;
    @Mock private MeasureDefinitionService definitionService;
    @InjectMocks private TestCaseExcelExportService service;

    private static MeasureDefinition measure() {
        return MeasureDefinition.builder()
                .id(10L).name("HbA1cControl").version("1.2.0").status("active").scoringType("proportion").ownerUsername("user")
                .groupDefinitions(List.of(
                        GroupDefinition.builder().groupId("group-1")
                                .populations(List.of(
                                        PopulationDefinition.builder().populationType("initial-population").criteriaExpression("IP").build(),
                                        PopulationDefinition.builder().populationType("denominator").criteriaExpression("D").build(),
                                        PopulationDefinition.builder().populationType("numerator").criteriaExpression("N").build()))
                                .stratifiers(List.of(StratifierDefinition.builder().stratifierId("gender").criteriaExpression("Gender").build()))
                                .build(),
                        GroupDefinition.builder().groupId("seniors/65+")
                                .populations(List.of(
                                        PopulationDefinition.builder().populationType("initial-population").criteriaExpression("IP65").build()))
                                .build()))
                .build();
    }

    private static Map<String, String> rowByHeader(Sheet sheet, int rowIdx) {
        Row header = sheet.getRow(0);
        Row row = sheet.getRow(rowIdx);
        Map<String, String> out = new LinkedHashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            out.put(header.getCell(c).getStringCellValue(), cell == null ? "" : cell.getStringCellValue());
        }
        return out;
    }

    @Test
    void export_writesAKeySheetAndOneSheetPerGroup_withExpectedNextToActual_andHighlightsMismatches() throws Exception {
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure()));
        TestCaseEntity structured = TestCaseEntity.builder()
                .id(1L).measureDefinitionId(10L).title("Mei controlled").series("adults").description("HbA1c 6.8")
                .status("fail").lastRunAt(LocalDateTime.of(2026, 10, 5, 9, 30)).validationStatus("valid")
                .patientBundleJson("{\"resourceType\":\"Bundle\",\"entry\":[]}")
                .expectedValues("{\"groups\":[{\"groupId\":\"group-1\",\"populations\":{\"initial-population\":1,\"denominator\":1,\"numerator\":1},"
                        + "\"observations\":[6.8],\"stratifiers\":{\"gender\":\"female\"}},"
                        + "{\"groupId\":\"seniors/65+\",\"populations\":{\"initial-population\":0}}]}")
                .lastRunResultJson("{\"testCaseId\":1,\"status\":\"fail\",\"actualValues\":{\"groups\":[{\"groupId\":\"group-1\","
                        + "\"populations\":{\"initial-population\":1,\"denominator\":1,\"numerator\":0},\"observations\":[6.8],\"stratifiers\":{\"gender\":\"female\"}},"
                        + "{\"groupId\":\"seniors/65+\",\"populations\":{\"initial-population\":0}}]},\"measurementPeriodStart\":\"2025-01-01\",\"measurementPeriodEnd\":\"2025-12-31\"}")
                .build();
        TestCaseEntity legacy = TestCaseEntity.builder()
                .id(2L).measureDefinitionId(10L).title("Wei excluded").status("pending")
                .patientBundleJson("{\"resourceType\":\"Bundle\",\"entry\":[]}")
                .build();
        legacy.getExpectedPopulationMap().put("initial-population", true);
        legacy.getExpectedPopulationMap().put("denominator", false);
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(structured, legacy));

        byte[] bytes = service.export(10L);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("KEY");
            assertThat(workbook.getSheetName(1)).isEqualTo("group-1");
            assertThat(workbook.getSheetName(2)).as("sheet names cannot contain '/'").isEqualTo("seniors_65+");
            assertThat(workbook.getSheet("KEY").getRow(0).getCell(0).getStringCellValue()).isEqualTo("Test cases of HbA1cControl v1.2.0");

            Sheet group1 = workbook.getSheet("group-1");
            Map<String, String> mei = rowByHeader(group1, 1);
            assertThat(mei).containsEntry("Test Case", "Mei controlled").containsEntry("Series", "adults").containsEntry("Status", "fail")
                    .containsEntry("Last Run", "2026-10-05 09:30").containsEntry("Validation", "valid")
                    .containsEntry("Expected Initial Population", "1").containsEntry("Actual Initial Population", "1")
                    .containsEntry("Expected Numerator", "1").containsEntry("Actual Numerator", "0")
                    .containsEntry("Expected Observations", "6.8").containsEntry("Actual Observations", "6.8")
                    .containsEntry("Expected Stratifier gender", "female").containsEntry("Actual Stratifier gender", "female")
                    .containsEntry("Description", "HbA1c 6.8");
            // the numerator mismatch is highlighted, the matching IP is not
            int numeratorActual = new ArrayList<>(mei.keySet()).indexOf("Actual Numerator");
            int ipActual = new ArrayList<>(mei.keySet()).indexOf("Actual Initial Population");
            assertThat(group1.getRow(1).getCell(numeratorActual).getCellStyle().getFillForegroundColor()).isEqualTo(IndexedColors.ROSE.getIndex());
            assertThat(group1.getRow(1).getCell(ipActual).getCellStyle().getFillForegroundColor()).isEqualTo(IndexedColors.LIGHT_GREEN.getIndex());

            Map<String, String> wei = rowByHeader(group1, 2);
            assertThat(wei).containsEntry("Test Case", "Wei excluded").containsEntry("Validation", "(none)")
                    .containsEntry("Expected Initial Population", "1").containsEntry("Expected Denominator", "0")
                    .as("no structured expectation for the numerator").containsEntry("Expected Numerator", "")
                    .as("never run → no actuals").containsEntry("Actual Initial Population", "").containsEntry("Actual Numerator", "");

            Sheet seniors = workbook.getSheet("seniors_65+");
            assertThat(rowByHeader(seniors, 1)).containsEntry("Expected Initial Population", "0").containsEntry("Actual Initial Population", "0");
            assertThat(rowByHeader(seniors, 2)).as("the legacy map only speaks for the first group").containsEntry("Expected Initial Population", "");
        }
    }

    @Test
    void export_measureWithoutGroups_writesTheLegacySheet_andNoTestCasesIsRefused() throws Exception {
        MeasureDefinition flat = MeasureDefinition.builder().id(11L).name("Flat").version("1.0").status("draft").ownerUsername("user").build();
        when(definitionService.getById(11L)).thenReturn(Optional.of(flat));
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(11L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.export(11L)).isInstanceOf(ValidationException.class);

        TestCaseEntity tc = TestCaseEntity.builder().id(1L).measureDefinitionId(11L).title("only").status("pass")
                .lastRunAt(LocalDateTime.of(2026, 1, 1, 0, 0)).patientBundleJson("{}").build();
        tc.getExpectedPopulationMap().put("initial-population", true);
        tc.getLastRunActualPopulationMap().put("initial-population", true);
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(11L)).thenReturn(List.of(tc));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(11L)))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet sheet = workbook.getSheet("Test Cases");
            assertThat(rowByHeader(sheet, 1)).containsEntry("Expected Initial Population", "1").containsEntry("Actual Initial Population", "1");
        }
    }

    @Test
    void uniqueSheetName_capsAt31Characters_andDisambiguates() {
        Set<String> taken = new HashSet<>();
        assertThat(TestCaseExcelExportService.uniqueSheetName(taken, "a:b*c?d/e\\f[g]")).isEqualTo("a_b_c_d_e_f_g_");
        String longId = "x".repeat(40);
        assertThat(TestCaseExcelExportService.uniqueSheetName(taken, longId)).hasSize(31);
        assertThat(TestCaseExcelExportService.uniqueSheetName(taken, longId)).hasSize(31).endsWith(" (2)");
    }
}
