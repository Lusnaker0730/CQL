package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.ScoringTypeConstants;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.model.measure.TestCaseExpectedValues;
import com.cqlplatform.model.measure.TestCaseExpectedValues.GroupValues;
import com.cqlplatform.model.measure.TestCaseRunResult;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * PAT-259 — a composite measure's test case: every component's CQL runs against the patient bundle, the
 * components become groups {@code component-<id>:<groupId>}, and {@code composite-summary} applies the
 * scoring method to this one patient. The expected-value validation knows those groups.
 */
@ExtendWith(MockitoExtension.class)
class CompositeTestCaseTest {

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
        StratifierEvaluator stratifierEvaluator = new StratifierEvaluator(populationEvaluator, new MeasureScoreCalculator());
        service = new TestCaseService(repository, definitionService, cqlExecutionService, dateShiftService,
                FhirContext.forR4Cached(), populationEvaluator, stratifierEvaluator,
                org.mockito.Mockito.mock(TestCaseValidationService.class));
        lenient().when(repository.save(any(TestCaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static PopulationDefinition pop(String type, String expression) {
        return PopulationDefinition.builder().populationType(type).criteriaExpression(expression).build();
    }

    private static GroupDefinition proportionGroup() {
        return GroupDefinition.builder().groupId("group-1").populations(List.of(
                pop("initial-population", "Initial Population"), pop("denominator", "Denominator"),
                pop("denominator-exclusion", "Denominator Exclusions"), pop("numerator", "Numerator"))).build();
    }

    private static MeasureDefinition component(long id, String name) {
        return MeasureDefinition.builder().id(id).name(name).status("active").scoringType(ScoringTypeConstants.PROPORTION)
                .cqlContent("library " + name + " version '1.0'").groupDefinitions(List.of(proportionGroup())).build();
    }

    private static MeasureDefinition composite(String method, Map<String, Double> weights) {
        return MeasureDefinition.builder().id(10L).name("Composite").status("active")
                .scoringType(ScoringTypeConstants.COMPOSITE).compositeScoring(method)
                .componentMeasureIds(List.of(11L, 12L)).componentWeights(weights).ownerUsername("user").build();
    }

    private static CqlExecutionResponse.ExpressionResult value(Object v) {
        return CqlExecutionResponse.ExpressionResult.builder().value(v).build();
    }

    private static CqlExecutionResponse response(boolean numerator) {
        return CqlExecutionResponse.builder().success(true).results(Map.of(
                "Initial Population", value(true), "Denominator", value(true),
                "Denominator Exclusions", value(false), "Numerator", value(numerator))).build();
    }

    private TestCaseEntity entityWith(TestCaseExpectedValues expected) throws Exception {
        return TestCaseEntity.builder()
                .id(1L).measureDefinitionId(10L).title("TC").patientBundleJson(BUNDLE)
                .expectedPopulationMap(new LinkedHashMap<>())
                .expectedValues(expected != null ? MAPPER.writeValueAsString(expected) : null)
                .status("pending").sortOrder(0).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    /** Component A meets its numerator, component B does not; both have the patient in their denominators. */
    private void wire(MeasureDefinition composite, TestCaseEntity entity) {
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(definitionService.getById(10L)).thenReturn(Optional.of(composite));
        when(definitionService.getById(11L)).thenReturn(Optional.of(component(11L, "A")));
        when(definitionService.getById(12L)).thenReturn(Optional.of(component(12L, "B")));
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenAnswer(inv -> response(((CqlExecutionRequest) inv.getArgument(0)).getCql().startsWith("library A")));
    }

    private static GroupValues group(String id, Map<String, Integer> populations) {
        return GroupValues.builder().groupId(id).populations(populations).build();
    }

    private static Map<String, Integer> actualPopulations(TestCaseRunResult result, String groupId) {
        return result.getActualValues().getGroups().stream().filter(g -> groupId.equals(g.getGroupId()))
                .findFirst().orElseThrow().getPopulations();
    }

    @Test
    void allOrNothing_componentsAreGroups_andTheSummaryNeedsEveryEligibleNumerator() throws Exception {
        TestCaseExpectedValues expected = TestCaseExpectedValues.builder().groups(List.of(
                group("component-11:group-1", Map.of("initial-population", 1, "denominator", 1, "numerator", 1)),
                group("component-12:group-1", Map.of("initial-population", 1, "denominator", 1, "numerator", 0)),
                group("composite-summary", Map.of("initial-population", 1, "denominator", 1, "numerator", 0)))).build();
        wire(composite("all-or-nothing", null), entityWith(expected));

        TestCaseRunResult result = service.runTestCase(1L);

        assertThat(result.getStatus()).isEqualTo("pass");
        assertThat(result.getActualValues().getGroups()).extracting(GroupValues::getGroupId)
                .containsExactly("composite-summary", "component-11:group-1", "component-12:group-1");
        assertThat(actualPopulations(result, "composite-summary"))
                .containsEntry("initial-population", 1).containsEntry("denominator", 1).containsEntry("numerator", 0);
        assertThat(actualPopulations(result, "component-11:group-1")).containsEntry("numerator", 1);
        // both components ran against the same bundle
        verify(cqlExecutionService, org.mockito.Mockito.times(2)).executeWithProvider(any(), any());
    }

    @Test
    void opportunity_sumsTheComponentMemberships() throws Exception {
        wire(composite("opportunity", null), entityWith(TestCaseExpectedValues.builder().groups(List.of(
                group("composite-summary", Map.of("initial-population", 2, "denominator", 2, "numerator", 1)))).build()));

        TestCaseRunResult result = service.runTestCase(1L);

        assertThat(result.getStatus()).isEqualTo("pass");
        assertThat(actualPopulations(result, "composite-summary")).containsEntry("numerator", 1).containsEntry("denominator", 2);
    }

    @Test
    void linearAndWeighted_reportThePatientsShareAsAnObservation() throws Exception {
        wire(composite("linear", null), entityWith(null));
        TestCaseRunResult linear = service.runTestCase(1L);
        GroupValues summary = linear.getActualValues().getGroups().get(0);
        assertThat(summary.getPopulations()).containsEntry("denominator", 1).doesNotContainKey("numerator");
        assertThat(summary.getObservations()).containsExactly(0.5);          // A met, B not: 1 of 2

        wire(composite("weighted", Map.of("11", 3.0, "12", 1.0)), entityWith(null));
        TestCaseRunResult weighted = service.runTestCase(1L);
        assertThat(weighted.getActualValues().getGroups().get(0).getObservations()).containsExactly(0.75);   // 3 / (3 + 1)
        // no structured expectation and no legacy map → informational pass
        assertThat(weighted.getStatus()).isEqualTo("pass");
    }

    @Test
    void create_validatesTheExpectationAgainstTheComponentGroupsAndTheSummary() {
        when(definitionService.getById(10L)).thenReturn(Optional.of(composite("all-or-nothing", null)));
        when(definitionService.getById(11L)).thenReturn(Optional.of(component(11L, "A")));
        when(definitionService.getById(12L)).thenReturn(Optional.of(component(12L, "B")));

        TestCase ok = TestCase.builder().title("TC").patientBundleJson(BUNDLE)
                .expectedValues(TestCaseExpectedValues.builder().groups(List.of(
                        group("component-11:group-1", Map.of("numerator", 1)),
                        group("composite-summary", Map.of("numerator", 0)))).build()).build();
        assertThat(service.create(10L, ok).getExpectedValues().getGroups()).hasSize(2);

        TestCase wrong = TestCase.builder().title("TC").patientBundleJson(BUNDLE)
                .expectedValues(TestCaseExpectedValues.builder().groups(List.of(
                        group("group-1", Map.of("numerator", 1)),                       // not a composite group
                        group("composite-summary", Map.of("measure-population", 1)))).build()).build();
        assertThatThrownBy(() -> service.create(10L, wrong))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getDetails())
                        .anyMatch(d -> d.contains("Unknown population group 'group-1'"))
                        .anyMatch(d -> d.contains("no population 'measure-population'")));
    }

    @Test
    void aComponentThatFailsToExecute_isAnErrorNamingTheComponent() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(entityWith(null)));
        when(definitionService.getById(10L)).thenReturn(Optional.of(composite("opportunity", null)));
        when(definitionService.getById(11L)).thenReturn(Optional.of(component(11L, "A")));
        when(definitionService.getById(12L)).thenReturn(Optional.of(component(12L, "B")));
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenAnswer(inv -> ((CqlExecutionRequest) inv.getArgument(0)).getCql().startsWith("library B")
                        ? CqlExecutionResponse.builder().success(false).errors(List.of("boom")).build()
                        : response(true));

        TestCaseRunResult result = service.runTestCase(1L);

        assertThat(result.getStatus()).isEqualTo("error");
        assertThat(result.getErrorMessage()).contains("B").contains("boom");
        verify(repository, never()).delete(any());
    }
}
