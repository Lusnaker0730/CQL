package com.cqlplatform.service.measure;

import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.model.measure.TestCaseValidation;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.service.fhir.FhirValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PAT-245 — a test case's patient bundle is validated with the FHIR validator; the outcome is a
 * small summary (status, counts, capped error issues) stored on the test case.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseValidationServiceTest {

    @Mock private TestCaseRepository repository;
    @Mock private FhirValidationService fhirValidationService;

    /** Runs submitted tasks on the calling thread, so tests see the outcome synchronously. */
    private static final ExecutorService DIRECT = new AbstractExecutorService() {
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    };

    private TestCaseValidationService service() {
        return new TestCaseValidationService(repository, fhirValidationService, DIRECT);
    }

    private static FhirValidationService.ValidationIssue issue(String severity, String message) {
        return new FhirValidationService.ValidationIssue(severity, "Patient", message);
    }

    private static FhirValidationService.ResourceValidationEntry entry(String type, String id, boolean valid,
                                                                       FhirValidationService.ValidationIssue... issues) {
        return new FhirValidationService.ResourceValidationEntry(type, id, null, valid, List.of(issues));
    }

    @Test
    void errorsMakeTheBundleInvalid_warningsAreOnlyCounted_andIssuesCarryTheResource() {
        when(fhirValidationService.validateBundle(anyString())).thenReturn(new FhirValidationService.BundleValidationResult(
                2, 1, 1, List.of(
                        entry("Patient", "p1", false, issue("error", "Patient.identifier: minimum required = 1"), issue("warning", "no profile")),
                        entry("Encounter", "e1", true, issue("information", "ok")))));

        TestCaseValidation v = service().validate("{\"resourceType\":\"Bundle\"}");

        assertThat(v.getStatus()).isEqualTo(TestCaseValidation.INVALID);
        assertThat(v.getTotalResources()).isEqualTo(2);
        assertThat(v.getInvalidResources()).isEqualTo(1);
        assertThat(v.getErrorCount()).isEqualTo(1);
        assertThat(v.getWarningCount()).isEqualTo(1);
        assertThat(v.getIssues()).hasSize(1);
        assertThat(v.getIssues().get(0).getResourceType()).isEqualTo("Patient");
        assertThat(v.getIssues().get(0).getResourceId()).isEqualTo("p1");
        assertThat(v.getIssues().get(0).getMessage()).contains("identifier");
        assertThat(v.getValidatedAt()).isNotNull();
    }

    @Test
    void noErrors_isValid_andTheIssueListIsCapped() {
        when(fhirValidationService.validateBundle(anyString())).thenReturn(new FhirValidationService.BundleValidationResult(
                1, 1, 0, List.of(entry("Patient", "p1", true, issue("warning", "w")))));
        assertThat(service().validate("{}").getStatus()).isEqualTo(TestCaseValidation.VALID);

        FhirValidationService.ValidationIssue[] many = new FhirValidationService.ValidationIssue[TestCaseValidation.MAX_ISSUES + 20];
        for (int i = 0; i < many.length; i++) many[i] = issue("error", "e" + i);
        when(fhirValidationService.validateBundle(anyString())).thenReturn(new FhirValidationService.BundleValidationResult(
                1, 0, 1, List.of(entry("Patient", "p1", false, many))));
        TestCaseValidation v = service().validate("{}");
        assertThat(v.getErrorCount()).isEqualTo(many.length);
        assertThat(v.getIssues()).hasSize(TestCaseValidation.MAX_ISSUES);
    }

    @Test
    void emptyBundle_unparseableBundle_andValidatorFailure_areReportedNotSwallowed() {
        assertThat(service().validate("  ").getStatus()).isEqualTo(TestCaseValidation.INVALID);
        assertThat(service().validate(null).getMessage()).contains("no patient bundle");

        // validateBundle turns a parse failure into an empty result
        when(fhirValidationService.validateBundle("not json")).thenReturn(new FhirValidationService.BundleValidationResult(0, 0, 0, List.of()));
        TestCaseValidation empty = service().validate("not json");
        assertThat(empty.getStatus()).isEqualTo(TestCaseValidation.INVALID);
        assertThat(empty.getMessage()).contains("could not be parsed");

        when(fhirValidationService.validateBundle("boom")).thenThrow(new IllegalStateException("validator down"));
        TestCaseValidation error = service().validate("boom");
        assertThat(error.getStatus()).isEqualTo(TestCaseValidation.ERROR);
        assertThat(error.getMessage()).contains("validator down");
    }

    @Test
    void validateNow_storesStatusSummaryAndTimestamp_andScheduleRunsOnTheExecutor() {
        TestCaseEntity entity = TestCaseEntity.builder().id(7L).measureDefinitionId(1L).title("t")
                .patientBundleJson("{\"resourceType\":\"Bundle\"}").build();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fhirValidationService.validateBundle(anyString())).thenReturn(new FhirValidationService.BundleValidationResult(
                1, 0, 1, List.of(entry("Patient", "p1", false, issue("error", "bad")))));

        TestCaseValidationService service = service();
        service.markPending(entity);
        assertThat(entity.getValidationStatus()).isEqualTo(TestCaseValidation.PENDING);

        service.scheduleValidation(7L); // no transaction → runs at once on the direct executor

        assertThat(entity.getValidationStatus()).isEqualTo(TestCaseValidation.INVALID);
        assertThat(entity.getValidatedAt()).isNotNull();
        TestCaseValidation stored = TestCaseValidationService.read(entity.getValidationSummary());
        assertThat(stored.getIssues()).extracting(TestCaseValidation.Issue::getMessage).containsExactly("bad");
        assertThat(TestCaseValidationService.isInvalid(entity)).isTrue();
        assertThat(TestCaseValidationService.summaryFor(entity).getErrorCount()).isEqualTo(1);
        verify(repository).save(entity);
    }

    @Test
    void summaryFor_aNeverValidatedTestCase_isNull_andAPendingOneCarriesJustTheStatus() {
        TestCaseEntity never = TestCaseEntity.builder().id(1L).build();
        assertThat(TestCaseValidationService.summaryFor(never)).isNull();
        TestCaseEntity pending = TestCaseEntity.builder().id(2L).validationStatus(TestCaseValidation.PENDING).build();
        assertThat(TestCaseValidationService.summaryFor(pending).getStatus()).isEqualTo(TestCaseValidation.PENDING);
        assertThat(TestCaseValidationService.isInvalid(pending)).isFalse();
    }

    @Test
    void scheduleAll_marksEveryTestCasePendingAndValidatesEach() {
        TestCaseEntity a = TestCaseEntity.builder().id(1L).measureDefinitionId(5L).patientBundleJson("{}").build();
        TestCaseEntity b = TestCaseEntity.builder().id(2L).measureDefinitionId(5L).patientBundleJson("{}").build();
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(a, b));
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.findById(2L)).thenReturn(Optional.of(b));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fhirValidationService.validateBundle(anyString())).thenReturn(new FhirValidationService.BundleValidationResult(
                1, 1, 0, List.of(entry("Patient", "p", true))));

        int scheduled = service().scheduleAll(5L);

        assertThat(scheduled).isEqualTo(2);
        assertThat(a.getValidationStatus()).isEqualTo(TestCaseValidation.VALID);
        assertThat(b.getValidationStatus()).isEqualTo(TestCaseValidation.VALID);
    }
}
