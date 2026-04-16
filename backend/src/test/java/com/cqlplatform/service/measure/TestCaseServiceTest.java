package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.cql.CqlExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock
    private TestCaseRepository repository;

    @Mock
    private MeasureDefinitionService definitionService;

    @Mock
    private CqlExecutionService cqlExecutionService;

    @Mock
    private DateShiftService dateShiftService;

    @Mock
    private PopulationEvaluator populationEvaluator;

    // Shared across test methods — forR4() is expensive (~300ms) and stateless for parsing.
    private static final FhirContext SHARED_FHIR_CTX = FhirContext.forR4();

    @org.mockito.Spy
    private FhirContext fhirContext = SHARED_FHIR_CTX;

    @InjectMocks
    private TestCaseService service;

    // ===== Helper methods =====

    private TestCaseEntity createEntity(Long id, Long measureId, String title) {
        return TestCaseEntity.builder()
                .id(id)
                .measureDefinitionId(measureId)
                .title(title)
                .description("Test description")
                .patientBundleJson("{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"test-1\"}}]}")
                .expectedPopulationMap(Map.of("initial-population", true, "numerator", false))
                .status("pending")
                .sortOrder(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private MeasureDefinition createMeasure(Long id) {
        return MeasureDefinition.builder()
                .id(id)
                .name("Test Measure")
                .cqlContent("library TestMeasure version '1.0'\ndefine InPopulation: true")
                .ownerUsername("user")
                .build();
    }

    // ===== getTestCasesForMeasure =====

    @Test
    void getTestCasesForMeasure_shouldReturnSortedList() {
        TestCaseEntity e1 = createEntity(1L, 10L, "TC1");
        TestCaseEntity e2 = createEntity(2L, 10L, "TC2");
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(e1, e2));

        List<TestCase> result = service.getTestCasesForMeasure(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("TC1");
        assertThat(result.get(1).getTitle()).isEqualTo("TC2");
    }

    // ===== getById =====

    @Test
    void getById_found_shouldReturnTestCase() {
        TestCaseEntity entity = createEntity(1L, 10L, "TC1");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        Optional<TestCase> result = service.getById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("TC1");
    }

    @Test
    void getById_notFound_shouldReturnEmpty() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        Optional<TestCase> result = service.getById(999L);

        assertThat(result).isEmpty();
    }

    // ===== create =====

    @Test
    void create_shouldSaveAndReturnDto() {
        when(definitionService.getById(10L)).thenReturn(Optional.of(createMeasure(10L)));
        when(repository.save(any())).thenAnswer(inv -> {
            TestCaseEntity e = inv.getArgument(0);
            e.setId(1L);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            return e;
        });

        TestCase input = TestCase.builder()
                .title("New TC")
                .description("Desc")
                .patientBundleJson("{}")
                .build();

        TestCase result = service.create(10L, input);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("New TC");
        assertThat(result.getMeasureDefinitionId()).isEqualTo(10L);
        verify(repository).save(any(TestCaseEntity.class));
    }

    @Test
    void create_measureNotFound_shouldThrow() {
        when(definitionService.getById(999L)).thenReturn(Optional.empty());

        TestCase input = TestCase.builder().title("TC").build();

        assertThatThrownBy(() -> service.create(999L, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Measure not found: 999");
    }

    // ===== update =====

    @Test
    void update_shouldMapFieldsCorrectly() {
        TestCaseEntity existing = createEntity(1L, 10L, "Old Title");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestCase update = TestCase.builder()
                .title("New Title")
                .description("New Desc")
                .patientBundleJson("{\"new\":true}")
                .expectedPopulations(Map.of("denominator", true))
                .series("Series A")
                .sortOrder(5)
                .build();

        TestCase result = service.update(1L, update);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getDescription()).isEqualTo("New Desc");
        assertThat(result.getSeries()).isEqualTo("Series A");
    }

    // ===== delete =====

    @Test
    void delete_shouldCallRepository() {
        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    // ===== batchImport =====

    @Test
    void batchImport_shouldImportSuccessfully() {
        when(definitionService.getById(10L)).thenReturn(Optional.of(createMeasure(10L)));
        when(repository.save(any())).thenAnswer(inv -> {
            TestCaseEntity e = inv.getArgument(0);
            e.setId(1L);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            return e;
        });

        TestCase tc = TestCase.builder().title("Imported TC").build();

        BatchTestCaseImportResult result = service.batchImport(10L, List.of(tc), 0);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getTotalReceived()).isEqualTo(1);
    }

    @Test
    void batchImport_withDateShift_shouldCallDateShiftService() {
        when(definitionService.getById(10L)).thenReturn(Optional.of(createMeasure(10L)));
        when(dateShiftService.shiftDates(anyString(), eq(30))).thenReturn("{\"shifted\":true}");
        when(repository.save(any())).thenAnswer(inv -> {
            TestCaseEntity e = inv.getArgument(0);
            e.setId(1L);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            return e;
        });

        TestCase tc = TestCase.builder()
                .title("TC with dates")
                .patientBundleJson("{\"old\":true}")
                .build();

        service.batchImport(10L, List.of(tc), 30);

        verify(dateShiftService).shiftDates("{\"old\":true}", 30);
    }

    // ===== runTestCase =====

    @Test
    void runTestCase_pass_shouldReturnPassStatus() {
        TestCaseEntity entity = createEntity(1L, 10L, "Pass TC");
        entity.setExpectedPopulationMap(Map.of("InPopulation", true));
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(definitionService.getById(10L)).thenReturn(Optional.of(createMeasure(10L)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(Map.of("InPopulation", CqlExecutionResponse.ExpressionResult.builder()
                        .name("InPopulation")
                        .value(true)
                        .valueType("Boolean")
                        .displayValue("true")
                        .build()))
                .build();
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(execResponse);

        TestCaseRunResult result = service.runTestCase(1L);

        assertThat(result.getStatus()).isEqualTo("pass");
        assertThat(result.getTestCaseId()).isEqualTo(1L);
    }

    @Test
    void runTestCase_fail_shouldReturnFailStatus() {
        TestCaseEntity entity = createEntity(1L, 10L, "Fail TC");
        entity.setExpectedPopulationMap(Map.of("InPopulation", false));
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(definitionService.getById(10L)).thenReturn(Optional.of(createMeasure(10L)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(Map.of("InPopulation", CqlExecutionResponse.ExpressionResult.builder()
                        .name("InPopulation")
                        .value(true) // actual is true, expected is false → fail
                        .valueType("Boolean")
                        .displayValue("true")
                        .build()))
                .build();
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(execResponse);

        TestCaseRunResult result = service.runTestCase(1L);

        assertThat(result.getStatus()).isEqualTo("fail");
    }

    @Test
    void runTestCase_noCql_shouldReturnError() {
        TestCaseEntity entity = createEntity(1L, 10L, "No CQL TC");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        MeasureDefinition measure = MeasureDefinition.builder()
                .id(10L).name("Empty Measure").cqlContent(null).ownerUsername("user").build();
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestCaseRunResult result = service.runTestCase(1L);

        assertThat(result.getStatus()).isEqualTo("error");
        assertThat(result.getErrorMessage()).contains("no CQL content");
    }

    // ===== runAllTestCases =====

    @Test
    void runAllTestCases_shouldRunAllAndReturnResults() {
        when(definitionService.getById(10L)).thenReturn(Optional.of(createMeasure(10L)));
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(createEntity(1L, 10L, "TC1"), createEntity(2L, 10L, "TC2")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(Map.of("InPopulation", CqlExecutionResponse.ExpressionResult.builder()
                        .name("InPopulation").value(true).valueType("Boolean").displayValue("true").build()))
                .build();
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(execResponse);

        List<TestCaseRunResult> results = service.runAllTestCases(10L);

        assertThat(results).hasSize(2);
        verify(repository, times(2)).save(any(TestCaseEntity.class));
    }

    // ===== extractPatientIdFromBundle =====

    @Test
    void extractPatientIdFromBundle_validBundle_shouldReturnId() throws Exception {
        String bundle = "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"patient-123\"}}]}";

        // Use reflection to test private method
        String result = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "extractPatientIdFromBundle", bundle);

        assertThat(result).isEqualTo("patient-123");
    }

    @Test
    void extractPatientIdFromBundle_nullBundle_shouldReturnDefault() throws Exception {
        String result = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "extractPatientIdFromBundle", (String) null);

        assertThat(result).isEqualTo("test-patient");
    }

    @Test
    void extractPatientIdFromBundle_emptyBundle_shouldReturnDefault() throws Exception {
        String result = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "extractPatientIdFromBundle", "  ");

        assertThat(result).isEqualTo("test-patient");
    }

    // ===== buildComparisons =====

    @Test
    void buildComparisons_matching_shouldHaveMatchTrue() throws Exception {
        Map<String, Boolean> expected = Map.of("ip", true, "denom", false);
        Map<String, Boolean> actual = Map.of("ip", true, "denom", false);

        @SuppressWarnings("unchecked")
        List<TestCaseRunResult.PopulationComparison> comparisons =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        service, "buildComparisons", expected, actual);

        assertThat(comparisons).isNotNull();
        assertThat(comparisons).allMatch(TestCaseRunResult.PopulationComparison::isMatch);
    }

    @Test
    void buildComparisons_mismatching_shouldHaveMatchFalse() throws Exception {
        Map<String, Boolean> expected = Map.of("ip", true);
        Map<String, Boolean> actual = Map.of("ip", false);

        @SuppressWarnings("unchecked")
        List<TestCaseRunResult.PopulationComparison> comparisons =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        service, "buildComparisons", expected, actual);

        assertThat(comparisons).isNotNull();
        assertThat(comparisons.get(0).isMatch()).isFalse();
    }
}
