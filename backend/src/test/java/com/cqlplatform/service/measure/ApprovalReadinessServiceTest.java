package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.ApprovalNotAllowedException;
import com.cqlplatform.exception.MeasureNotReadyException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.ApprovalReadiness;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.service.cql.CqlTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * PAT-249 — the gate in front of submit-for-review / approve: CQL must translate, every test case
 * must be FHIR-valid and pass on the current logic; warnings for the rest; four-eyes refuses the
 * author and the submitter as approver.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalReadinessServiceTest {

    @Mock private TestCaseRepository testCaseRepository;
    @Mock private CqlTranslationService cqlTranslationService;
    @Mock private MeasureAuditRepository auditRepository;

    private ApprovalReadinessService service;

    private static final LocalDateTime CHANGED = LocalDateTime.of(2026, 10, 1, 12, 0);

    @BeforeEach
    void setUp() {
        service = new ApprovalReadinessService(testCaseRepository, cqlTranslationService, auditRepository);
        service.setFourEyes(true);
        lenient().when(auditRepository.findFirstByMeasureIdAndActionOrderByCreatedAtDesc(any(), eq("SUBMIT_FOR_REVIEW")))
                .thenReturn(Optional.empty());
    }

    private static MeasureDefinitionEntity measure(String owner) {
        return MeasureDefinitionEntity.builder()
                .id(10L).name("HbA1c").version("1.0.0").status("draft").ownerUsername(owner).createdBy("creator")
                .cqlContent("library HbA1c version '1.0.0'\ndefine IP: true")
                .updatedAt(CHANGED)
                .build();
    }

    private static TestCaseEntity tc(String title, String status, String validation, LocalDateTime lastRun) {
        return TestCaseEntity.builder().id((long) title.hashCode()).measureDefinitionId(10L).title(title)
                .status(status).validationStatus(validation).lastRunAt(lastRun)
                .patientBundleJson("{\"resourceType\":\"Bundle\"}").build();
    }

    private void cqlTranslates() {
        when(cqlTranslationService.translate(any(CqlTranslationRequest.class)))
                .thenReturn(CqlTranslationResponse.builder().success(true).errors(List.of()).warnings(List.of()).build());
    }

    private static List<String> codes(List<ApprovalReadiness.Item> items) {
        return items.stream().map(ApprovalReadiness.Item::getCode).toList();
    }

    @Test
    void ready_whenCqlTranslates_andEveryTestCaseIsValidAndPassesOnTheCurrentLogic() {
        cqlTranslates();
        when(testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(
                tc("controlled", "pass", "valid", CHANGED.plusHours(1)),
                tc("excluded", "pass", "valid", CHANGED.plusHours(2))));

        ApprovalReadiness r = service.check(measure("alice"), "bob");

        assertThat(r.isReady()).isTrue();
        assertThat(r.getBlockers()).isEmpty();
        assertThat(r.getWarnings()).isEmpty();
        assertThat(r.getTestCases().getTotal()).isEqualTo(2);
        assertThat(r.getTestCases().getPassed()).isEqualTo(2);
        assertThat(r.getTestCases().getValid()).isEqualTo(2);
        assertThat(r.getFourEyes().isEnabled()).isTrue();
        assertThat(r.getFourEyes().getAuthor()).isEqualTo("alice");
        assertThat(r.getFourEyes().isSelfApprovalBlocked()).as("bob is not the author").isFalse();
        assertThatCode(() -> service.requireReady(measure("alice"), "be approved")).doesNotThrowAnyException();
    }

    @Test
    void blockers_cqlErrors_invalidTestCase_pendingValidation_andNotPassingTestCases() {
        when(cqlTranslationService.translate(any(CqlTranslationRequest.class))).thenReturn(CqlTranslationResponse.builder()
                .success(false)
                .errors(List.of(CqlTranslationResponse.CqlError.builder().severity("error").message("Could not resolve identifier X").startLine(7).build()))
                .build());
        when(testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(
                tc("bad bundle", "pass", "invalid", CHANGED.plusHours(1)),
                tc("still validating", "pass", "pending", CHANGED.plusHours(1)),
                tc("wrong expectation", "fail", "valid", CHANGED.plusHours(1)),
                tc("crashed", "error", "valid", CHANGED.plusHours(1)),
                tc("never run", "pending", "valid", null)));

        ApprovalReadiness r = service.check(measure("alice"), null);

        assertThat(r.isReady()).isFalse();
        assertThat(codes(r.getBlockers())).containsExactly(
                ApprovalReadiness.CQL_ERRORS, ApprovalReadiness.TEST_CASE_INVALID,
                ApprovalReadiness.TEST_CASE_VALIDATION_PENDING, ApprovalReadiness.TEST_CASE_NOT_PASSING);
        assertThat(r.getBlockers().get(0).getItems()).containsExactly("line 7: Could not resolve identifier X");
        assertThat(r.getBlockers().get(1).getItems()).containsExactly("bad bundle");
        assertThat(r.getBlockers().get(3).getCount()).isEqualTo(3);
        assertThat(r.getBlockers().get(3).getItems()).containsExactly("wrong expectation (fail)", "crashed (error)", "never run (pending)");
        assertThat(r.getTestCases().getFailed()).isEqualTo(1);
        assertThat(r.getTestCases().getErrored()).isEqualTo(1);
        assertThat(r.getTestCases().getNotRun()).isEqualTo(1);
        assertThat(r.getTestCases().getInvalid()).isEqualTo(1);
        assertThat(r.getTestCases().getValidationPending()).isEqualTo(1);
        assertThat(r.getCqlErrorCount()).isEqualTo(1);

        assertThatThrownBy(() -> service.requireReady(measure("alice"), "be submitted for review"))
                .isInstanceOf(MeasureNotReadyException.class)
                .hasMessageContaining("not ready to be submitted for review")
                .hasMessageContaining("4 blocker(s)")
                .extracting(e -> ((MeasureNotReadyException) e).getDetails())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .hasSize(4);
    }

    @Test
    void warnings_noTestCases_neverValidated_validatorError_staleRun_andCqlWarnings_doNotBlock() {
        when(cqlTranslationService.translate(any(CqlTranslationRequest.class))).thenReturn(CqlTranslationResponse.builder()
                .success(true).errors(List.of())
                .warnings(List.of(CqlTranslationResponse.CqlError.builder().severity("warning").message("unused").build()))
                .build());
        when(testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        ApprovalReadiness empty = service.check(measure("alice"), null);
        assertThat(empty.isReady()).isTrue();
        assertThat(codes(empty.getWarnings())).containsExactly(ApprovalReadiness.CQL_WARNINGS, ApprovalReadiness.NO_TEST_CASES);
        assertThat(empty.getCqlWarningCount()).isEqualTo(1);

        when(testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(
                tc("old pass", "pass", null, CHANGED.minusDays(1)),
                tc("validator down", "pass", "error", CHANGED.plusHours(1))));
        ApprovalReadiness r = service.check(measure("alice"), null);
        assertThat(r.isReady()).isTrue();
        assertThat(codes(r.getWarnings())).containsExactly(ApprovalReadiness.CQL_WARNINGS,
                ApprovalReadiness.TEST_CASE_NEVER_VALIDATED, ApprovalReadiness.TEST_CASE_VALIDATION_ERROR, ApprovalReadiness.TEST_CASE_RUN_STALE);
        assertThat(r.getTestCases().getStale()).isEqualTo(1);
        assertThat(r.getTestCases().getNeverValidated()).isEqualTo(1);
        assertThat(r.getTestCases().getValidationError()).isEqualTo(1);
    }

    @Test
    void missingCql_orATranslatorCrash_isABlocker() {
        when(testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());
        MeasureDefinitionEntity noCql = measure("alice");
        noCql.setCqlContent("  ");
        assertThat(codes(service.check(noCql, null).getBlockers())).containsExactly(ApprovalReadiness.CQL_MISSING);

        when(cqlTranslationService.translate(any(CqlTranslationRequest.class))).thenThrow(new IllegalStateException("translator exploded"));
        ApprovalReadiness r = service.check(measure("alice"), null);
        assertThat(codes(r.getBlockers())).containsExactly(ApprovalReadiness.CQL_ERRORS);
        assertThat(r.getBlockers().get(0).getMessage()).contains("translator exploded");
    }

    @Test
    void fourEyes_refusesTheAuthorAndTheSubmitter_allowsAnotherReviewer_andCanBeSwitchedOff() {
        MeasureDefinitionEntity m = measure("alice");
        when(auditRepository.findFirstByMeasureIdAndActionOrderByCreatedAtDesc(10L, "SUBMIT_FOR_REVIEW"))
                .thenReturn(Optional.of(MeasureAuditEntity.builder().measureId(10L).action("SUBMIT_FOR_REVIEW").performedBy("carol").build()));

        assertThatThrownBy(() -> service.requireFourEyes(m, "alice"))
                .isInstanceOf(ApprovalNotAllowedException.class).hasMessageContaining("its author (alice)");
        assertThatThrownBy(() -> service.requireFourEyes(m, "carol"))
                .isInstanceOf(ApprovalNotAllowedException.class).hasMessageContaining("submitted it for review (carol)");
        assertThatCode(() -> service.requireFourEyes(m, "bob")).doesNotThrowAnyException();

        // no owner → the creator counts as the author
        MeasureDefinitionEntity ownerless = measure(null);
        assertThatThrownBy(() -> service.requireFourEyes(ownerless, "creator")).isInstanceOf(ApprovalNotAllowedException.class);

        service.setFourEyes(false);
        assertThatCode(() -> service.requireFourEyes(m, "alice")).doesNotThrowAnyException();
        cqlTranslates();
        when(testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());
        ApprovalReadiness r = service.check(m, "alice");
        assertThat(r.getFourEyes().isEnabled()).isFalse();
        assertThat(r.getFourEyes().isSelfApprovalBlocked()).isFalse();
        assertThat(r.getFourEyes().getSubmittedBy()).isEqualTo("carol");
    }

    @Test
    void itemsNameAtMostFiveTestCases_thenCountTheRest() {
        cqlTranslates();
        when(testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(
                tc("a", "fail", "valid", CHANGED), tc("b", "fail", "valid", CHANGED), tc("c", "fail", "valid", CHANGED),
                tc("d", "fail", "valid", CHANGED), tc("e", "fail", "valid", CHANGED), tc("f", "fail", "valid", CHANGED),
                tc("g", "fail", "valid", CHANGED)));

        ApprovalReadiness.Item notPassing = service.check(measure("alice"), null).getBlockers().get(0);

        assertThat(notPassing.getCode()).isEqualTo(ApprovalReadiness.TEST_CASE_NOT_PASSING);
        assertThat(notPassing.getCount()).isEqualTo(7);
        assertThat(notPassing.getItems()).hasSize(6).last().isEqualTo("… and 2 more");
    }
}
