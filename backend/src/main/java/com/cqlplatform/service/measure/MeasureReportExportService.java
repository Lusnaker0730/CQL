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

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureReportExportService {

    private final MeasureReportService reportService;
    private final QrdaExportService qrdaExportService;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

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

        MeasureEvaluationResult result = report.getEvaluationResult();
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
                    ArrayNode stratArray = groupNode.putArray("stratifier");
                    for (StratifierResult strat : group.getStratifiers()) {
                        ObjectNode stratNode = stratArray.addObject();
                        ArrayNode strataArray = stratNode.putArray("stratum");
                        ObjectNode stratumNode = strataArray.addObject();

                        ObjectNode valueNode = stratumNode.putObject("value");
                        valueNode.put("text", strat.getStrataValue());

                        if (strat.getPopulations() != null) {
                            ArrayNode stratPopArray = stratumNode.putArray("population");
                            for (PopulationResult pop : strat.getPopulations()) {
                                ObjectNode popNode = stratPopArray.addObject();
                                ObjectNode code = popNode.putObject("code");
                                ArrayNode coding = code.putArray("coding");
                                ObjectNode codeEntry = coding.addObject();
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

        MeasureEvaluationResult result = report.getEvaluationResult();
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

            MeasureEvaluationResult result = report.getEvaluationResult();

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
