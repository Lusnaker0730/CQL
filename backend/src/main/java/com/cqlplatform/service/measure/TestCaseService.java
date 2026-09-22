package com.cqlplatform.service.measure;

import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.BundleParseException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ca.uhn.fhir.context.FhirContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestCaseService {

    private final TestCaseRepository repository;
    private final MeasureDefinitionService definitionService;
    private final CqlExecutionService cqlExecutionService;
    private final DateShiftService dateShiftService;
    private final FhirContext fhirContext;
    private final PopulationEvaluator populationEvaluator;
    private final StratifierEvaluator stratifierEvaluator;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** Observation values are doubles out of the CQL engine; compare with a small tolerance. */
    private static final double OBSERVATION_TOLERANCE = 1e-6;

    // ===== CRUD =====

    @Transactional(readOnly = true)
    public List<TestCase> getTestCasesForMeasure(Long measureDefinitionId) {
        return repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(measureDefinitionId)
                .stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<TestCase> getById(Long id) {
        return repository.findById(id).map(this::entityToModel);
    }

    @Transactional
    public TestCase create(Long measureDefinitionId, TestCase testCase) {
        // Verify measure exists
        MeasureDefinition measure = definitionService.getById(measureDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureDefinitionId));
        validateExpectedValues(measure, testCase.getExpectedValues());

        TestCaseEntity entity = modelToEntity(testCase);
        entity.setMeasureDefinitionId(measureDefinitionId);
        entity = repository.save(entity);
        log.info("Created test case '{}' for measure {}", entity.getTitle(), measureDefinitionId);
        return entityToModel(entity);
    }

    @Transactional
    public TestCase update(Long id, TestCase testCase) {
        TestCaseEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Test case not found: " + id));

        if (testCase.getExpectedValues() != null && !testCase.getExpectedValues().isEmpty()) {
            Long measureId = entity.getMeasureDefinitionId();
            MeasureDefinition measure = definitionService.getById(measureId)
                    .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
            validateExpectedValues(measure, testCase.getExpectedValues());
        }

        entity.setTitle(testCase.getTitle());
        entity.setDescription(testCase.getDescription());
        entity.setPatientBundleJson(testCase.getPatientBundleJson());
        entity.setExpectedPopulationMap(testCase.getExpectedPopulations() != null
                ? testCase.getExpectedPopulations() : new LinkedHashMap<>());
        entity.setExpectedValues(writeExpectedValues(testCase.getExpectedValues()));
        entity.setSeries(testCase.getSeries());
        entity.setSortOrder(testCase.getSortOrder() != null ? testCase.getSortOrder() : 0);

        entity = repository.save(entity);
        log.info("Updated test case '{}'", entity.getTitle());
        return entityToModel(entity);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
        log.info("Deleted test case {}", id);
    }

    // ===== Batch Import =====

    @Transactional
    public BatchTestCaseImportResult batchImport(Long measureDefinitionId, List<TestCase> testCases, int dateShiftDays) {
        definitionService.getById(measureDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureDefinitionId));

        List<TestCase> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < testCases.size(); i++) {
            try {
                TestCase tc = testCases.get(i);
                // Apply date shifting if requested
                if (dateShiftDays != 0 && tc.getPatientBundleJson() != null && !tc.getPatientBundleJson().isBlank()) {
                    tc.setPatientBundleJson(dateShiftService.shiftDates(tc.getPatientBundleJson(), dateShiftDays));
                }
                TestCase created = create(measureDefinitionId, tc);
                imported.add(created);
            } catch (Exception e) {
                String title = testCases.get(i).getTitle();
                errors.add(String.format("Item %d (%s): %s", i + 1, title != null ? title : "untitled", e.getMessage()));
                log.warn("Failed to import test case {} for measure {}", i, measureDefinitionId, e);
            }
        }

        log.info("Batch imported {}/{} test cases for measure {} (dateShift={})",
                imported.size(), testCases.size(), measureDefinitionId, dateShiftDays);

        return BatchTestCaseImportResult.builder()
                .totalReceived(testCases.size())
                .successCount(imported.size())
                .failureCount(errors.size())
                .imported(imported)
                .errors(errors)
                .build();
    }

    // ===== Execution =====

    @Transactional
    public TestCaseRunResult runTestCase(Long testCaseId) {
        return runTestCase(testCaseId, false);
    }

    @Transactional
    public TestCaseRunResult runTestCase(Long testCaseId, boolean debugMode) {
        TestCaseEntity entity = repository.findById(testCaseId)
                .orElseThrow(() -> new IllegalArgumentException("Test case not found: " + testCaseId));

        MeasureDefinition measure = definitionService.getById(entity.getMeasureDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Measure not found: " + entity.getMeasureDefinitionId()));

        TestCaseRunResult result = executeTestCase(entity, measure, debugMode);
        persistRunResult(entity, result);
        return result;
    }

    @Transactional
    public List<TestCaseRunResult> runAllTestCases(Long measureDefinitionId) {
        return runAllTestCases(measureDefinitionId, false);
    }

    private static final int DEBUG_BATCH_CAP = 20;

    @Transactional
    public List<TestCaseRunResult> runAllTestCases(Long measureDefinitionId, boolean debugMode) {
        MeasureDefinition measure = definitionService.getById(measureDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureDefinitionId));

        List<TestCaseEntity> entities = repository
                .findByMeasureDefinitionIdOrderByCreatedAtAsc(measureDefinitionId);

        // Cap debug mode for large suites to avoid per-expression evaluation overhead
        boolean effectiveDebug = debugMode;
        if (debugMode && entities.size() > DEBUG_BATCH_CAP) {
            log.warn("Debug mode disabled for batch run — {} test cases exceeds cap of {}",
                    entities.size(), DEBUG_BATCH_CAP);
            effectiveDebug = false;
        }

        List<TestCaseRunResult> results = new ArrayList<>();
        for (TestCaseEntity entity : entities) {
            TestCaseRunResult result = executeTestCase(entity, measure, effectiveDebug);
            persistRunResult(entity, result);
            results.add(result);
        }
        return results;
    }

    /**
     * Persists the run outcome to the entity. Strips large debug fields before serializing
     * to keep {@code lastRunResultJson} from bloating the DB row.
     */
    private void persistRunResult(TestCaseEntity entity, TestCaseRunResult result) {
        entity.setStatus(result.getStatus());
        entity.setLastRunAt(LocalDateTime.now());
        entity.setLastRunActualPopulationMap(result.getActualPopulations() != null
                ? result.getActualPopulations() : new LinkedHashMap<>());
        try {
            TestCaseRunResult stored = result.toBuilder()
                    .debugTrace(null).populationTrace(null).coverage(null).clauseCoverage(null).build();
            entity.setLastRunResultJson(MAPPER.writeValueAsString(stored));
        } catch (Exception e) {
            entity.setLastRunResultJson("{}");
        }
        repository.save(entity);
    }

    // ===== Coverage =====

    /**
     * PAT-232 — clause coverage of the measure's CQL across all its test cases: a clause counts
     * as covered when ANY test case reached it. Runs every test case with coverage recording
     * (single patient each, no debug traces) and merges. Run outcomes are persisted like a
     * normal run-all, so the list's pass / fail badges stay current.
     */
    @Transactional
    public MeasureClauseCoverage measureClauseCoverage(Long measureDefinitionId) {
        MeasureDefinition measure = definitionService.getById(measureDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureDefinitionId));
        List<TestCaseEntity> entities = repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(measureDefinitionId);
        List<ClauseCoverage> runs = new ArrayList<>();
        int executed = 0;
        int passed = 0;
        for (TestCaseEntity entity : entities) {
            TestCaseRunResult result = executeTestCase(entity, measure, false, true);
            persistRunResult(entity, result);
            if (result.getClauseCoverage() != null) {
                executed++;
                runs.add(result.getClauseCoverage());
            }
            if ("pass".equals(result.getStatus())) passed++;
        }
        return MeasureClauseCoverage.builder()
                .measureId(measureDefinitionId)
                .testCases(entities.size())
                .executed(executed)
                .passed(passed)
                .coverage(ClauseCoverage.merge(runs))
                .build();
    }

    /**
     * Backward-compat endpoint. Delegates to {@link #runTestCase(Long, boolean)} with debugMode=true
     * and returns only the coverage portion. Preserves the original contract of returning an
     * empty CoverageResult on any failure (rather than null) so existing callers keep working.
     */
    @Transactional
    public CoverageResult runWithCoverage(Long testCaseId) {
        try {
            TestCaseRunResult result = runTestCase(testCaseId, true);
            return result.getCoverage() != null ? result.getCoverage() : emptyCoverage();
        } catch (Exception e) {
            log.error("Coverage analysis failed for test case {}", testCaseId, e);
            return emptyCoverage();
        }
    }

    private CoverageResult emptyCoverage() {
        return CoverageResult.builder()
                .definitions(Collections.emptyList())
                .functions(Collections.emptyList())
                .build();
    }

    private TestCaseRunResult executeTestCase(TestCaseEntity entity, MeasureDefinition measure, boolean debugMode) {
        return executeTestCase(entity, measure, debugMode, debugMode);
    }

    /**
     * {@code clauseCoverage} is separate from {@code debugMode}: the measure-wide coverage run
     * wants every test case's clause hits without the per-expression traces debug mode adds.
     */
    private TestCaseRunResult executeTestCase(TestCaseEntity entity, MeasureDefinition measure,
                                              boolean debugMode, boolean clauseCoverage) {
        long startTime = System.currentTimeMillis();

        if (measure.getCqlContent() == null || measure.getCqlContent().isBlank()) {
            return TestCaseRunResult.builder()
                    .testCaseId(entity.getId())
                    .testCaseTitle(entity.getTitle())
                    .status("error")
                    .errorMessage("Measure has no CQL content")
                    .build();
        }

        String currentPhase = "BUNDLE_PARSE";
        try {
            String patientId = extractPatientIdFromBundle(entity.getPatientBundleJson());
            List<Resource> resources = parseBundleResources(entity.getPatientBundleJson());
            PrefetchRetrieveProvider bundleProvider = new PrefetchRetrieveProvider(resources, patientId);

            currentPhase = "CQL_EXECUTION";
            CqlExecutionRequest execRequest = new CqlExecutionRequest();
            execRequest.setCql(measure.getCqlContent());
            execRequest.setPatientId(patientId);
            execRequest.setParameters(buildMeasurementPeriodParams(measure));
            execRequest.setDebugMode(debugMode);
            execRequest.setClauseCoverage(clauseCoverage);

            CqlExecutionResponse execResponse = cqlExecutionService.executeWithProvider(execRequest, bundleProvider);

            if (!execResponse.isSuccess()) {
                TestCaseRunResult.TestCaseRunResultBuilder b = TestCaseRunResult.builder()
                        .testCaseId(entity.getId())
                        .testCaseTitle(entity.getTitle())
                        .status("error")
                        .errorMessage(execResponse.getErrors() != null
                                ? String.join("; ", execResponse.getErrors()) : "Execution failed")
                        .executionTimeMs(System.currentTimeMillis() - startTime);
                if (debugMode) {
                    b.phaseError(TestCaseRunResult.PhaseError.builder()
                            .phase("CQL_EXECUTION")
                            .message(execResponse.getErrors() != null
                                    ? String.join("; ", execResponse.getErrors()) : "Execution failed")
                            .build())
                     .debugTrace(execResponse.getDebugTrace());
                }
                return b.build();
            }

            currentPhase = "POPULATION_EVAL";
            Map<String, Boolean> actualPopulations = buildActualPopulations(execResponse, measure);
            Map<String, Boolean> expectedPopulations = entity.getExpectedPopulationMap();
            // Structured actual values are computed on every run (also for legacy test cases)
            // so the editor can offer them as a starting point for a structured expectation.
            TestCaseExpectedValues actualValues = buildActualValues(measure, execResponse);
            // Strict on purpose: if a stored structured expectation cannot be read, the run is an
            // ERROR. Quietly comparing the legacy boolean map instead would report a pass / fail
            // for something the author is no longer testing.
            TestCaseExpectedValues expectedValues = readExpectedValuesStrict(entity.getExpectedValues());

            TestCaseRunResult.TestCaseRunResultBuilder b = TestCaseRunResult.builder()
                    .testCaseId(entity.getId())
                    .testCaseTitle(entity.getTitle())
                    .expectedPopulations(expectedPopulations)
                    .actualPopulations(actualPopulations)
                    .actualValues(actualValues);

            if (expectedValues != null && !expectedValues.isEmpty()) {
                // PAT-228: the structured expectation decides pass / fail; the flat boolean map
                // is ignored because it cannot tell groups apart and sees raw define results.
                List<TestCaseRunResult.ValueComparison> valueComparisons =
                        compareValues(expectedValues, actualValues);
                boolean allMatch = valueComparisons.stream().allMatch(TestCaseRunResult.ValueComparison::isMatch);
                b.status(allMatch ? "pass" : "fail")
                 .expectedValues(expectedValues)
                 .valueComparisons(valueComparisons);
            } else {
                List<TestCaseRunResult.PopulationComparison> comparisons = buildComparisons(
                        expectedPopulations, actualPopulations);
                boolean allMatch = comparisons.stream().allMatch(TestCaseRunResult.PopulationComparison::isMatch);
                b.status(allMatch ? "pass" : "fail")
                 .comparisons(comparisons);
            }
            b.executionTimeMs(System.currentTimeMillis() - startTime);

            if (debugMode) {
                b.debugTrace(execResponse.getDebugTrace())
                 .populationTrace(populationEvaluator.buildTestCaseTrace(measure, execResponse))
                 .coverage(computeCoverage(execResponse));
            }
            if (clauseCoverage) {
                b.clauseCoverage(execResponse.getClauseCoverage());
            }

            return b.build();

        } catch (BundleParseException e) {
            return errorResult(entity, "BUNDLE_PARSE", e, debugMode, startTime);
        } catch (Exception e) {
            log.error("Failed to execute test case '{}' in phase {}", entity.getTitle(), currentPhase, e);
            return errorResult(entity, currentPhase, e, debugMode, startTime);
        }
    }

    private TestCaseRunResult errorResult(TestCaseEntity entity, String phase, Throwable e,
                                          boolean debugMode, long startTime) {
        TestCaseRunResult.TestCaseRunResultBuilder b = TestCaseRunResult.builder()
                .testCaseId(entity.getId())
                .testCaseTitle(entity.getTitle())
                .status("error")
                .errorMessage(e.getMessage())
                .executionTimeMs(System.currentTimeMillis() - startTime);
        if (debugMode) {
            List<String> frames = Arrays.stream(e.getStackTrace())
                    .filter(f -> f.getClassName().startsWith("com.cqlplatform"))
                    .limit(5)
                    .map(StackTraceElement::toString)
                    .toList();
            b.phaseError(TestCaseRunResult.PhaseError.builder()
                    .phase(phase)
                    .message(e.getMessage())
                    .stackHint(frames.isEmpty() ? null : frames)
                    .build());
        }
        return b.build();
    }

    /**
     * Classifies CQL expression results into definitions vs functions with relevance + result string.
     * Shared by debug-mode runs and the backward-compat {@link #runWithCoverage} path.
     */
    private CoverageResult computeCoverage(CqlExecutionResponse execResponse) {
        List<CoverageResult.ExpressionCoverage> definitions = new ArrayList<>();
        List<CoverageResult.ExpressionCoverage> functions = new ArrayList<>();
        if (execResponse.getResults() != null) {
            for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : execResponse.getResults().entrySet()) {
                String name = entry.getKey();
                CqlExecutionResponse.ExpressionResult result = entry.getValue();
                boolean truthy = isTruthy(result);
                String relevance = result.getValue() == null ? "NA" : (truthy ? "TRUE" : "FALSE");
                String resultStr = result.getDisplayValue() != null
                        ? result.getDisplayValue() : String.valueOf(result.getValue());
                String type = result.getValueType() != null ? result.getValueType() : "unknown";
                CoverageResult.ExpressionCoverage item = CoverageResult.ExpressionCoverage.builder()
                        .name(name).type(type).relevance(relevance).result(resultStr).build();
                // Names containing parens or starting with lowercase likely denote functions
                if (name.contains("(") || (!name.isEmpty() && Character.isLowerCase(name.charAt(0)))) {
                    functions.add(item);
                } else {
                    definitions.add(item);
                }
            }
        }
        return CoverageResult.builder().definitions(definitions).functions(functions).build();
    }

    private Map<String, Boolean> buildActualPopulations(CqlExecutionResponse response,
                                                         MeasureDefinition measure) {
        Map<String, Boolean> actual = new LinkedHashMap<>();
        Map<String, CqlExecutionResponse.ExpressionResult> results = response.getResults();
        if (results == null) return actual;

        // Map population criteria expressions to population types
        if (measure.getGroupDefinitions() != null) {
            for (GroupDefinition group : measure.getGroupDefinitions()) {
                if (group.getPopulations() != null) {
                    for (PopulationDefinition pop : group.getPopulations()) {
                        String exprName = pop.getCriteriaExpression();
                        if (exprName != null && results.containsKey(exprName)) {
                            CqlExecutionResponse.ExpressionResult exprResult = results.get(exprName);
                            actual.put(pop.getPopulationType(), isTruthy(exprResult));
                        }
                    }
                }
            }
        }

        // If no group definitions, try standard population expression names
        if (actual.isEmpty()) {
            for (Map.Entry<String, CqlExecutionResponse.ExpressionResult> entry : results.entrySet()) {
                actual.put(entry.getKey(), isTruthy(entry.getValue()));
            }
        }

        return actual;
    }

    private boolean isTruthy(CqlExecutionResponse.ExpressionResult result) {
        if (result == null || result.getValue() == null) return false;
        Object val = result.getValue();
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() > 0;
        if (val instanceof Collection) return !((Collection<?>) val).isEmpty();
        // Non-null, non-false = truthy (e.g. a retrieved resource exists)
        return true;
    }

    private List<TestCaseRunResult.PopulationComparison> buildComparisons(
            Map<String, Boolean> expected, Map<String, Boolean> actual) {
        Set<String> allKeys = new LinkedHashSet<>();
        if (expected != null) allKeys.addAll(expected.keySet());
        allKeys.addAll(actual.keySet());

        List<TestCaseRunResult.PopulationComparison> comparisons = new ArrayList<>();
        for (String key : allKeys) {
            Boolean exp = expected != null ? expected.get(key) : null;
            Boolean act = actual.get(key);
            comparisons.add(TestCaseRunResult.PopulationComparison.builder()
                    .populationType(key)
                    .expected(exp)
                    .actual(act)
                    .match(Objects.equals(exp, act))
                    .build());
        }
        return comparisons;
    }

    // ===== Structured expected values (PAT-228) =====

    /** The id a group is addressed by; groups without one are numbered like the evaluation does. */
    static String effectiveGroupId(GroupDefinition group, int index) {
        return group.getGroupId() != null && !group.getGroupId().isBlank()
                ? group.getGroupId() : "group-" + (index + 1);
    }

    /**
     * Per group: what this patient contributes — effective population counts and observation
     * values from {@link PopulationEvaluator#evaluateSinglePatient} (the production rules), plus
     * the stratum the patient falls into for each stratifier. {@code null} when the measure has
     * no group definitions (nothing structured to describe).
     */
    private TestCaseExpectedValues buildActualValues(MeasureDefinition measure, CqlExecutionResponse response) {
        List<GroupDefinition> groups = measure.getGroupDefinitions();
        Map<String, CqlExecutionResponse.ExpressionResult> results = response.getResults();
        if (groups == null || groups.isEmpty() || results == null) return null;

        List<TestCaseExpectedValues.GroupValues> out = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            GroupDefinition group = groups.get(i);
            TestCaseExpectedValues.GroupValues values =
                    populationEvaluator.evaluateSinglePatient(measure.getScoringType(), groups, group, results);
            if (values == null) continue;
            values.setGroupId(effectiveGroupId(group, i));

            if (group.getStratifiers() != null && !group.getStratifiers().isEmpty()) {
                Map<String, String> strata = new LinkedHashMap<>();
                for (StratifierDefinition stratifier : group.getStratifiers()) {
                    if (stratifier.getStratifierId() == null) continue;
                    String stratum = stratifierEvaluator.resolveStratumValue(stratifier, results);
                    strata.put(stratifier.getStratifierId(), stratum != null ? stratum : "");
                }
                values.setStratifiers(strata);
            }
            out.add(values);
        }
        return TestCaseExpectedValues.builder().groups(out).build();
    }

    /**
     * Compares every group the expectation lists. Within a group: all populations (one the
     * expectation omits is expected to be 0), the observation values when asserted
     * (order-insensitive), and only the stratifiers that are listed.
     */
    private List<TestCaseRunResult.ValueComparison> compareValues(TestCaseExpectedValues expected,
                                                                  TestCaseExpectedValues actual) {
        Map<String, TestCaseExpectedValues.GroupValues> actualByGroup = new LinkedHashMap<>();
        if (actual != null && actual.getGroups() != null) {
            for (TestCaseExpectedValues.GroupValues g : actual.getGroups()) {
                actualByGroup.put(g.getGroupId(), g);
            }
        }

        List<TestCaseRunResult.ValueComparison> comparisons = new ArrayList<>();
        for (TestCaseExpectedValues.GroupValues exp : expected.getGroups()) {
            String groupId = exp.getGroupId();
            TestCaseExpectedValues.GroupValues act = actualByGroup.get(groupId);
            if (act == null) {
                // The measure changed after the expectation was saved — never a silent pass.
                comparisons.add(TestCaseRunResult.ValueComparison.builder()
                        .groupId(groupId).kind(TestCaseRunResult.ValueComparison.KIND_POPULATION)
                        .key("*").expected("group exists").actual("group not in measure").match(false).build());
                continue;
            }

            Map<String, Integer> expPops = exp.getPopulations() != null ? exp.getPopulations() : Map.of();
            Map<String, Integer> actPops = act.getPopulations() != null ? act.getPopulations() : Map.of();
            Set<String> popKeys = new LinkedHashSet<>(actPops.keySet());
            popKeys.addAll(expPops.keySet());
            for (String key : popKeys) {
                int e = expPops.getOrDefault(key, 0) != null ? expPops.getOrDefault(key, 0) : 0;
                int a = actPops.getOrDefault(key, 0) != null ? actPops.getOrDefault(key, 0) : 0;
                comparisons.add(TestCaseRunResult.ValueComparison.builder()
                        .groupId(groupId).kind(TestCaseRunResult.ValueComparison.KIND_POPULATION)
                        .key(key).expected(String.valueOf(e)).actual(String.valueOf(a)).match(e == a).build());
            }

            if (exp.getObservations() != null) {
                List<Double> e = sortedValues(exp.getObservations());
                List<Double> a = sortedValues(act.getObservations());
                comparisons.add(TestCaseRunResult.ValueComparison.builder()
                        .groupId(groupId).kind(TestCaseRunResult.ValueComparison.KIND_OBSERVATION)
                        .key("values").expected(renderValues(e)).actual(renderValues(a))
                        .match(sameValues(e, a)).build());
            }

            if (exp.getStratifiers() != null) {
                Map<String, String> actStrata = act.getStratifiers() != null ? act.getStratifiers() : Map.of();
                for (Map.Entry<String, String> entry : exp.getStratifiers().entrySet()) {
                    String e = entry.getValue() != null ? entry.getValue().trim() : "";
                    String a = actStrata.getOrDefault(entry.getKey(), "");
                    comparisons.add(TestCaseRunResult.ValueComparison.builder()
                            .groupId(groupId).kind(TestCaseRunResult.ValueComparison.KIND_STRATIFIER)
                            .key(entry.getKey()).expected(e).actual(a).match(e.equalsIgnoreCase(a)).build());
                }
            }
        }
        return comparisons;
    }

    private List<Double> sortedValues(List<Double> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).sorted().toList();
    }

    private boolean sameValues(List<Double> expected, List<Double> actual) {
        if (expected.size() != actual.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            if (Math.abs(expected.get(i) - actual.get(i)) > OBSERVATION_TOLERANCE) return false;
        }
        return true;
    }

    /** {@code [30, 45.5]} — integers without a trailing ".0" so the UI reads naturally. */
    private String renderValues(List<Double> values) {
        return values.stream()
                .map(v -> v == Math.rint(v) && !Double.isInfinite(v) ? String.valueOf(v.longValue()) : String.valueOf(v))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Rejects an expectation that cannot match the measure — an unknown group, population or
     * stratifier would otherwise produce a test that fails (or worse, passes) for a reason the
     * author never sees. Checked on save; a measure edited afterwards is caught at run time.
     */
    private void validateExpectedValues(MeasureDefinition measure, TestCaseExpectedValues expected) {
        if (expected == null || expected.isEmpty()) return;

        List<GroupDefinition> groups = measure.getGroupDefinitions() != null
                ? measure.getGroupDefinitions() : List.of();
        Map<String, GroupDefinition> byId = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            byId.put(effectiveGroupId(groups.get(i), i), groups.get(i));
        }

        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TestCaseExpectedValues.GroupValues values : expected.getGroups()) {
            String groupId = values.getGroupId();
            GroupDefinition group = groupId != null ? byId.get(groupId) : null;
            if (group == null) {
                problems.add("Unknown population group '" + groupId + "' (measure has: " + byId.keySet() + ")");
                continue;
            }
            if (!seen.add(groupId)) {
                problems.add("Population group '" + groupId + "' is listed more than once");
            }

            Set<String> populationTypes = new HashSet<>();
            if (group.getPopulations() != null) {
                group.getPopulations().forEach(p -> populationTypes.add(p.getPopulationType()));
            }
            if (values.getPopulations() != null) {
                values.getPopulations().forEach((type, count) -> {
                    if (!populationTypes.contains(type)) {
                        problems.add("Group '" + groupId + "' has no population '" + type + "'");
                    } else if (count == null || count < 0) {
                        problems.add("Group '" + groupId + "' population '" + type + "' needs a count of 0 or more");
                    }
                });
            }

            if (values.getObservations() != null) {
                for (Double v : values.getObservations()) {
                    if (v == null || v.isNaN() || v.isInfinite()) {
                        problems.add("Group '" + groupId + "' has an observation value that is not a number");
                        break;
                    }
                }
            }

            Set<String> stratifierIds = new HashSet<>();
            if (group.getStratifiers() != null) {
                group.getStratifiers().forEach(s -> stratifierIds.add(s.getStratifierId()));
            }
            if (values.getStratifiers() != null) {
                for (String stratifierId : values.getStratifiers().keySet()) {
                    if (!stratifierIds.contains(stratifierId)) {
                        problems.add("Group '" + groupId + "' has no stratifier '" + stratifierId + "'");
                    }
                }
            }
        }

        if (!problems.isEmpty()) {
            throw new ValidationException("Expected values do not match the measure", problems);
        }
    }

    /** Lenient read for listing / editing: an unreadable value shows up as "no structured expectation". */
    private TestCaseExpectedValues readExpectedValues(String json) {
        try {
            return readExpectedValuesStrict(json);
        } catch (IllegalStateException e) {
            log.warn("Could not read structured expected values: {}", e.getMessage());
            return null;
        }
    }

    /** Strict read for runs — see the call site for why a run must not fall back silently. */
    private TestCaseExpectedValues readExpectedValuesStrict(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, TestCaseExpectedValues.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Stored expected values of this test case could not be read: " + e.getMessage(), e);
        }
    }

    /** {@code null} (not "{}") when there is nothing structured, so the column stays NULL. */
    private String writeExpectedValues(TestCaseExpectedValues values) {
        if (values == null || values.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            throw new ValidationException("Expected values could not be stored: " + e.getMessage());
        }
    }

    private List<Resource> parseBundleResources(String bundleJson) {
        List<Resource> resources = new ArrayList<>();
        if (bundleJson == null || bundleJson.isBlank()) return resources;
        try {
            Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.hasResource()) {
                    resources.add(entry.getResource());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse test case bundle: {}", e.getMessage());
            throw new BundleParseException("Failed to parse test case bundle: " + e.getMessage(), e);
        }
        return resources;
    }

    private Map<String, Object> buildMeasurementPeriodParams(MeasureDefinition measure) {
        // Use current year as default measurement period (Jan 1 – Dec 31)
        int year = Year.now().getValue();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("Measurement Period",
                new Interval(
                        new DateTime(OffsetDateTime.of(LocalDate.of(year, 1, 1), LocalTime.MIN, ZoneOffset.UTC)),
                        true,
                        new DateTime(OffsetDateTime.of(LocalDate.of(year, 12, 31), LocalTime.MAX, ZoneOffset.UTC)),
                        true));
        return parameters;
    }

    private String extractPatientIdFromBundle(String bundleJson) {
        if (bundleJson == null || bundleJson.isBlank()) return "test-patient";
        try {
            var node = MAPPER.readTree(bundleJson);
            var entries = node.get("entry");
            if (entries != null && entries.isArray()) {
                for (var entry : entries) {
                    var resource = entry.get("resource");
                    if (resource != null && "Patient".equals(resource.path("resourceType").asText())) {
                        String id = resource.path("id").asText(null);
                        if (id != null) return id;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract patient ID from bundle, using default", e);
        }
        return "test-patient";
    }

    // ===== Entity ↔ Model Conversion =====

    private TestCase entityToModel(TestCaseEntity entity) {
        return TestCase.builder()
                .id(entity.getId())
                .measureDefinitionId(entity.getMeasureDefinitionId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .patientBundleJson(entity.getPatientBundleJson())
                .expectedPopulations(entity.getExpectedPopulationMap())
                .expectedValues(readExpectedValues(entity.getExpectedValues()))
                .status(entity.getStatus())
                .lastRunResultJson(entity.getLastRunResultJson())
                .lastRunActualPopulations(entity.getLastRunActualPopulationMap())
                .lastRunAt(entity.getLastRunAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .series(entity.getSeries())
                .sortOrder(entity.getSortOrder())
                .build();
    }

    private TestCaseEntity modelToEntity(TestCase model) {
        return TestCaseEntity.builder()
                .title(model.getTitle())
                .description(model.getDescription())
                .patientBundleJson(model.getPatientBundleJson())
                .expectedPopulationMap(model.getExpectedPopulations() != null
                        ? model.getExpectedPopulations() : new LinkedHashMap<>())
                .expectedValues(writeExpectedValues(model.getExpectedValues()))
                .status(model.getStatus() != null ? model.getStatus() : "pending")
                .series(model.getSeries())
                .sortOrder(model.getSortOrder() != null ? model.getSortOrder() : 0)
                .build();
    }
}
