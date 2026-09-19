package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.ObservationDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.model.measure.StratifierDefinition;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.model.measure.TestCaseExpectedValues;
import com.cqlplatform.model.measure.TestCaseExpectedValues.GroupValues;
import com.cqlplatform.model.measure.TestCaseRunResult;
import com.cqlplatform.model.measure.TestCaseRunResult.ValueComparison;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-228 — structured expected values of measure test cases.
 *
 * <p>The population / stratifier evaluators are the REAL ones (pure logic): the point of the
 * feature is that a test case compares against what the production evaluation would count for
 * this patient, so these tests pin that the runner goes through the production rules rather
 * than the raw truthiness of each define.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseExpectedValuesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BUNDLE =
            "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"p1\"}}]}";

    @Mock private TestCaseRepository repository;
    @Mock private MeasureDefinitionService definitionService;
    @Mock private CqlExecutionService cqlExecutionService;
    @Mock private DateShiftService dateShiftService;

    private TestCaseService service;

    @BeforeEach
    void setUp() {
        PopulationEvaluator populationEvaluator = new PopulationEvaluator();
        StratifierEvaluator stratifierEvaluator =
                new StratifierEvaluator(populationEvaluator, new MeasureScoreCalculator());
        service = new TestCaseService(repository, definitionService, cqlExecutionService,
                dateShiftService, FhirContext.forR4Cached(), populationEvaluator, stratifierEvaluator);
        lenient().when(repository.save(any(TestCaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ===== fixtures =====

    private static PopulationDefinition pop(String type, String expression) {
        return PopulationDefinition.builder().populationType(type).criteriaExpression(expression).build();
    }

    private static GroupDefinition proportionGroup(String groupId, String suffix) {
        return GroupDefinition.builder()
                .groupId(groupId)
                .populations(List.of(
                        pop("initial-population", "Initial Population" + suffix),
                        pop("denominator", "Denominator" + suffix),
                        pop("denominator-exclusion", "Denominator Exclusions" + suffix),
                        pop("numerator", "Numerator" + suffix)))
                .build();
    }

    private static MeasureDefinition measure(String scoringType, GroupDefinition... groups) {
        return MeasureDefinition.builder()
                .id(10L).name("M").scoringType(scoringType)
                .cqlContent("library M version '1.0'")
                .groupDefinitions(List.of(groups))
                .ownerUsername("user")
                .build();
    }

    private static CqlExecutionResponse.ExpressionResult value(Object v) {
        return CqlExecutionResponse.ExpressionResult.builder().value(v).build();
    }

    private TestCaseEntity entityWith(TestCaseExpectedValues expected, Map<String, Boolean> legacy) throws Exception {
        return TestCaseEntity.builder()
                .id(1L).measureDefinitionId(10L).title("TC")
                .patientBundleJson(BUNDLE)
                .expectedPopulationMap(legacy != null ? new LinkedHashMap<>(legacy) : new LinkedHashMap<>())
                .expectedValues(expected != null ? MAPPER.writeValueAsString(expected) : null)
                .status("pending").sortOrder(0)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private TestCaseRunResult run(MeasureDefinition measure, TestCaseEntity entity,
                                  Map<String, CqlExecutionResponse.ExpressionResult> results) {
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure));
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(CqlExecutionResponse.builder().success(true).results(results).build());
        return service.runTestCase(1L);
    }

    private static TestCaseExpectedValues expected(GroupValues... groups) {
        return TestCaseExpectedValues.builder().groups(List.of(groups)).build();
    }

    private static ValueComparison find(TestCaseRunResult result, String groupId, String kind, String key) {
        return result.getValueComparisons().stream()
                .filter(c -> groupId.equals(c.getGroupId()) && kind.equals(c.getKind()) && key.equals(c.getKey()))
                .findFirst().orElseThrow(() -> new AssertionError("no comparison " + groupId + "/" + kind + "/" + key));
    }

    // ===== population hierarchy =====

    @Test
    void excludedPatient_numeratorIsEffectivelyZero_evenThoughTheRawDefineIsTrue() throws Exception {
        // The raw "Numerator" define is TRUE, but the patient is removed by a denominator
        // exclusion — production counts numerator 0. The legacy flat map saw "numerator: true".
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, proportionGroup("group-1", ""));
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "denominator", 1,
                        "denominator-exclusion", 1, "numerator", 0)).build());

        TestCaseRunResult result = run(m, entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Denominator", value(true),
                "Denominator Exclusions", value(true), "Numerator", value(true)));

        assertThat(result.getStatus()).isEqualTo("pass");
        assertThat(find(result, "group-1", ValueComparison.KIND_POPULATION, "numerator").getActual()).isEqualTo("0");
        // The legacy raw view is still reported for the list chips — and shows why it misled.
        assertThat(result.getActualPopulations()).containsEntry("numerator", true);
        assertThat(result.getComparisons()).isNull();
    }

    @Test
    void wrongPopulationExpectation_fails_andNamesTheMismatch() throws Exception {
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, proportionGroup("group-1", ""));
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "denominator", 1, "numerator", 1)).build());

        TestCaseRunResult result = run(m, entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Denominator", value(true),
                "Denominator Exclusions", value(false), "Numerator", value(false)));

        assertThat(result.getStatus()).isEqualTo("fail");
        ValueComparison numerator = find(result, "group-1", ValueComparison.KIND_POPULATION, "numerator");
        assertThat(numerator.getExpected()).isEqualTo("1");
        assertThat(numerator.getActual()).isEqualTo("0");
        assertThat(numerator.isMatch()).isFalse();
        assertThat(find(result, "group-1", ValueComparison.KIND_POPULATION, "denominator").isMatch()).isTrue();
    }

    @Test
    void populationTheExpectationOmits_isExpectedToBeZero() throws Exception {
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, proportionGroup("group-1", ""));
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1)).build());

        TestCaseRunResult result = run(m, entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Denominator", value(true),
                "Denominator Exclusions", value(false), "Numerator", value(false)));

        assertThat(result.getStatus()).isEqualTo("fail");
        ValueComparison denominator = find(result, "group-1", ValueComparison.KIND_POPULATION, "denominator");
        assertThat(denominator.getExpected()).isEqualTo("0");
        assertThat(denominator.getActual()).isEqualTo("1");
    }

    // ===== multi-group =====

    @Test
    void multiGroup_sameNamedPopulationsAreComparedPerGroup() throws Exception {
        // Both groups have a "numerator"; the flat map collapses them into one key.
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION,
                proportionGroup("group-1", " 1"), proportionGroup("group-2", " 2"));
        TestCaseExpectedValues exp = expected(
                GroupValues.builder().groupId("group-1")
                        .populations(Map.of("initial-population", 1, "denominator", 1, "numerator", 1)).build(),
                GroupValues.builder().groupId("group-2")
                        .populations(Map.of("initial-population", 1, "denominator", 1, "numerator", 0)).build());

        TestCaseRunResult result = run(m, entityWith(exp, null), Map.of(
                "Initial Population 1", value(true), "Denominator 1", value(true),
                "Denominator Exclusions 1", value(false), "Numerator 1", value(true),
                "Initial Population 2", value(true), "Denominator 2", value(true),
                "Denominator Exclusions 2", value(false), "Numerator 2", value(false)));

        assertThat(result.getStatus()).isEqualTo("pass");
        assertThat(find(result, "group-1", ValueComparison.KIND_POPULATION, "numerator").getActual()).isEqualTo("1");
        assertThat(find(result, "group-2", ValueComparison.KIND_POPULATION, "numerator").getActual()).isEqualTo("0");
    }

    // ===== ratio =====

    @Test
    void ratio_numeratorIsNotGatedByDenominator() throws Exception {
        MeasureDefinition m = measure(ScoringTypeConstants.RATIO, proportionGroup("group-1", ""));
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "denominator", 0, "numerator", 1)).build());

        TestCaseRunResult result = run(m, entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Denominator", value(false),
                "Denominator Exclusions", value(false), "Numerator", value(true)));

        assertThat(result.getStatus()).isEqualTo("pass");
    }

    // ===== continuous variable observations =====

    private static MeasureDefinition cvMeasure() {
        return measure(ScoringTypeConstants.CONTINUOUS_VARIABLE, GroupDefinition.builder()
                .groupId("group-1")
                .populations(List.of(
                        pop("initial-population", "Initial Population"),
                        pop("measure-population", "Measure Population")))
                .observations(List.of(ObservationDefinition.builder()
                        .criteriaExpression("Measure Observation 1").aggregateMethod("average")
                        .populationRef("measure-population").build()))
                .build());
    }

    @Test
    void cvObservations_compareAsAnUnorderedListWithTolerance() throws Exception {
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "measure-population", 1))
                .observations(List.of(45.0, 30.0)).build());

        TestCaseRunResult result = run(cvMeasure(), entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Measure Population", value(List.of("enc-1", "enc-2")),
                "Measure Observation Values", value(List.of(30.0000001, 45))));

        assertThat(result.getStatus()).isEqualTo("pass");
        ValueComparison obs = find(result, "group-1", ValueComparison.KIND_OBSERVATION, "values");
        assertThat(obs.getExpected()).isEqualTo("[30, 45]");
        assertThat(obs.isMatch()).isTrue();
    }

    @Test
    void cvObservations_wrongValueFails() throws Exception {
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "measure-population", 1))
                .observations(List.of(30.0, 60.0)).build());

        TestCaseRunResult result = run(cvMeasure(), entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Measure Population", value(true),
                "Measure Observation Values", value(List.of(30, 45))));

        assertThat(result.getStatus()).isEqualTo("fail");
        ValueComparison obs = find(result, "group-1", ValueComparison.KIND_OBSERVATION, "values");
        assertThat(obs.getExpected()).isEqualTo("[30, 60]");
        assertThat(obs.getActual()).isEqualTo("[30, 45]");
    }

    @Test
    void cvObservations_patientOutsideMeasurePopulationContributesNone() throws Exception {
        // Same gate as production: no effective Measure Population → no observation values,
        // even though the wrapper define returned some.
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "measure-population", 0))
                .observations(List.of()).build());

        TestCaseRunResult result = run(cvMeasure(), entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Measure Population", value(false),
                "Measure Observation Values", value(List.of(30, 45))));

        assertThat(result.getStatus()).isEqualTo("pass");
        assertThat(find(result, "group-1", ValueComparison.KIND_OBSERVATION, "values").getActual()).isEqualTo("[]");
    }

    @Test
    void observationsNotListed_areNotAsserted() throws Exception {
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "measure-population", 1)).build());

        TestCaseRunResult result = run(cvMeasure(), entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Measure Population", value(true),
                "Measure Observation Values", value(List.of(30, 45))));

        assertThat(result.getStatus()).isEqualTo("pass");
        assertThat(result.getValueComparisons())
                .noneMatch(c -> ValueComparison.KIND_OBSERVATION.equals(c.getKind()));
        // ...but the actual values are still reported, so the editor can adopt them.
        assertThat(result.getActualValues().getGroups().get(0).getObservations()).containsExactly(30.0, 45.0);
    }

    // ===== stratifiers =====

    @Test
    void stratifier_expectedStratumIsComparedCaseInsensitively() throws Exception {
        GroupDefinition group = proportionGroup("group-1", "");
        group.setStratifiers(List.of(
                StratifierDefinition.builder().stratifierId("strat-female").criteriaExpression("Stratifier 1").build(),
                StratifierDefinition.builder().stratifierId("strat-elderly").criteriaExpression("Stratifier 2").build()));
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, group);
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "denominator", 1, "numerator", 1))
                .stratifiers(Map.of("strat-female", "TRUE")).build());

        TestCaseRunResult result = run(m, entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Denominator", value(true),
                "Denominator Exclusions", value(false), "Numerator", value(true),
                "Stratifier 1", value(true), "Stratifier 2", value(false)));

        assertThat(result.getStatus()).isEqualTo("pass");
        assertThat(find(result, "group-1", ValueComparison.KIND_STRATIFIER, "strat-female").getActual()).isEqualTo("true");
        // Only the listed stratifier is asserted; the other one is reported as an actual value.
        assertThat(result.getValueComparisons())
                .noneMatch(c -> "strat-elderly".equals(c.getKey()));
        assertThat(result.getActualValues().getGroups().get(0).getStratifiers())
                .containsEntry("strat-elderly", "false");
    }

    @Test
    void stratifier_wrongStratumFails() throws Exception {
        GroupDefinition group = proportionGroup("group-1", "");
        group.setStratifiers(List.of(
                StratifierDefinition.builder().stratifierId("strat-female").criteriaExpression("Stratifier 1").build()));
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, group);
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-1")
                .populations(Map.of("initial-population", 1, "denominator", 1, "numerator", 1))
                .stratifiers(Map.of("strat-female", "true")).build());

        TestCaseRunResult result = run(m, entityWith(exp, null), Map.of(
                "Initial Population", value(true), "Denominator", value(true),
                "Denominator Exclusions", value(false), "Numerator", value(true),
                "Stratifier 1", value(false)));

        assertThat(result.getStatus()).isEqualTo("fail");
        assertThat(find(result, "group-1", ValueComparison.KIND_STRATIFIER, "strat-female").isMatch()).isFalse();
    }

    // ===== legacy compatibility =====

    @Test
    void legacyTestCase_keepsTheFlatBooleanComparison_butReportsStructuredActuals() throws Exception {
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, proportionGroup("group-1", ""));

        TestCaseRunResult result = run(m, entityWith(null, Map.of("initial-population", true, "numerator", true)),
                Map.of("Initial Population", value(true), "Denominator", value(true),
                        "Denominator Exclusions", value(true), "Numerator", value(true)));

        // Unchanged legacy semantics: raw define truthiness, union of keys.
        assertThat(result.getComparisons()).isNotEmpty();
        assertThat(result.getValueComparisons()).isNull();
        assertThat(result.getExpectedValues()).isNull();
        // New: effective values are available to adopt as a structured expectation.
        assertThat(result.getActualValues().getGroups().get(0).getPopulations())
                .containsEntry("numerator", 0).containsEntry("denominator-exclusion", 1);
    }

    @Test
    void groupRemovedFromTheMeasureAfterSaving_failsInsteadOfSilentlyPassing() throws Exception {
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, proportionGroup("group-1", ""));
        TestCaseExpectedValues exp = expected(GroupValues.builder().groupId("group-9")
                .populations(Map.of("initial-population", 1)).build());

        TestCaseRunResult result = run(m, entityWith(exp, null), Map.of("Initial Population", value(true)));

        assertThat(result.getStatus()).isEqualTo("fail");
        assertThat(result.getValueComparisons()).hasSize(1);
        assertThat(result.getValueComparisons().get(0).getActual()).contains("not in measure");
    }

    @Test
    void unreadableStoredExpectation_isAnError_notASilentLegacyComparison() throws Exception {
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, proportionGroup("group-1", ""));
        TestCaseEntity entity = entityWith(null, Map.of("initial-population", true));
        // Not the documented shape at all (e.g. hand-edited or written by an incompatible build).
        entity.setExpectedValues("{\"groups\":\"not-a-list\"}");

        TestCaseRunResult result = run(m, entity, Map.of("Initial Population", value(true)));

        // The legacy map alone would have PASSED here — reporting that would be a false assurance.
        assertThat(result.getStatus()).isEqualTo("error");
        assertThat(result.getErrorMessage()).contains("could not be read");
        assertThat(result.getComparisons()).isNull();
    }

    // ===== save-time validation =====

    @Test
    void create_rejectsAnExpectationThatCannotMatchTheMeasure() {
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, proportionGroup("group-1", ""));
        when(definitionService.getById(10L)).thenReturn(Optional.of(m));
        Map<String, Integer> populations = new LinkedHashMap<>();
        populations.put("measure-population", 1);   // not a population of this proportion group
        populations.put("numerator", -1);           // negative count
        TestCase tc = TestCase.builder().title("TC").patientBundleJson(BUNDLE)
                .expectedValues(expected(
                        GroupValues.builder().groupId("group-1").populations(populations)
                                .stratifiers(Map.of("no-such-stratifier", "true")).build(),
                        GroupValues.builder().groupId("group-7").build()))
                .build();

        assertThatThrownBy(() -> service.create(10L, tc))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getDetails())
                        .anyMatch(d -> d.contains("no population 'measure-population'"))
                        .anyMatch(d -> d.contains("count of 0 or more"))
                        .anyMatch(d -> d.contains("no stratifier 'no-such-stratifier'"))
                        .anyMatch(d -> d.contains("Unknown population group 'group-7'")));
        verify(repository, never()).save(any());
    }

    @Test
    void create_storesTheStructuredExpectation_andUpdateWithoutOneClearsIt() throws Exception {
        MeasureDefinition m = measure(ScoringTypeConstants.PROPORTION, proportionGroup("group-1", ""));
        when(definitionService.getById(10L)).thenReturn(Optional.of(m));
        TestCase tc = TestCase.builder().title("TC").patientBundleJson(BUNDLE)
                .expectedValues(expected(GroupValues.builder().groupId("group-1")
                        .populations(Map.of("initial-population", 1)).build()))
                .build();

        TestCase created = service.create(10L, tc);

        ArgumentCaptor<TestCaseEntity> saved = ArgumentCaptor.forClass(TestCaseEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExpectedValues())
                .contains("\"groupId\":\"group-1\"")
                // isEmpty() must not leak as a property — the stored JSON could not be read back.
                .doesNotContain("\"empty\"");
        assertThat(created.getExpectedValues().getGroups()).hasSize(1);

        // Switching the test case back to the legacy form stores NULL, not "{}".
        TestCaseEntity existing = entityWith(tc.getExpectedValues(), null);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        service.update(1L, TestCase.builder().title("TC").patientBundleJson(BUNDLE)
                .expectedPopulations(Map.of("initial-population", true)).build());
        assertThat(existing.getExpectedValues()).isNull();
    }
}
