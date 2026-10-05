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

    @Mock
    private TestCaseValidationService validationService;

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

    // PAT-242 — a test case runs in the measure's own Measurement Period when it has one, and the
    // run result says which period was used; a measure without one runs in the current calendar
    // year, exactly as before.
    @Test
    void runTestCase_usesTheMeasuresMeasurementPeriod_andReportsIt() {
        TestCaseEntity entity = createEntity(1L, 10L, "MP TC");
        entity.setExpectedPopulationMap(Map.of("InPopulation", true));
        MeasureDefinition measure = createMeasure(10L);
        measure.setMeasurementPeriodStart(java.time.LocalDate.of(2024, 1, 1));
        measure.setMeasurementPeriodEnd(java.time.LocalDate.of(2024, 12, 31));
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(definitionService.getById(10L)).thenReturn(Optional.of(measure));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.ArgumentCaptor<CqlExecutionRequest> sent = org.mockito.ArgumentCaptor.forClass(CqlExecutionRequest.class);
        when(cqlExecutionService.executeWithProvider(sent.capture(), any(PrefetchRetrieveProvider.class)))
                .thenReturn(CqlExecutionResponse.builder().success(true)
                        .results(Map.of("InPopulation", CqlExecutionResponse.ExpressionResult.builder()
                                .name("InPopulation").value(true).valueType("Boolean").displayValue("true").build()))
                        .build());

        TestCaseRunResult result = service.runTestCase(1L);

        assertThat(result.getMeasurementPeriodStart()).isEqualTo(java.time.LocalDate.of(2024, 1, 1));
        assertThat(result.getMeasurementPeriodEnd()).isEqualTo(java.time.LocalDate.of(2024, 12, 31));
        org.opencds.cqf.cql.engine.runtime.Interval period =
                (org.opencds.cqf.cql.engine.runtime.Interval) sent.getValue().getParameters().get("Measurement Period");
        assertThat(((org.opencds.cqf.cql.engine.runtime.DateTime) period.getStart()).getDateTime().getYear()).isEqualTo(2024);
        assertThat(((org.opencds.cqf.cql.engine.runtime.DateTime) period.getEnd()).getDateTime().getMonthValue()).isEqualTo(12);
    }

    @Test
    void measurementPeriod_fallsBackToTheCurrentCalendarYear() {
        java.time.LocalDate[] period = TestCaseService.measurementPeriod(createMeasure(10L));

        int year = java.time.Year.now().getValue();
        assertThat(period[0]).isEqualTo(java.time.LocalDate.of(year, 1, 1));
        assertThat(period[1]).isEqualTo(java.time.LocalDate.of(year, 12, 31));
    }

    // ===== PAT-245 — FHIR validation of the patient bundle =====

    @Test
    void create_marksTheBundlePending_andSchedulesValidation() {
        when(definitionService.getById(10L)).thenReturn(Optional.of(createMeasure(10L)));
        when(repository.save(any())).thenAnswer(inv -> { TestCaseEntity e = inv.getArgument(0); e.setId(1L); return e; });

        service.create(10L, TestCase.builder().title("New TC").patientBundleJson("{}").build());

        org.mockito.ArgumentCaptor<TestCaseEntity> saved = org.mockito.ArgumentCaptor.forClass(TestCaseEntity.class);
        verify(validationService).markPending(saved.capture());
        verify(validationService).scheduleValidation(1L);
    }

    @Test
    void update_revalidatesOnlyWhenTheBundleChanged_orWasNeverValidated() {
        TestCaseEntity entity = createEntity(1L, 10L, "TC");
        entity.setValidationStatus("valid");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // same bundle, already validated → untouched
        service.update(1L, TestCase.builder().title("TC").patientBundleJson(entity.getPatientBundleJson()).build());
        verify(validationService, never()).markPending(any());
        verify(validationService, never()).scheduleValidation(any());

        // a changed bundle → pending + scheduled
        service.update(1L, TestCase.builder().title("TC").patientBundleJson("{\"resourceType\":\"Bundle\",\"entry\":[]}").build());
        verify(validationService).markPending(entity);
        verify(validationService).scheduleValidation(1L);
    }

    @Test
    void runAllTestCases_skipInvalid_leavesOutInvalidCases_butRunsPendingAndUnvalidatedOnes() {
        TestCaseEntity valid = createEntity(1L, 10L, "valid");
        valid.setValidationStatus("valid");
        TestCaseEntity invalid = createEntity(2L, 10L, "invalid");
        invalid.setValidationStatus("invalid");
        TestCaseEntity pending = createEntity(3L, 10L, "pending");
        pending.setValidationStatus("pending");
        TestCaseEntity never = createEntity(4L, 10L, "never");
        when(definitionService.getById(10L)).thenReturn(Optional.of(createMeasure(10L)));
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(valid, invalid, pending, never));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cqlExecutionService.executeWithProvider(any(CqlExecutionRequest.class), any(PrefetchRetrieveProvider.class)))
                .thenReturn(CqlExecutionResponse.builder().success(true)
                        .results(Map.of("InPopulation", CqlExecutionResponse.ExpressionResult.builder()
                                .name("InPopulation").value(true).valueType("Boolean").build()))
                        .build());

        List<TestCaseRunResult> results = service.runAllTestCases(10L, false, true);

        assertThat(results).extracting(TestCaseRunResult::getTestCaseTitle).containsExactly("valid", "pending", "never");
        assertThat(service.runAllTestCases(10L, false, false)).hasSize(4);
    }

    // ===== PAT-246 — copy to another measure =====

    @Test
    void copyTo_copiesTheChosenTestCases_andDropsAnExpectationThatDoesNotFitTheTarget() {
        MeasureDefinition target = createMeasure(20L);
        target.setGroupDefinitions(List.of(GroupDefinition.builder().groupId("group-1")
                .populations(List.of(PopulationDefinition.builder().populationType("initial-population").criteriaExpression("IP").build()))
                .build()));
        when(definitionService.getById(20L)).thenReturn(Optional.of(target));
        TestCaseEntity fits = createEntity(1L, 10L, "fits");
        fits.setExpectedValues("{\"groups\":[{\"groupId\":\"group-1\",\"populations\":{\"initial-population\":1}}]}");
        TestCaseEntity wrongGroup = createEntity(2L, 10L, "wrong group");
        wrongGroup.setExpectedValues("{\"groups\":[{\"groupId\":\"group-9\",\"populations\":{\"initial-population\":1}}]}");
        TestCaseEntity notChosen = createEntity(3L, 10L, "not chosen");
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(fits, wrongGroup, notChosen));
        when(repository.save(any())).thenAnswer(inv -> { TestCaseEntity e = inv.getArgument(0); e.setId(100L + e.getTitle().length()); return e; });

        TestCaseCopyResult result = service.copyTo(10L, 20L, List.of(1L, 2L));

        assertThat(result.getCopied()).extracting(TestCase::getTitle).containsExactly("fits", "wrong group");
        assertThat(result.getCopied()).allSatisfy(tc -> {
            assertThat(tc.getMeasureDefinitionId()).isEqualTo(20L);
            assertThat(tc.getStatus()).isEqualTo("pending");
        });
        assertThat(result.getCopied().get(0).getExpectedValues()).isNotNull();
        assertThat(result.getCopied().get(1).getExpectedValues()).isNull();
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings().get(0)).contains("wrong group").contains("group-9");
    }

    @Test
    void copyTo_sameMeasure_isRefused() {
        assertThatThrownBy(() -> service.copyTo(10L, 10L, null))
                .isInstanceOf(com.cqlplatform.exception.ValidationException.class);
    }
}
