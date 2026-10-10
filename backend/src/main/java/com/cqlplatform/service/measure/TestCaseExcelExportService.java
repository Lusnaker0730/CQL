package com.cqlplatform.service.measure;

import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.StratifierDefinition;
import com.cqlplatform.model.measure.TestCaseExpectedValues;
import com.cqlplatform.model.measure.TestCaseRunResult;
import com.cqlplatform.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PAT-248 — the test case suite as a spreadsheet, the way MADiE's "Export Test Cases (Excel)"
 * lays it out: a KEY sheet explaining the columns, then one sheet per population group with a
 * row per test case and, per population, the expected value next to the actual value of the
 * last run (observations and strata likewise). Cells where expected and actual disagree are
 * highlighted, so a reviewer can scan a 40-case suite without opening each case.
 *
 * <p>Expected values come from the structured expectation (PAT-228) or, for test cases that
 * only have the legacy boolean map, from that map as 1 / 0 on the first group. Actual values
 * come from the stored last run ({@code lastRunResultJson}); a test case never run on the
 * current bundle has empty actual cells.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestCaseExcelExportService {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TestCaseRepository repository;
    private final MeasureDefinitionService definitionService;

    @Transactional(readOnly = true)
    public byte[] export(Long measureId) {
        MeasureDefinition measure = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
        List<TestCaseEntity> entities = repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(measureId);
        if (entities.isEmpty()) {
            throw new ValidationException("The measure has no test cases to export");
        }
        List<GroupDefinition> groups = measure.getGroupDefinitions() != null ? measure.getGroupDefinitions() : List.of();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            writeKeySheet(workbook, styles, measure, entities.size(), groups);
            if (groups.isEmpty()) {
                writeLegacySheet(workbook, styles, entities);
            } else {
                Set<String> sheetNames = new HashSet<>();
                for (int i = 0; i < groups.size(); i++) {
                    GroupDefinition group = groups.get(i);
                    String groupId = TestCaseService.effectiveGroupId(group, i);
                    writeGroupSheet(workbook, styles, uniqueSheetName(sheetNames, groupId), groupId, i == 0, group, entities);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the test case workbook: " + e.getMessage(), e);
        }
    }

    // ───────────────────────────────────────────────────────────── sheets

    private void writeKeySheet(Workbook workbook, Styles styles, MeasureDefinition measure, int count, List<GroupDefinition> groups) {
        Sheet sheet = workbook.createSheet("KEY");
        int r = 0;
        Row title = sheet.createRow(r++);
        cell(title, 0, "Test cases of " + measure.getName() + " v" + measure.getVersion(), styles.header);
        r++;
        kv(sheet, r++, "Measure", measure.getName() + " (" + measure.getVersion() + ", " + measure.getStatus() + ")");
        kv(sheet, r++, "Scoring", measure.getScoringType() != null ? measure.getScoringType() : "");
        LocalDate[] period = TestCaseService.measurementPeriod(measure);
        kv(sheet, r++, "Measurement Period", period[0] + " – " + period[1]);
        kv(sheet, r++, "Test cases", String.valueOf(count));
        kv(sheet, r++, "Exported", java.time.LocalDateTime.now().format(STAMP));
        r++;
        Row how = sheet.createRow(r++);
        cell(how, 0, "How to read the group sheets", styles.header);
        kv(sheet, r++, "One sheet per population group", groups.isEmpty() ? "(this measure has no group definitions — see 'Test Cases')"
                : groups.stream().map(g -> TestCaseService.effectiveGroupId(g, groups.indexOf(g))).collect(Collectors.joining(", ")));
        kv(sheet, r++, "One row per test case", "Title, series, run status, last run, FHIR validation, then expected / actual pairs");
        kv(sheet, r++, "Expected <population>", "The count the test case expects for that population (episode count for episode-based groups, 1 / 0 for patient-based)");
        kv(sheet, r++, "Actual <population>", "What the last run produced; empty when the test case has not run on its current bundle");
        kv(sheet, r++, "Observations", "Expected / actual measure observation values, comma separated, in population order");
        kv(sheet, r++, "Stratifier <id>", "Expected / actual stratum the patient falls into");
        kv(sheet, r++, "Highlighted cell", "Actual differs from expected (the mismatch that made the run fail)");
        kv(sheet, r++, "Status", "pass / fail / error / pending (pending = not run since the last change)");
        kv(sheet, r++, "Validation", "FHIR validation of the patient bundle: valid / invalid / error / pending / (none)");
        sheet.setColumnWidth(0, 34 * 256);
        sheet.setColumnWidth(1, 90 * 256);
    }

    private void writeGroupSheet(Workbook workbook, Styles styles, String sheetName, String groupId, boolean firstGroup,
                                 GroupDefinition group, List<TestCaseEntity> entities) {
        Sheet sheet = workbook.createSheet(sheetName);
        List<String> populations = group.getPopulations() == null ? List.of() : group.getPopulations().stream()
                .map(PopulationDefinition::getPopulationType).filter(Objects::nonNull).distinct().toList();
        List<String> stratifiers = stratifierIds(group, groupId, entities);
        boolean hasObservations = group.getObservations() != null && !group.getObservations().isEmpty()
                || entities.stream().anyMatch(e -> {
                    TestCaseExpectedValues.GroupValues v = groupValues(readExpected(e), groupId);
                    return v != null && v.getObservations() != null && !v.getObservations().isEmpty();
                });

        int r = 0;
        Row header = sheet.createRow(r++);
        int c = 0;
        cell(header, c++, "Test Case", styles.header);
        cell(header, c++, "Series", styles.header);
        cell(header, c++, "Status", styles.header);
        cell(header, c++, "Last Run", styles.header);
        cell(header, c++, "Validation", styles.header);
        for (String population : populations) {
            cell(header, c++, "Expected " + TestCaseBundleService.displayName(population), styles.expectedHeader);
            cell(header, c++, "Actual " + TestCaseBundleService.displayName(population), styles.actualHeader);
        }
        if (hasObservations) {
            cell(header, c++, "Expected Observations", styles.expectedHeader);
            cell(header, c++, "Actual Observations", styles.actualHeader);
        }
        for (String stratifier : stratifiers) {
            cell(header, c++, "Expected Stratifier " + stratifier, styles.expectedHeader);
            cell(header, c++, "Actual Stratifier " + stratifier, styles.actualHeader);
        }
        cell(header, c++, "Description", styles.header);
        int columns = c;

        for (TestCaseEntity entity : entities) {
            Row row = sheet.createRow(r++);
            c = 0;
            row.createCell(c++).setCellValue(nullToEmpty(entity.getTitle()));
            row.createCell(c++).setCellValue(nullToEmpty(entity.getSeries()));
            row.createCell(c++).setCellValue(nullToEmpty(entity.getStatus()));
            row.createCell(c++).setCellValue(entity.getLastRunAt() != null ? entity.getLastRunAt().format(STAMP) : "");
            row.createCell(c++).setCellValue(entity.getValidationStatus() != null ? entity.getValidationStatus() : "(none)");

            TestCaseExpectedValues.GroupValues expected = groupValues(readExpected(entity), groupId);
            TestCaseRunResult run = readRun(entity);
            TestCaseExpectedValues.GroupValues actual = run != null ? groupValues(run.getActualValues(), groupId) : null;
            boolean ran = run != null && entity.getLastRunAt() != null;

            for (String population : populations) {
                String exp = expectedCount(expected, population, firstGroup, entity);
                String act = actual != null && actual.getPopulations() != null && actual.getPopulations().containsKey(population)
                        ? String.valueOf(actual.getPopulations().get(population))
                        : ran && firstGroup && run.getActualPopulations() != null && run.getActualPopulations().containsKey(population)
                        ? (Boolean.TRUE.equals(run.getActualPopulations().get(population)) ? "1" : "0")
                        : "";
                pair(row, c, exp, act, styles);
                c += 2;
            }
            if (hasObservations) {
                String exp = expected != null && expected.getObservations() != null ? joinNumbers(expected.getObservations()) : "";
                String act = actual != null && actual.getObservations() != null ? joinNumbers(actual.getObservations()) : "";
                pair(row, c, exp, act, styles);
                c += 2;
            }
            for (String stratifier : stratifiers) {
                String exp = expected != null && expected.getStratifiers() != null ? nullToEmpty(expected.getStratifiers().get(stratifier)) : "";
                String act = actual != null && actual.getStratifiers() != null ? nullToEmpty(actual.getStratifiers().get(stratifier)) : "";
                pair(row, c, exp, act, styles);
                c += 2;
            }
            row.createCell(c).setCellValue(nullToEmpty(entity.getDescription()));
        }
        sheet.createFreezePane(1, 1);
        for (int i = 0; i < columns; i++) sheet.autoSizeColumn(i);
        sheet.setColumnWidth(columns - 1, Math.min(sheet.getColumnWidth(columns - 1), 60 * 256));
    }

    /** Measures without group definitions: the legacy boolean map per population, expected next to the last run. */
    private void writeLegacySheet(Workbook workbook, Styles styles, List<TestCaseEntity> entities) {
        Sheet sheet = workbook.createSheet("Test Cases");
        List<String> populations = entities.stream()
                .flatMap(e -> e.getExpectedPopulationMap() != null ? e.getExpectedPopulationMap().keySet().stream() : java.util.stream.Stream.<String>empty())
                .distinct().toList();
        int r = 0;
        Row header = sheet.createRow(r++);
        int c = 0;
        cell(header, c++, "Test Case", styles.header);
        cell(header, c++, "Series", styles.header);
        cell(header, c++, "Status", styles.header);
        cell(header, c++, "Last Run", styles.header);
        cell(header, c++, "Validation", styles.header);
        for (String population : populations) {
            cell(header, c++, "Expected " + TestCaseBundleService.displayName(population), styles.expectedHeader);
            cell(header, c++, "Actual " + TestCaseBundleService.displayName(population), styles.actualHeader);
        }
        cell(header, c++, "Description", styles.header);
        int columns = c;
        for (TestCaseEntity entity : entities) {
            Row row = sheet.createRow(r++);
            c = 0;
            row.createCell(c++).setCellValue(nullToEmpty(entity.getTitle()));
            row.createCell(c++).setCellValue(nullToEmpty(entity.getSeries()));
            row.createCell(c++).setCellValue(nullToEmpty(entity.getStatus()));
            row.createCell(c++).setCellValue(entity.getLastRunAt() != null ? entity.getLastRunAt().format(STAMP) : "");
            row.createCell(c++).setCellValue(entity.getValidationStatus() != null ? entity.getValidationStatus() : "(none)");
            Map<String, Boolean> expected = entity.getExpectedPopulationMap() != null ? entity.getExpectedPopulationMap() : Map.of();
            Map<String, Boolean> actual = entity.getLastRunAt() != null && entity.getLastRunActualPopulationMap() != null
                    ? entity.getLastRunActualPopulationMap() : Map.of();
            for (String population : populations) {
                String exp = expected.containsKey(population) ? (Boolean.TRUE.equals(expected.get(population)) ? "1" : "0") : "";
                String act = actual.containsKey(population) ? (Boolean.TRUE.equals(actual.get(population)) ? "1" : "0") : "";
                pair(row, c, exp, act, styles);
                c += 2;
            }
            row.createCell(c).setCellValue(nullToEmpty(entity.getDescription()));
        }
        sheet.createFreezePane(1, 1);
        for (int i = 0; i < columns; i++) sheet.autoSizeColumn(i);
    }

    // ───────────────────────────────────────────────────────────── values

    /** The expected count of a population: structured expectation first, else the legacy map on the first group. */
    private static String expectedCount(TestCaseExpectedValues.GroupValues expected, String population, boolean firstGroup, TestCaseEntity entity) {
        if (expected != null && expected.getPopulations() != null && expected.getPopulations().containsKey(population)) {
            return String.valueOf(expected.getPopulations().get(population));
        }
        if (expected == null && firstGroup && entity.getExpectedPopulationMap() != null && entity.getExpectedPopulationMap().containsKey(population)) {
            return Boolean.TRUE.equals(entity.getExpectedPopulationMap().get(population)) ? "1" : "0";
        }
        return "";
    }

    /** Stratifier ids of the group — from its definition, plus any the expectations or runs mention. */
    private static List<String> stratifierIds(GroupDefinition group, String groupId, List<TestCaseEntity> entities) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (group.getStratifiers() != null) {
            for (StratifierDefinition s : group.getStratifiers()) if (s.getStratifierId() != null) ids.add(s.getStratifierId());
        }
        for (TestCaseEntity entity : entities) {
            TestCaseExpectedValues.GroupValues expected = groupValues(readExpected(entity), groupId);
            if (expected != null && expected.getStratifiers() != null) ids.addAll(expected.getStratifiers().keySet());
        }
        return new ArrayList<>(ids);
    }

    private static TestCaseExpectedValues.GroupValues groupValues(TestCaseExpectedValues values, String groupId) {
        if (values == null || values.getGroups() == null) return null;
        for (TestCaseExpectedValues.GroupValues g : values.getGroups()) {
            if (groupId.equals(g.getGroupId())) return g;
        }
        return null;
    }

    private static TestCaseExpectedValues readExpected(TestCaseEntity entity) {
        if (entity.getExpectedValues() == null || entity.getExpectedValues().isBlank()) return null;
        try {
            return MAPPER.readValue(entity.getExpectedValues(), TestCaseExpectedValues.class);
        } catch (IOException e) {
            return null;
        }
    }

    private static TestCaseRunResult readRun(TestCaseEntity entity) {
        if (entity.getLastRunResultJson() == null || entity.getLastRunResultJson().isBlank()) return null;
        try {
            return MAPPER.readValue(entity.getLastRunResultJson(), TestCaseRunResult.class);
        } catch (IOException e) {
            log.debug("Unreadable last run on test case {}: {}", entity.getId(), e.getMessage());
            return null;
        }
    }

    private static String joinNumbers(List<Double> values) {
        return values.stream().filter(Objects::nonNull)
                .map(v -> v == Math.rint(v) ? String.valueOf(v.longValue()) : String.valueOf(v))
                .collect(Collectors.joining(", "));
    }

    // ───────────────────────────────────────────────────────────── cells

    private static void pair(Row row, int col, String expected, String actual, Styles styles) {
        row.createCell(col).setCellValue(expected);
        Cell actualCell = row.createCell(col + 1);
        actualCell.setCellValue(actual);
        if (!actual.isEmpty() && !expected.isEmpty() && !actual.equals(expected)) {
            actualCell.setCellStyle(styles.mismatch);
        } else if (!actual.isEmpty() && !expected.isEmpty()) {
            actualCell.setCellStyle(styles.match);
        }
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void kv(Sheet sheet, int rowIdx, String key, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
    }

    /** Excel sheet names: at most 31 characters, none of {@code []:*?/\}, unique within the workbook. */
    static String uniqueSheetName(Set<String> taken, String groupId) {
        String base = groupId.replaceAll("[\\[\\]:*?/\\\\]", "_");
        if (base.isBlank()) base = "group";
        if (base.length() > 31) base = base.substring(0, 31);
        String candidate = base;
        int n = 2;
        while (!taken.add(candidate)) {
            String suffix = " (" + (n++) + ")";
            candidate = (base.length() + suffix.length() > 31 ? base.substring(0, 31 - suffix.length()) : base) + suffix;
        }
        return candidate;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle expectedHeader;
        final CellStyle actualHeader;
        final CellStyle mismatch;
        final CellStyle match;

        Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            header = workbook.createCellStyle();
            header.setFont(bold);
            expectedHeader = workbook.createCellStyle();
            expectedHeader.setFont(bold);
            expectedHeader.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            expectedHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            actualHeader = workbook.createCellStyle();
            actualHeader.setFont(bold);
            actualHeader.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            actualHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            mismatch = workbook.createCellStyle();
            mismatch.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            mismatch.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            match = workbook.createCellStyle();
            match.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            match.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
    }
}
