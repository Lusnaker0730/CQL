package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.*;
import com.cqlplatform.util.CsvUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureReportExportService {

    private final MeasureReportService reportService;
    private final QrdaExportService qrdaExportService;
    /** Phase 2 of ADR-001: prefer normalized tables over result_json for read. */
    private final NormalizedMeasureReportReader reportReader;
    private final FhirCanonicalResolver canonical;

    /** PAT-234: where the supplemental data / risk adjustment distributions live on the FHIR MeasureReport. */
    static final String SUPPLEMENTAL_DATA_EXTENSION = "StructureDefinition/measurereport-supplemental-data";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Source of truth for a report's evaluation result. Prefers the normalized
     * {@code measure_report_*} tables (Phase 2); falls back to {@code result_json}
     * parsing for reports that haven't been backfilled yet. Phase 3 deletes the fallback.
     */
    private MeasureEvaluationResult loadResult(MeasureReportEntity report) {
        return reportReader.reconstruct(report.getId())
                .orElseGet(report::getEvaluationResult);
    }

    /**
     * PAT-234 — the supplemental data / risk adjustment distributions as a complex extension
     * on the MeasureReport: one extension per element with {@code definition}, {@code usage},
     * {@code description?}, one {@code value} extension per distinct value ({@code value},
     * {@code count}) and {@code patientsWithoutValue}. This is a platform extension (url under
     * the installation's canonical base): FHIR R4 summary MeasureReports have no standard
     * element for SDE distributions — the standard carries SDE per patient, on individual reports.
     */
    private void addSupplementalDataExtension(ObjectNode fhirReport, MeasureEvaluationResult result) {
        if (result == null || result.getSupplementalDataResults() == null || result.getSupplementalDataResults().isEmpty()) return;
        String url = canonical.getBase() + "/" + SUPPLEMENTAL_DATA_EXTENSION;
        ArrayNode extensions = fhirReport.withArray("extension");
        for (SupplementalDataResult element : result.getSupplementalDataResults()) {
            ObjectNode ext = extensions.addObject();
            ext.put("url", url);
            ArrayNode parts = ext.putArray("extension");
            parts.addObject().put("url", "definition").put("valueString", element.getDefinition());
            parts.addObject().put("url", "usage").put("valueCode", element.getUsage());
            if (element.getDescription() != null && !element.getDescription().isBlank()) {
                parts.addObject().put("url", "description").put("valueString", element.getDescription());
            }
            if (element.getValues() != null) {
                for (ValueCount vc : element.getValues()) {
                    ObjectNode valueExt = parts.addObject();
                    valueExt.put("url", "value");
                    ArrayNode valueParts = valueExt.putArray("extension");
                    valueParts.addObject().put("url", "value").put("valueString", vc.getValue());
                    valueParts.addObject().put("url", "count").put("valueInteger", vc.getCount() != null ? vc.getCount() : 0);
                }
            }
            parts.addObject().put("url", "patientsWithoutValue")
                    .put("valueInteger", element.getPatientsWithoutValue() != null ? element.getPatientsWithoutValue() : 0);
        }
    }

    public ResponseEntity<byte[]> exportReport(Long reportId, String format) {
        MeasureReportEntity report = reportService.getReport(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        return switch (format.toLowerCase()) {
            case "csv" -> exportAsCsv(report);
            case "excel" -> exportAsExcel(report);
            case "qrda3" -> exportAsQrdaIII(report);
            default -> exportAsFhir(report);
        };
    }

    private ResponseEntity<byte[]> exportAsFhir(MeasureReportEntity report) {
        ObjectNode fhirReport = MAPPER.createObjectNode();
        fhirReport.put("resourceType", "MeasureReport");
        fhirReport.put("id", report.getId().toString());
        fhirReport.put("status", report.getStatus() != null ? report.getStatus() : com.cqlplatform.model.measure.EvaluationStatusConstants.COMPLETE);
        fhirReport.put("type", report.getReportType() != null ? report.getReportType() : "summary");
        fhirReport.put("measure", report.getMeasureName());

        ObjectNode period = fhirReport.putObject("period");
        period.put("start", report.getPeriodStart().toString());
        period.put("end", report.getPeriodEnd().toString());

        MeasureEvaluationResult result = loadResult(report);
        if (result != null && result.getGroups() != null) {
            ArrayNode groupArray = fhirReport.putArray("group");
            for (GroupResult group : result.getGroups()) {
                ObjectNode groupNode = groupArray.addObject();

                if (group.getPopulations() != null) {
                    ArrayNode popArray = groupNode.putArray("population");
                    for (PopulationResult pop : group.getPopulations()) {
                        ObjectNode popNode = popArray.addObject();
                        ObjectNode code = popNode.putObject("code");
                        ArrayNode coding = code.putArray("coding");
                        ObjectNode codeEntry = coding.addObject();
                        codeEntry.put("system", com.cqlplatform.model.fhir.FhirCodeSystemConstants.CS_MEASURE_POPULATION);
                        codeEntry.put("code", pop.getPopulationType());
                        popNode.put("count", pop.getCount() != null ? pop.getCount() : 0);
                    }
                }

                if (group.getMeasureScore() != null) {
                    ObjectNode scoreNode = groupNode.putObject("measureScore");
                    scoreNode.put("value", group.getMeasureScore());
                    scoreNode.put("unit", "%");
                }

                if (group.getStratifiers() != null) {
                    // The platform keeps one row per (stratifier, stratum); FHIR wants one
                    // stratifier holding all its strata, identified by its code (PAT-233 — it
                    // used to emit one code-less stratifier per stratum).
                    ArrayNode stratArray = groupNode.putArray("stratifier");
                    Map<String, ArrayNode> strataByStratifier = new java.util.LinkedHashMap<>();
                    for (StratifierResult strat : group.getStratifiers()) {
                        String stratifierId = strat.getStrataId() != null ? strat.getStrataId() : "stratifier";
                        ArrayNode strataArray = strataByStratifier.computeIfAbsent(stratifierId, id -> {
                            ObjectNode stratNode = stratArray.addObject();
                            stratNode.putObject("code").put("text", id);
                            return stratNode.putArray("stratum");
                        });
                        ObjectNode stratumNode = strataArray.addObject();

                        ObjectNode valueNode = stratumNode.putObject("value");
                        valueNode.put("text", strat.getStrataValue());
                        // PAT-235: a multi-component stratum also carries each component's value
                        // (MeasureReport.stratum.component); value.text keeps the combination.
                        if (strat.getComponents() != null && !strat.getComponents().isEmpty()) {
                            ArrayNode componentArray = stratumNode.putArray("component");
                            for (StratumComponent component : strat.getComponents()) {
                                ObjectNode componentNode = componentArray.addObject();
                                componentNode.putObject("code").put("text", component.getCode());
                                componentNode.putObject("value").put("text", component.getValue());
                            }
                        }

                        if (strat.getPopulations() != null) {
                            ArrayNode stratPopArray = stratumNode.putArray("population");
                            for (PopulationResult pop : strat.getPopulations()) {
                                ObjectNode popNode = stratPopArray.addObject();
                                ObjectNode code = popNode.putObject("code");
                                ArrayNode coding = code.putArray("coding");
                                ObjectNode codeEntry = coding.addObject();
                                codeEntry.put("system", com.cqlplatform.model.fhir.FhirCodeSystemConstants.CS_MEASURE_POPULATION);
                                codeEntry.put("code", pop.getPopulationType());
                                popNode.put("count", pop.getCount() != null ? pop.getCount() : 0);
                            }
                        }

                        if (strat.getMeasureScore() != null) {
                            ObjectNode stratScoreNode = stratumNode.putObject("measureScore");
                            stratScoreNode.put("value", strat.getMeasureScore());
                        }
                    }
                }
            }
        }
        addSupplementalDataExtension(fhirReport, result);

        try {
            byte[] json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(fhirReport);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=measure-report-" + report.getId() + ".json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (Exception e) {
            throw new com.cqlplatform.exception.CqlExecutionException("Failed to serialize FHIR MeasureReport: " + e.getMessage());
        }
    }

    private ResponseEntity<byte[]> exportAsQrdaIII(MeasureReportEntity report) {
        String qrda = qrdaExportService.exportQrdaIII(report);
        byte[] xmlBytes = qrda.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=qrda3-report-" + report.getId() + ".xml")
                .contentType(MediaType.APPLICATION_XML)
                .body(xmlBytes);
    }

    private ResponseEntity<byte[]> exportAsCsv(MeasureReportEntity report) {
        StringBuilder csv = new StringBuilder();
        csv.append("Measure Report: ").append(CsvUtils.escapeCsv(report.getMeasureName())).append("\n");
        csv.append("Period: ").append(report.getPeriodStart()).append(" to ").append(report.getPeriodEnd()).append("\n");
        csv.append("Status: ").append(CsvUtils.escapeCsv(report.getStatus())).append("\n\n");

        MeasureEvaluationResult result = loadResult(report);
        if (result != null && result.getGroups() != null) {
            for (GroupResult group : result.getGroups()) {
                csv.append("Group: ").append(CsvUtils.escapeCsv(group.getGroupId())).append("\n");
                if (group.getDescription() != null) {
                    csv.append("Description: ").append(CsvUtils.escapeCsv(group.getDescription())).append("\n");
                }
                csv.append("\nPopulation Type,Count\n");

                if (group.getPopulations() != null) {
                    for (PopulationResult pop : group.getPopulations()) {
                        csv.append(CsvUtils.escapeCsv(pop.getPopulationType())).append(",")
                                .append(pop.getCount() != null ? pop.getCount() : 0).append("\n");
                    }
                }

                if (group.getMeasureScore() != null) {
                    csv.append("\nMeasure Score: ").append(String.format("%.2f%%", group.getMeasureScore())).append("\n");
                }

                if (group.getStratifiers() != null && !group.getStratifiers().isEmpty()) {
                    csv.append("\nStratification Results\n");
                    csv.append("Stratum ID,Stratum Value,Population Type,Count,Score\n");
                    for (StratifierResult strat : group.getStratifiers()) {
                        if (strat.getPopulations() != null) {
                            for (PopulationResult pop : strat.getPopulations()) {
                                csv.append(CsvUtils.escapeCsv(strat.getStrataId())).append(",")
                                        .append(CsvUtils.escapeCsv(strat.getStrataValue())).append(",")
                                        .append(CsvUtils.escapeCsv(pop.getPopulationType())).append(",")
                                        .append(pop.getCount() != null ? pop.getCount() : 0).append(",")
                                        .append(strat.getMeasureScore() != null ? String.format("%.2f%%", strat.getMeasureScore()) : "")
                                        .append("\n");
                            }
                        }
                    }
                }
                csv.append("\n");
            }
        }
        if (result != null && result.getSupplementalDataResults() != null && !result.getSupplementalDataResults().isEmpty()) {
            csv.append("Supplemental Data and Risk Adjustment Factors\n");
            csv.append("Definition,Usage,Value,Patients\n");
            for (SupplementalDataResult element : result.getSupplementalDataResults()) {
                if (element.getValues() != null) {
                    for (ValueCount vc : element.getValues()) {
                        csv.append(CsvUtils.escapeCsv(element.getDefinition())).append(",")
                                .append(CsvUtils.escapeCsv(element.getUsage())).append(",")
                                .append(CsvUtils.escapeCsv(vc.getValue())).append(",")
                                .append(vc.getCount() != null ? vc.getCount() : 0).append("\n");
                    }
                }
                csv.append(CsvUtils.escapeCsv(element.getDefinition())).append(",")
                        .append(CsvUtils.escapeCsv(element.getUsage())).append(",(no value),")
                        .append(element.getPatientsWithoutValue() != null ? element.getPatientsWithoutValue() : 0).append("\n");
            }
            csv.append("\n");
        }

        byte[] csvBytes = csv.toString().getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=measure-report-" + report.getId() + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }

    private ResponseEntity<byte[]> exportAsExcel(MeasureReportEntity report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Sheet 1: Summary
            Sheet summarySheet = workbook.createSheet("Summary");
            int row = 0;
            Row headerRow = summarySheet.createRow(row++);
            createCell(headerRow, 0, "Property", headerStyle);
            createCell(headerRow, 1, "Value", headerStyle);

            createRow(summarySheet, row++, "Measure Name", report.getMeasureName());
            createRow(summarySheet, row++, "Period Start", report.getPeriodStart().toString());
            createRow(summarySheet, row++, "Period End", report.getPeriodEnd().toString());
            createRow(summarySheet, row++, "Status", report.getStatus() != null ? report.getStatus() : "");
            createRow(summarySheet, row++, "Report Type", report.getReportType() != null ? report.getReportType() : "");
            if (report.getMeasureScore() != null) {
                createRow(summarySheet, row++, "Measure Score", String.format("%.2f%%", report.getMeasureScore()));
            }
            if (report.getTotalPatients() != null) {
                createRow(summarySheet, row++, "Total Patients", report.getTotalPatients().toString());
            }
            if (report.getEvaluationDurationMs() != null) {
                createRow(summarySheet, row++, "Evaluation Duration", report.getEvaluationDurationMs() + " ms");
            }
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            MeasureEvaluationResult result = loadResult(report);

            // Sheet 2: Populations
            Sheet popSheet = workbook.createSheet("Populations");
            row = 0;
            headerRow = popSheet.createRow(row++);
            createCell(headerRow, 0, "Group", headerStyle);
            createCell(headerRow, 1, "Population Type", headerStyle);
            createCell(headerRow, 2, "Count", headerStyle);
            createCell(headerRow, 3, "Measure Score", headerStyle);

            if (result != null && result.getGroups() != null) {
                for (GroupResult group : result.getGroups()) {
                    if (group.getPopulations() != null) {
                        for (PopulationResult pop : group.getPopulations()) {
                            Row dataRow = popSheet.createRow(row++);
                            dataRow.createCell(0).setCellValue(group.getGroupId());
                            dataRow.createCell(1).setCellValue(pop.getPopulationType());
                            dataRow.createCell(2).setCellValue(pop.getCount() != null ? pop.getCount() : 0);
                            dataRow.createCell(3).setCellValue(
                                    group.getMeasureScore() != null ? String.format("%.2f%%", group.getMeasureScore()) : "");
                        }
                    }
                }
            }
            for (int i = 0; i < 4; i++) popSheet.autoSizeColumn(i);

            // Sheet 3: Stratifiers
            Sheet stratSheet = workbook.createSheet("Stratifiers");
            row = 0;
            headerRow = stratSheet.createRow(row++);
            createCell(headerRow, 0, "Stratum ID", headerStyle);
            createCell(headerRow, 1, "Stratum Value", headerStyle);
            createCell(headerRow, 2, "Population Type", headerStyle);
            createCell(headerRow, 3, "Count", headerStyle);
            createCell(headerRow, 4, "Score", headerStyle);

            if (result != null && result.getGroups() != null) {
                for (GroupResult group : result.getGroups()) {
                    if (group.getStratifiers() != null) {
                        for (StratifierResult strat : group.getStratifiers()) {
                            if (strat.getPopulations() != null) {
                                for (PopulationResult pop : strat.getPopulations()) {
                                    Row dataRow = stratSheet.createRow(row++);
                                    dataRow.createCell(0).setCellValue(strat.getStrataId());
                                    dataRow.createCell(1).setCellValue(strat.getStrataValue());
                                    dataRow.createCell(2).setCellValue(pop.getPopulationType());
                                    dataRow.createCell(3).setCellValue(pop.getCount() != null ? pop.getCount() : 0);
                                    dataRow.createCell(4).setCellValue(
                                            strat.getMeasureScore() != null ? String.format("%.2f%%", strat.getMeasureScore()) : "");
                                }
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < 5; i++) stratSheet.autoSizeColumn(i);

            // Sheet 4: Supplemental data / risk adjustment factors (PAT-234)
            Sheet sdeSheet = workbook.createSheet("Supplemental Data");
            row = 0;
            headerRow = sdeSheet.createRow(row++);
            createCell(headerRow, 0, "Definition", headerStyle);
            createCell(headerRow, 1, "Usage", headerStyle);
            createCell(headerRow, 2, "Value", headerStyle);
            createCell(headerRow, 3, "Patients", headerStyle);
            if (result != null && result.getSupplementalDataResults() != null) {
                for (SupplementalDataResult element : result.getSupplementalDataResults()) {
                    if (element.getValues() != null) {
                        for (ValueCount vc : element.getValues()) {
                            Row dataRow = sdeSheet.createRow(row++);
                            dataRow.createCell(0).setCellValue(element.getDefinition());
                            dataRow.createCell(1).setCellValue(element.getUsage());
                            dataRow.createCell(2).setCellValue(vc.getValue());
                            dataRow.createCell(3).setCellValue(vc.getCount() != null ? vc.getCount() : 0);
                        }
                    }
                    Row dataRow = sdeSheet.createRow(row++);
                    dataRow.createCell(0).setCellValue(element.getDefinition());
                    dataRow.createCell(1).setCellValue(element.getUsage());
                    dataRow.createCell(2).setCellValue("(no value)");
                    dataRow.createCell(3).setCellValue(element.getPatientsWithoutValue() != null ? element.getPatientsWithoutValue() : 0);
                }
            }
            for (int i = 0; i < 4; i++) sdeSheet.autoSizeColumn(i);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            byte[] excelBytes = baos.toByteArray();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=measure-report-" + report.getId() + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);
        } catch (Exception e) {
            throw new com.cqlplatform.exception.CqlExecutionException("Failed to generate Excel export: " + e.getMessage());
        }
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }
}
