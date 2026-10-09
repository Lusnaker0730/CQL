package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.ApprovalNotAllowedException;
import com.cqlplatform.exception.MeasureNotReadyException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.ApprovalReadiness;
import com.cqlplatform.model.measure.TestCaseValidation;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.service.cql.CqlTranslationService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PAT-249 — the gate in front of the review workflow (MADiE refuses to version a measure whose
 * CQL has errors or whose test cases are invalid; this platform also asks that every test case
 * passes on the current logic, since the test cases are the verification evidence an approval
 * rests on). {@link #check} describes the state; {@link #requireReady} turns blockers into a 409;
 * {@link #requireFourEyes} refuses the author / submitter as approver.
 *
 * <p>Deliberately independent of {@link MeasureDefinitionService} (which calls it) and of
 * {@link MeasureValidationService} (which depends on the definition service): it reads the
 * entity it is handed plus the test case and audit tables.
 */
@Service
@Slf4j
public class ApprovalReadinessService {

    static final int MAX_NAMED_ITEMS = 5;
    static final String SUBMIT_ACTION = "SUBMIT_FOR_REVIEW";

    private final TestCaseRepository testCaseRepository;
    private final CqlTranslationService cqlTranslationService;
    private final MeasureAuditRepository auditRepository;

    /** {@code measure.review.four-eyes}: author / submitter may not approve (default on). */
    @Setter
    @Value("${measure.review.four-eyes:true}")
    private boolean fourEyes = true;

    public ApprovalReadinessService(TestCaseRepository testCaseRepository,
                                    CqlTranslationService cqlTranslationService,
                                    MeasureAuditRepository auditRepository) {
        this.testCaseRepository = testCaseRepository;
        this.cqlTranslationService = cqlTranslationService;
        this.auditRepository = auditRepository;
    }

    /** The full picture for {@code measure}, with the four-eyes verdict for {@code currentUser} (may be null). */
    public ApprovalReadiness check(MeasureDefinitionEntity measure, String currentUser) {
        List<ApprovalReadiness.Item> blockers = new ArrayList<>();
        List<ApprovalReadiness.Item> warnings = new ArrayList<>();

        int[] cqlCounts = checkCql(measure, blockers, warnings);
        ApprovalReadiness.TestCaseSummary summary = checkTestCases(measure, blockers, warnings);
        ApprovalReadiness.FourEyes eyes = fourEyes(measure, currentUser);

        return ApprovalReadiness.builder()
                .measureId(measure.getId())
                .status(measure.getStatus())
                .ready(blockers.isEmpty())
                .blockers(blockers)
                .warnings(warnings)
                .testCases(summary)
                .cqlErrorCount(cqlCounts[0])
                .cqlWarningCount(cqlCounts[1])
                .fourEyes(eyes)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    /** Refuses {@code action} (for the message) while the measure has blockers. */
    public void requireReady(MeasureDefinitionEntity measure, String action) {
        ApprovalReadiness readiness = check(measure, null);
        if (readiness.isReady()) return;
        List<String> details = readiness.getBlockers().stream().map(ApprovalReadiness.Item::getMessage).toList();
        log.info("Measure {} not ready to {}: {}", measure.getId(), action, details);
        throw new MeasureNotReadyException(
                "Measure '" + measure.getName() + "' is not ready to " + action + ": " + details.size()
                        + " blocker(s) — fix them and try again", details);
    }

    /** Four-eyes: the author and whoever submitted the measure for review may not approve it. */
    public void requireFourEyes(MeasureDefinitionEntity measure, String currentUser) {
        ApprovalReadiness.FourEyes eyes = fourEyes(measure, currentUser);
        if (!eyes.isSelfApprovalBlocked()) return;
        String who = currentUser != null && currentUser.equals(eyes.getSubmittedBy()) && !currentUser.equals(eyes.getAuthor())
                ? "the user who submitted it for review" : "its author";
        throw new ApprovalNotAllowedException("Four-eyes principle: " + who + " (" + currentUser
                + ") may not approve measure '" + measure.getName() + "'. Ask another reviewer it is shared with to approve it");
    }

    // ───────────────────────────────────────────────────────────── checks

    /** @return {errorCount, warningCount} */
    private int[] checkCql(MeasureDefinitionEntity measure, List<ApprovalReadiness.Item> blockers, List<ApprovalReadiness.Item> warnings) {
        String cql = measure.getCqlContent();
        if (cql == null || cql.isBlank()) {
            blockers.add(item(ApprovalReadiness.CQL_MISSING, 0, "The measure has no CQL", List.of()));
            return new int[] {0, 0};
        }
        CqlTranslationResponse response;
        try {
            CqlTranslationRequest request = new CqlTranslationRequest();
            request.setCql(cql);
            response = cqlTranslationService.translate(request);
        } catch (Exception e) {
            blockers.add(item(ApprovalReadiness.CQL_ERRORS, 1, "CQL does not translate: " + e.getMessage(), List.of()));
            return new int[] {1, 0};
        }
        int errors = response.getErrors() != null ? response.getErrors().size() : 0;
        int warningCount = response.getWarnings() != null ? response.getWarnings().size() : 0;
        if (!response.isSuccess() || errors > 0) {
            List<String> messages = new ArrayList<>();
            if (response.getErrors() != null) {
                for (CqlTranslationResponse.CqlError error : response.getErrors()) {
                    if (messages.size() >= MAX_NAMED_ITEMS) break;
                    messages.add(error.getStartLine() != null ? "line " + error.getStartLine() + ": " + error.getMessage() : error.getMessage());
                }
            }
            blockers.add(item(ApprovalReadiness.CQL_ERRORS, Math.max(errors, 1),
                    "CQL has " + Math.max(errors, 1) + " translation error(s)", messages));
        } else if (warningCount > 0) {
            warnings.add(item(ApprovalReadiness.CQL_WARNINGS, warningCount, "CQL translates with " + warningCount + " warning(s)", List.of()));
        }
        return new int[] {errors, warningCount};
    }

    private ApprovalReadiness.TestCaseSummary checkTestCases(MeasureDefinitionEntity measure,
                                                             List<ApprovalReadiness.Item> blockers,
                                                             List<ApprovalReadiness.Item> warnings) {
        List<TestCaseEntity> cases = testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(measure.getId());
        ApprovalReadiness.TestCaseSummary.TestCaseSummaryBuilder summary = ApprovalReadiness.TestCaseSummary.builder().total(cases.size());
        if (cases.isEmpty()) {
            warnings.add(item(ApprovalReadiness.NO_TEST_CASES, 0,
                    "The measure has no test cases — nothing verifies its logic", List.of()));
            return summary.build();
        }
        List<String> invalid = new ArrayList<>(), validationPending = new ArrayList<>(), validationError = new ArrayList<>(),
                neverValidated = new ArrayList<>(), notPassing = new ArrayList<>(), stale = new ArrayList<>();
        int passed = 0, failed = 0, errored = 0, notRun = 0, valid = 0;
        LocalDateTime changedAt = measure.getUpdatedAt();
        for (TestCaseEntity tc : cases) {
            String v = tc.getValidationStatus();
            if (v == null) neverValidated.add(tc.getTitle());
            else if (TestCaseValidation.INVALID.equals(v)) invalid.add(tc.getTitle());
            else if (TestCaseValidation.PENDING.equals(v)) validationPending.add(tc.getTitle());
            else if (TestCaseValidation.ERROR.equals(v)) validationError.add(tc.getTitle());
            else if (TestCaseValidation.VALID.equals(v)) valid++;

            String s = tc.getStatus();
            if ("pass".equals(s)) {
                passed++;
                if (changedAt != null && tc.getLastRunAt() != null && tc.getLastRunAt().isBefore(changedAt)) stale.add(tc.getTitle());
            } else {
                if ("fail".equals(s)) failed++;
                else if ("error".equals(s)) errored++;
                else notRun++;
                notPassing.add(tc.getTitle() + " (" + (s == null || s.isBlank() ? "not run" : s) + ")");
            }
        }
        if (!invalid.isEmpty()) blockers.add(item(ApprovalReadiness.TEST_CASE_INVALID, invalid.size(),
                invalid.size() + " test case(s) have FHIR validation errors", invalid));
        if (!validationPending.isEmpty()) blockers.add(item(ApprovalReadiness.TEST_CASE_VALIDATION_PENDING, validationPending.size(),
                validationPending.size() + " test case(s) are still being validated — wait or re-validate", validationPending));
        if (!notPassing.isEmpty()) blockers.add(item(ApprovalReadiness.TEST_CASE_NOT_PASSING, notPassing.size(),
                notPassing.size() + " of " + cases.size() + " test case(s) do not pass on the current logic (failed, errored or not run)", notPassing));
        if (!neverValidated.isEmpty()) warnings.add(item(ApprovalReadiness.TEST_CASE_NEVER_VALIDATED, neverValidated.size(),
                neverValidated.size() + " test case(s) were never FHIR-validated — run 'Validate all'", neverValidated));
        if (!validationError.isEmpty()) warnings.add(item(ApprovalReadiness.TEST_CASE_VALIDATION_ERROR, validationError.size(),
                validationError.size() + " test case(s) could not be validated (validator error) — re-validate", validationError));
        if (!stale.isEmpty()) warnings.add(item(ApprovalReadiness.TEST_CASE_RUN_STALE, stale.size(),
                stale.size() + " passing test case(s) last ran before the measure's last change — run them again", stale));
        return summary.passed(passed).failed(failed).errored(errored).notRun(notRun).stale(stale.size())
                .valid(valid).invalid(invalid.size()).validationPending(validationPending.size())
                .validationError(validationError.size()).neverValidated(neverValidated.size())
                .build();
    }

    private ApprovalReadiness.FourEyes fourEyes(MeasureDefinitionEntity measure, String currentUser) {
        String author = measure.getOwnerUsername() != null && !measure.getOwnerUsername().isBlank()
                ? measure.getOwnerUsername() : measure.getCreatedBy();
        String submittedBy = auditRepository.findFirstByMeasureIdAndActionOrderByCreatedAtDesc(measure.getId(), SUBMIT_ACTION)
                .map(MeasureAuditEntity::getPerformedBy).orElse(null);
        boolean blocked = fourEyes && currentUser != null
                && (Objects.equals(currentUser, author) || Objects.equals(currentUser, submittedBy));
        return ApprovalReadiness.FourEyes.builder()
                .enabled(fourEyes)
                .author(author)
                .submittedBy(submittedBy)
                .selfApprovalBlocked(blocked)
                .build();
    }

    private static ApprovalReadiness.Item item(String code, int count, String message, List<String> names) {
        List<String> capped = names.size() > MAX_NAMED_ITEMS ? new ArrayList<>(names.subList(0, MAX_NAMED_ITEMS)) : new ArrayList<>(names);
        if (names.size() > MAX_NAMED_ITEMS) capped.add("… and " + (names.size() - MAX_NAMED_ITEMS) + " more");
        return ApprovalReadiness.Item.builder().code(code).count(count).message(message).items(capped).build();
    }
}
