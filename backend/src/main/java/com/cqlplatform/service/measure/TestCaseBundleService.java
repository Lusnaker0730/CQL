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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * PAT-247 — test cases in the shape MADiE (and the CQF Measures IG) exchange them: one FHIR
 * {@code collection} Bundle per test case holding the patient's resources <em>and</em> a
 * {@code MeasureReport} with profile {@code test-case-cqfm} that carries the expectation —
 * one {@code group.population} per population with its expected {@code count}, measure
 * observations as {@code measure-observation} populations, strata as {@code group.stratifier}.
 * Export writes a zip of those bundles (plus a README); import reads a zip, a single bundle or an
 * array of bundles and turns the MeasureReport back into the platform's structured expectation.
 *
 * <p>Compatibility notes: the MeasureReport also carries MADiE's {@code cqfm-isTestCase}
 * modifier extension, the {@code cqf-inputParameters} reference to a contained {@code Parameters}
 * naming the subject, and {@code cqfm-testCaseDescription}. MADiE derives a test case's title and
 * series from the Patient's given / family names on import; this import does the same (falling
 * back to the file name), so a suite exported from MADiE lands with the names MADiE would show.
 * Observation values may be decimals while {@code MeasureReport.population.count} is an integer,
 * so each observation population also carries a platform extension with the decimal value.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestCaseBundleService {

    public static final String PROFILE_TEST_CASE = "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/test-case-cqfm";
    public static final String EXT_IS_TEST_CASE = "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-isTestCase";
    public static final String EXT_INPUT_PARAMETERS = "http://hl7.org/fhir/StructureDefinition/cqf-inputParameters";
    public static final String EXT_DESCRIPTION = "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-testCaseDescription";
    public static final String MEASURE_POPULATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/measure-population";
    public static final String CODE_MEASURE_OBSERVATION = "measure-observation";
    /** Platform extension on a {@code measure-observation} population: the expected value as a decimal. */
    public static final String EXT_OBSERVATION_VALUE_PATH = "/StructureDefinition/testcase-observation-value";

    /** Import guards. */
    static final int MAX_ZIP_ENTRIES = 500;
    static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final TestCaseRepository repository;
    private final MeasureDefinitionService definitionService;
    private final TestCaseService testCaseService;
    private final FhirCanonicalResolver canonical;

    // ───────────────────────────────────────────────────────────────── export

    /** The zip of test case bundles for {@code measureId} ({@code testCaseIds} null / empty = all). */
    @Transactional(readOnly = true)
    public byte[] exportZip(Long measureId, Collection<Long> testCaseIds) {
        MeasureDefinition measure = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
        List<TestCaseEntity> entities = repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(measureId);
        if (testCaseIds != null && !testCaseIds.isEmpty()) {
            Set<Long> wanted = new HashSet<>(testCaseIds);
            entities = entities.stream().filter(e -> wanted.contains(e.getId())).toList();
        }
        if (entities.isEmpty()) {
            throw new ValidationException("The measure has no test cases to export");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            Set<String> names = new HashSet<>();
            for (TestCaseEntity entity : entities) {
                ObjectNode bundle = exportBundle(measure, entity);
                String name = uniqueName(names, fileName(measure, entity));
                zip.putNextEntry(new ZipEntry(name));
                zip.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(readme(measure, entities.size()).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the test case zip: " + e.getMessage(), e);
        }
    }

    /** The exchange bundle of one test case: its patient bundle as a collection plus the test-case MeasureReport. */
    ObjectNode exportBundle(MeasureDefinition measure, TestCaseEntity entity) {
        ObjectNode bundle = parseBundle(entity.getPatientBundleJson());
        bundle.put("type", "collection");
        ArrayNode entries = bundle.withArray("entry");
        String patientId = null;
        List<String> references = new ArrayList<>();
        for (JsonNode entry : entries) {
            ((ObjectNode) entry).remove("request");
            JsonNode resource = entry.path("resource");
            String type = resource.path("resourceType").asText(null);
            String id = resource.path("id").asText(null);
            if ("Patient".equals(type) && patientId == null) patientId = id;
            if (type != null && id != null) references.add(type + "/" + id);
        }
        if (patientId == null) patientId = "patient";

        ObjectNode report = MAPPER.createObjectNode();
        String reportId = UUID.randomUUID().toString();
        report.put("resourceType", "MeasureReport");
        report.put("id", reportId);
        report.putObject("meta").putArray("profile").add(PROFILE_TEST_CASE);
        String parametersId = reportId + "-parameters";
        ObjectNode parameters = report.putArray("contained").addObject();
        parameters.put("resourceType", "Parameters");
        parameters.put("id", parametersId);
        ObjectNode subject = parameters.putArray("parameter").addObject();
        subject.put("name", "subject");
        subject.put("valueString", patientId);
        ArrayNode extensions = report.putArray("extension");
        ObjectNode inputParameters = extensions.addObject();
        inputParameters.put("url", EXT_INPUT_PARAMETERS);
        inputParameters.putObject("valueReference").put("reference", "#" + parametersId);
        String description = entity.getDescription() != null && !entity.getDescription().isBlank()
                ? entity.getDescription() : entity.getTitle();
        ObjectNode descriptionExt = extensions.addObject();
        descriptionExt.put("url", EXT_DESCRIPTION);
        descriptionExt.put("valueMarkdown", description);
        ObjectNode isTestCase = report.putArray("modifierExtension").addObject();
        isTestCase.put("url", EXT_IS_TEST_CASE);
        isTestCase.put("valueBoolean", true);
        report.put("status", "complete");
        report.put("type", "individual");
        report.put("measure", FhirCanonicalResolver.versioned(canonical.measureUrl(measure.getName()), measure.getVersion()));
        LocalDate[] period = TestCaseService.measurementPeriod(measure);
        ObjectNode periodNode = report.putObject("period");
        periodNode.put("start", period[0].toString());
        periodNode.put("end", period[1].toString());
        report.set("group", exportGroups(measure, entity));
        ArrayNode evaluated = report.putArray("evaluatedResource");
        for (String reference : references) evaluated.addObject().put("reference", reference);

        ObjectNode reportEntry = entries.addObject();
        reportEntry.put("fullUrl", "MeasureReport/" + reportId);
        reportEntry.set("resource", report);
        return bundle;
    }

    /** The expectation as MeasureReport groups — structured per group, or the legacy boolean map on the first group. */
    private ArrayNode exportGroups(MeasureDefinition measure, TestCaseEntity entity) {
        ArrayNode groups = MAPPER.createArrayNode();
        List<GroupDefinition> defs = measure.getGroupDefinitions() != null ? measure.getGroupDefinitions() : List.of();
        TestCaseExpectedValues structured = readExpected(entity.getExpectedValues());
        Map<String, TestCaseExpectedValues.GroupValues> byGroup = new HashMap<>();
        if (structured != null && structured.getGroups() != null) {
            for (TestCaseExpectedValues.GroupValues g : structured.getGroups()) byGroup.put(g.getGroupId(), g);
        }
        for (int i = 0; i < defs.size(); i++) {
            GroupDefinition def = defs.get(i);
            String groupId = TestCaseService.effectiveGroupId(def, i);
            ObjectNode group = groups.addObject();
            group.put("id", groupId);
            ArrayNode populations = group.putArray("population");
            TestCaseExpectedValues.GroupValues values = byGroup.get(groupId);
            Map<String, Integer> counts = values != null && values.getPopulations() != null ? values.getPopulations() : null;
            if (counts == null && i == 0 && entity.getExpectedPopulationMap() != null && !entity.getExpectedPopulationMap().isEmpty()) {
                counts = new LinkedHashMap<>();
                for (Map.Entry<String, Boolean> e : entity.getExpectedPopulationMap().entrySet()) {
                    counts.put(e.getKey(), Boolean.TRUE.equals(e.getValue()) ? 1 : 0);
                }
            }
            if (def.getPopulations() != null) {
                for (PopulationDefinition pop : def.getPopulations()) {
                    if (pop.getPopulationType() == null) continue;
                    ObjectNode population = populations.addObject();
                    population.put("id", groupId + "-" + pop.getPopulationType());
                    population.set("code", codeable(pop.getPopulationType()));
                    if (counts != null) population.put("count", counts.getOrDefault(pop.getPopulationType(), 0));
                }
            }
            if (values != null && values.getObservations() != null) {
                for (Double observation : values.getObservations()) {
                    if (observation == null) continue;
                    ObjectNode population = populations.addObject();
                    population.set("code", codeable(CODE_MEASURE_OBSERVATION));
                    if (observation == Math.rint(observation)) population.put("count", observation.intValue());
                    ObjectNode ext = population.putArray("extension").addObject();
                    ext.put("url", canonical.getBase() + EXT_OBSERVATION_VALUE_PATH);
                    ext.put("valueDecimal", observation);
                }
            }
            if (values != null && values.getStratifiers() != null && !values.getStratifiers().isEmpty()) {
                ArrayNode stratifiers = group.putArray("stratifier");
                for (Map.Entry<String, String> stratum : values.getStratifiers().entrySet()) {
                    ObjectNode stratifier = stratifiers.addObject();
                    stratifier.put("id", stratum.getKey());
                    stratifier.putArray("code").addObject().put("text", stratum.getKey());
                    stratifier.putArray("stratum").addObject().putObject("value").put("text", stratum.getValue() != null ? stratum.getValue() : "");
                }
            }
        }
        return groups;
    }

    private ObjectNode codeable(String populationType) {
        ObjectNode code = MAPPER.createObjectNode();
        ObjectNode coding = code.putArray("coding").addObject();
        coding.put("system", MEASURE_POPULATION_SYSTEM);
        coding.put("code", populationType);
        coding.put("display", displayName(populationType));
        return code;
    }

    static String displayName(String populationType) {
        StringBuilder out = new StringBuilder();
        for (String part : populationType.split("-")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    /** {@code <patientId>/<Measure>-v<version>-<series>-<title>.json}, the way MADiE lays its zip out. */
    static String fileName(MeasureDefinition measure, TestCaseEntity entity) {
        String patientId = sanitize(patientIdOf(entity.getPatientBundleJson()));
        String base = sanitize(measure.getName()) + "-v" + sanitize(measure.getVersion());
        if (entity.getSeries() != null && !entity.getSeries().isBlank()) base += "-" + sanitize(entity.getSeries());
        return patientId + "/" + base + "-" + sanitize(entity.getTitle()) + ".json";
    }

    private static String uniqueName(Set<String> taken, String name) {
        String candidate = name;
        int n = 2;
        while (!taken.add(candidate)) {
            candidate = name.replaceAll("\\.json$", "") + "-" + (n++) + ".json";
        }
        return candidate;
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) return "unnamed";
        String cleaned = s.trim().replaceAll("\\s+", "").replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]", "_");
        return cleaned.isEmpty() ? "unnamed" : cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private static String patientIdOf(String bundleJson) {
        try {
            JsonNode root = MAPPER.readTree(bundleJson);
            for (JsonNode entry : root.path("entry")) {
                if ("Patient".equals(entry.path("resource").path("resourceType").asText())) {
                    return entry.path("resource").path("id").asText("patient");
                }
            }
        } catch (Exception ignored) {
            // falls through
        }
        return "patient";
    }

    private String readme(MeasureDefinition measure, int count) {
        return "Test cases of measure " + measure.getName() + " v" + measure.getVersion() + " (" + count + ")\n"
                + "Exported by CQL Platform on " + LocalDate.now() + ".\n\n"
                + "Each JSON file is a FHIR R4 collection Bundle: the test patient's resources plus a MeasureReport\n"
                + "(profile " + PROFILE_TEST_CASE + ") whose group.population[].count values are the expected population\n"
                + "counts, measure-observation populations the expected observation values (decimal in extension\n"
                + canonical.getBase() + EXT_OBSERVATION_VALUE_PATH + "), and group.stratifier[] the expected strata.\n"
                + "The zip imports back into CQL Platform (Test Cases > Import) and into MADiE (QI-Core test case import).\n";
    }

    // ───────────────────────────────────────────────────────────────── import

    /**
     * Imports test cases from a zip of bundles, a single bundle or a JSON array of bundles. A bundle
     * without a test-case MeasureReport is imported with no expectation; an expectation that does
     * not fit the measure's groups is dropped from that test case with a warning.
     */
    @Transactional
    public BatchTestCaseImportResult importFile(Long measureId, byte[] content, String fileName) {
        MeasureDefinition measure = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<TestCase> imported = new ArrayList<>();
        int received = 0;
        for (NamedJson item : unpack(content, fileName, errors)) {
            received++;
            try {
                ParsedCase parsed = parseBundle(measure, item.json(), item.name());
                if (parsed.measureMismatch() != null) warnings.add(item.name() + ": " + parsed.measureMismatch());
                TestCase created;
                try {
                    created = testCaseService.create(measureId, parsed.testCase());
                } catch (ValidationException e) {
                    if (parsed.testCase().getExpectedValues() == null) throw e;
                    parsed.testCase().setExpectedValues(null);
                    created = testCaseService.create(measureId, parsed.testCase());
                    warnings.add(String.format("'%s': expected values dropped — %s", parsed.testCase().getTitle(),
                            e.getDetails() != null && !e.getDetails().isEmpty() ? String.join("; ", e.getDetails()) : e.getMessage()));
                }
                imported.add(created);
            } catch (Exception e) {
                errors.add(item.name() + ": " + e.getMessage());
                log.warn("Failed to import test case bundle {} for measure {}: {}", item.name(), measureId, e.getMessage());
            }
        }
        log.info("Imported {}/{} test case bundles for measure {} ({} warnings)", imported.size(), received, measureId, warnings.size());
        return BatchTestCaseImportResult.builder()
                .totalReceived(received)
                .successCount(imported.size())
                .failureCount(errors.size())
                .imported(imported)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    record NamedJson(String name, JsonNode json) {}

    record ParsedCase(TestCase testCase, String measureMismatch) {}

    /** The JSON documents in the upload: zip entries ending in .json, a JSON array, or one document. */
    List<NamedJson> unpack(byte[] content, String fileName, List<String> errors) {
        List<NamedJson> out = new ArrayList<>();
        boolean zip = (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip"))
                || (content.length > 4 && content[0] == 'P' && content[1] == 'K');
        if (zip) {
            long total = 0;
            try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".json")) continue;
                    if (out.size() >= MAX_ZIP_ENTRIES) throw new ValidationException("The zip has more than " + MAX_ZIP_ENTRIES + " JSON files");
                    byte[] bytes = in.readAllBytes();
                    total += bytes.length;
                    if (total > MAX_UNCOMPRESSED_BYTES) throw new ValidationException("The zip expands to more than 64 MB");
                    String name = entry.getName().contains("/") ? entry.getName().substring(entry.getName().lastIndexOf('/') + 1) : entry.getName();
                    try {
                        out.add(new NamedJson(name, MAPPER.readTree(bytes)));
                    } catch (IOException e) {
                        errors.add(name + ": not valid JSON");
                    }
                }
            } catch (IOException e) {
                throw new ValidationException("The upload is not a readable zip file: " + e.getMessage());
            }
            return out;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (IOException e) {
            throw new ValidationException("The upload is not valid JSON: " + e.getMessage());
        }
        String base = fileName != null ? fileName.replaceAll("\\.json$", "") : "upload";
        if (root.isArray()) {
            int i = 1;
            for (JsonNode item : root) out.add(new NamedJson(base + "-" + (i++), item));
        } else {
            out.add(new NamedJson(base, root));
        }
        return out;
    }

    /** A test case from one exchange bundle; the MeasureReport (if any) becomes the expectation and leaves the bundle. */
    ParsedCase parseBundle(MeasureDefinition measure, JsonNode json, String name) {
        if (!json.isObject() || !"Bundle".equals(json.path("resourceType").asText())) {
            throw new ValidationException("not a FHIR Bundle");
        }
        ObjectNode bundle = (ObjectNode) json.deepCopy();
        ArrayNode entries = bundle.withArray("entry");
        JsonNode report = null;
        JsonNode patient = null;
        for (Iterator<JsonNode> it = entries.iterator(); it.hasNext(); ) {
            JsonNode entry = it.next();
            JsonNode resource = entry.path("resource");
            String type = resource.path("resourceType").asText();
            if ("MeasureReport".equals(type) && isTestCaseReport(resource)) {
                if (report == null) report = resource;
                it.remove();
            } else if ("Patient".equals(type) && patient == null) {
                patient = resource;
            }
        }
        if (entries.isEmpty()) throw new ValidationException("the bundle has no patient resources");
        if ("transaction".equals(bundle.path("type").asText())) {
            bundle.put("type", "collection");
            for (JsonNode entry : entries) ((ObjectNode) entry).remove("request");
        }

        String baseName = name != null ? name.replaceAll("\\.json$", "") : "test case";
        String title = null;
        String series = null;
        JsonNode humanName = patient != null ? patient.path("name").path(0) : null;
        if (humanName != null && !humanName.isMissingNode()) {
            List<String> given = new ArrayList<>();
            for (JsonNode g : humanName.path("given")) given.add(g.asText());
            if (!given.isEmpty()) title = String.join(" ", given);
            if (humanName.hasNonNull("family")) series = humanName.get("family").asText();
        }
        if (title == null || title.isBlank()) title = baseName;
        String description = null;
        String mismatch = null;
        TestCaseExpectedValues expected = null;
        if (report != null) {
            for (JsonNode ext : report.path("extension")) {
                if (EXT_DESCRIPTION.equals(ext.path("url").asText()) && ext.hasNonNull("valueMarkdown")) {
                    description = ext.get("valueMarkdown").asText();
                }
            }
            String reportedMeasure = report.path("measure").asText(null);
            if (reportedMeasure != null && !reportedMeasure.isBlank()) {
                String ours = canonical.measureUrl(measure.getName());
                String reportedBase = reportedMeasure.contains("|") ? reportedMeasure.substring(0, reportedMeasure.indexOf('|')) : reportedMeasure;
                if (!reportedBase.equals(ours)) {
                    mismatch = "the test case was written for measure " + reportedMeasure + " (this measure is " + ours + ")";
                }
            }
            expected = expectationFrom(measure, report);
        }
        TestCase testCase = TestCase.builder()
                .title(title.length() > 200 ? title.substring(0, 200) : title)
                .description(description != null && description.length() > 2000 ? description.substring(0, 2000) : description)
                .series(series != null && series.length() > 100 ? series.substring(0, 100) : series)
                .patientBundleJson(bundle.toString())
                .expectedValues(expected)
                .expectedPopulations(expected != null ? legacyMap(expected) : new LinkedHashMap<>())
                .build();
        return new ParsedCase(testCase, mismatch);
    }

    static boolean isTestCaseReport(JsonNode report) {
        for (JsonNode profile : report.path("meta").path("profile")) {
            if (PROFILE_TEST_CASE.equals(profile.asText())) return true;
        }
        for (JsonNode ext : report.path("modifierExtension")) {
            if (EXT_IS_TEST_CASE.equals(ext.path("url").asText()) && ext.path("valueBoolean").asBoolean(false)) return true;
        }
        return false;
    }

    /** The MeasureReport's groups as the platform's structured expectation, matched to the measure's groups by id then by position. */
    TestCaseExpectedValues expectationFrom(MeasureDefinition measure, JsonNode report) {
        List<GroupDefinition> defs = measure.getGroupDefinitions() != null ? measure.getGroupDefinitions() : List.of();
        if (defs.isEmpty()) return null;
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < defs.size(); i++) indexById.put(TestCaseService.effectiveGroupId(defs.get(i), i), i);
        List<TestCaseExpectedValues.GroupValues> groups = new ArrayList<>();
        int position = 0;
        for (JsonNode group : report.path("group")) {
            String reportedId = group.path("id").asText(null);
            Integer index = reportedId != null ? indexById.get(reportedId) : null;
            if (index == null && position < defs.size()) index = position;
            position++;
            if (index == null) continue; // more groups than the measure has
            String groupId = TestCaseService.effectiveGroupId(defs.get(index), index);
            Map<String, Integer> populations = new LinkedHashMap<>();
            List<Double> observations = new ArrayList<>();
            for (JsonNode population : group.path("population")) {
                String code = population.path("code").path("coding").path(0).path("code").asText(null);
                if (code == null) code = population.path("code").path("text").asText(null);
                if (code == null) continue;
                if (CODE_MEASURE_OBSERVATION.equals(code)) {
                    Double value = null;
                    for (JsonNode ext : population.path("extension")) {
                        if (ext.path("url").asText("").endsWith(EXT_OBSERVATION_VALUE_PATH) && ext.hasNonNull("valueDecimal")) {
                            value = ext.get("valueDecimal").asDouble();
                        }
                    }
                    if (value == null && population.hasNonNull("count")) value = population.get("count").asDouble();
                    if (value != null) observations.add(value);
                } else if (population.hasNonNull("count")) {
                    populations.put(code, population.get("count").asInt());
                }
            }
            Map<String, String> strata = new LinkedHashMap<>();
            for (JsonNode stratifier : group.path("stratifier")) {
                String id = stratifier.path("id").asText(null);
                if (id == null) id = stratifier.path("code").path(0).path("text").asText(null);
                JsonNode stratum = stratifier.path("stratum");
                if (id == null || stratum.size() != 1) continue; // MADiE's true/false pair carries no single expectation
                strata.put(id, stratum.path(0).path("value").path("text").asText(""));
            }
            groups.add(TestCaseExpectedValues.GroupValues.builder()
                    .groupId(groupId)
                    .populations(populations)
                    .observations(observations.isEmpty() ? null : observations)
                    .stratifiers(strata.isEmpty() ? null : strata)
                    .build());
        }
        return groups.isEmpty() ? null : TestCaseExpectedValues.builder().groups(groups).build();
    }

    /** The first group's counts as the legacy boolean map, so list chips keep working. */
    private static Map<String, Boolean> legacyMap(TestCaseExpectedValues expected) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        if (expected.getGroups() == null || expected.getGroups().isEmpty()) return map;
        Map<String, Integer> populations = expected.getGroups().get(0).getPopulations();
        if (populations != null) populations.forEach((k, v) -> map.put(k, v != null && v > 0));
        return map;
    }

    private static ObjectNode parseBundle(String bundleJson) {
        try {
            JsonNode node = bundleJson == null || bundleJson.isBlank() ? null : MAPPER.readTree(bundleJson);
            if (node == null || !node.isObject()) {
                ObjectNode empty = MAPPER.createObjectNode();
                empty.put("resourceType", "Bundle");
                empty.putArray("entry");
                return empty;
            }
            ObjectNode bundle = (ObjectNode) node;
            if (!bundle.has("resourceType")) bundle.put("resourceType", "Bundle");
            return bundle;
        } catch (IOException e) {
            throw new ValidationException("The test case's patient bundle is not valid JSON: " + e.getMessage());
        }
    }

    private static TestCaseExpectedValues readExpected(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, TestCaseExpectedValues.class);
        } catch (IOException e) {
            return null;
        }
    }
}
