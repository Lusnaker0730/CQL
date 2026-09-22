package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.ClauseCoverage;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureClauseCoverage;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.TestCaseRunResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-232 — how test-case runs ask for clause coverage and what they do with it. The engine side
 * is locked by {@code ClauseCoverageCollectorTest}; here the execution service is mocked and
 * returns canned coverage.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseClauseCoverageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BUNDLE =
            "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"p1\"}}]}";
    private static final String CQL = "library M version '1.0'\ndefine \"Initial Population\": true";

    @Mock private TestCaseRepository repository;
    @Mock private MeasureDefinitionService definitionService;
    @Mock private CqlExecutionService cqlExecutionService;
    @Mock private DateShiftService dateShiftService;

    private TestCaseService service;

    @BeforeEach
    void setUp() {
        PopulationEvaluator populationEvaluator = new PopulationEvaluator();
        service = new TestCaseService(repository, definitionService, cqlExecutionService, dateShiftService,
                FhirContext.forR4Cached(), populationEvaluator,
                new StratifierEvaluator(populationEvaluator, new MeasureScoreCalculator()));
        lenient().when(repository.save(any(TestCaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(definitionService.getById(10L)).thenReturn(Optional.of(MeasureDefinition.builder()
                .id(10L).name("M").scoringType("cohort").cqlContent(CQL).ownerUsername("user")
                .groupDefinitions(List.of(GroupDefinition.builder().groupId("g").populations(List.of(
                        PopulationDefinition.builder().populationType("initial-population")
                                .criteriaExpression("Initial Population").build())).build()))
                .build()));
    }

    private TestCaseEntity entity(long id, boolean expectIp) {
        return TestCaseEntity.builder().id(id).measureDefinitionId(10L).title("TC" + id)
                .patientBundleJson(BUNDLE)
                .expectedPopulationMap(new LinkedHashMap<>(Map.of("initial-population", expectIp)))
                .status("pending").sortOrder(0).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    /** Coverage of the one-clause library: the literal `true` hit {@code hits} times. */
    private static ClauseCoverage coverage(int hits) {
        ClauseCoverage.Clause literal = ClauseCoverage.Clause.builder().localId("3").locator("2:31-2:34")
                .type("Literal").hits(hits).value(hits > 0 ? "true" : null).build();
        return ClauseCoverage.builder().cql(CQL).libraryName("M").totalClauses(1).coveredClauses(hits > 0 ? 1 : 0)
                .percent(hits > 0 ? 100.0 : 0.0)
                .statements(List.of(ClauseCoverage.Statement.builder().name("Initial Population").locator("2:1-2:34")
                        .totalClauses(1).coveredClauses(hits > 0 ? 1 : 0).clauses(List.of(literal)).build()))
                .build();
    }

    private static CqlExecutionResponse response(ClauseCoverage coverage) {
        return CqlExecutionResponse.builder().success(true)
                .results(Map.of("Initial Population", CqlExecutionResponse.ExpressionResult.builder().value(true).build()))
                .clauseCoverage(coverage).build();
    }

    @Test
    void debugRun_asksTheEngineForClauseCoverage_andReturnsIt() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, true)));
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(response(coverage(1)));

        TestCaseRunResult result = service.runTestCase(1L, true);

        ArgumentCaptor<CqlExecutionRequest> request = ArgumentCaptor.forClass(CqlExecutionRequest.class);
        verify(cqlExecutionService).executeWithProvider(request.capture(), any(PrefetchRetrieveProvider.class));
        assertThat(request.getValue().isClauseCoverage()).isTrue();
        assertThat(result.getClauseCoverage()).isNotNull();
        assertThat(result.getClauseCoverage().getPercent()).isEqualTo(100.0);
        assertThat(result.getStatus()).isEqualTo("pass");
    }

    @Test
    void normalRun_doesNotAskForCoverage_andThePersistedResultNeverCarriesIt() throws Exception {
        TestCaseEntity entity = entity(1L, true);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(response(coverage(1)));

        TestCaseRunResult result = service.runTestCase(1L, false);

        ArgumentCaptor<CqlExecutionRequest> request = ArgumentCaptor.forClass(CqlExecutionRequest.class);
        verify(cqlExecutionService).executeWithProvider(request.capture(), any(PrefetchRetrieveProvider.class));
        assertThat(request.getValue().isClauseCoverage()).isFalse();
        assertThat(result.getClauseCoverage()).isNull();
        // Even if the engine had returned some, the stored last-run JSON must stay small.
        assertThat(MAPPER.readTree(entity.getLastRunResultJson()).has("clauseCoverage")).isFalse();
    }

    @Test
    void measureCoverage_runsEveryTestCase_withoutDebug_andMergesTheUnion() {
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(entity(1L, true), entity(2L, false), entity(3L, true)));
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(response(coverage(1)), response(coverage(0)), response(coverage(1)));

        MeasureClauseCoverage summary = service.measureClauseCoverage(10L);

        ArgumentCaptor<CqlExecutionRequest> request = ArgumentCaptor.forClass(CqlExecutionRequest.class);
        verify(cqlExecutionService, org.mockito.Mockito.times(3))
                .executeWithProvider(request.capture(), any(PrefetchRetrieveProvider.class));
        assertThat(request.getAllValues()).allSatisfy(r -> {
            assertThat(r.isClauseCoverage()).isTrue();
            assertThat(r.isDebugMode()).isFalse(); // coverage, not traces
        });
        assertThat(summary.getTestCases()).isEqualTo(3);
        assertThat(summary.getExecuted()).isEqualTo(3);
        assertThat(summary.getPassed()).isEqualTo(2); // TC2 expected the patient OUT of the IP
        assertThat(summary.getCoverage().getCoveredClauses()).isEqualTo(1);
        assertThat(summary.getCoverage().getPercent()).isEqualTo(100.0);
        assertThat(summary.getCoverage().getStatements().get(0).getClauses().get(0).getHits()).isEqualTo(2);
        // Outcomes were persisted like a normal run-all.
        verify(repository, org.mockito.Mockito.times(3)).save(any(TestCaseEntity.class));
    }

    @Test
    void measureCoverage_withoutTestCases_orWithoutAnyExecutableOne_hasNoCoverage() {
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());
        assertThat(service.measureClauseCoverage(10L).getCoverage()).isNull();

        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(entity(1L, true)));
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(CqlExecutionResponse.builder().success(false).errors(List.of("boom")).build());
        MeasureClauseCoverage summary = service.measureClauseCoverage(10L);
        assertThat(summary.getExecuted()).isZero();
        assertThat(summary.getCoverage()).isNull();
    }
}
