package com.cqlplatform.service.measure;

import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.model.measure.TestCaseValidation;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.security.TenantContext;
import com.cqlplatform.service.fhir.FhirValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * PAT-245 — validates a test case's patient bundle with the platform's FHIR validator and stores
 * the outcome on the test case ({@link TestCaseValidation}).
 *
 * <p>Validation runs <em>off</em> the request thread: the HAPI validator takes seconds per bundle
 * (tens of seconds the first time the profiles load), and a save must not wait for it. A save
 * marks the test case {@code pending} and queues the run on a dedicated single-thread executor
 * after the transaction commits, so the worker always sees the saved row. The caller's tenant is
 * carried onto the worker thread ({@code test_case} is behind row-level security keyed by its
 * measure). A synchronous {@link #validateNow} exists for the "re-validate" button and tests.
 */
@Service
@Slf4j
public class TestCaseValidationService {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final TestCaseRepository repository;
    private final FhirValidationService fhirValidationService;
    private final ExecutorService executor;

    public TestCaseValidationService(TestCaseRepository repository,
                                     FhirValidationService fhirValidationService,
                                     @Qualifier("testCaseValidationExecutor") ExecutorService executor) {
        this.repository = repository;
        this.fhirValidationService = fhirValidationService;
        this.executor = executor;
    }

    /**
     * Marks the (unsaved or saved) entity pending; the caller persists it. Pair with
     * {@link #scheduleValidation(Long)} once the id is known.
     */
    public void markPending(TestCaseEntity entity) {
        entity.setValidationStatus(TestCaseValidation.PENDING);
        entity.setValidationSummary(null);
        entity.setValidatedAt(null);
    }

    /**
     * Queues a validation run for the test case, after the surrounding transaction commits (or
     * at once when there is none). A full queue leaves the test case {@code pending} and logs —
     * the author can re-validate by hand.
     */
    public void scheduleValidation(Long testCaseId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Runnable submit = () -> {
            try {
                executor.submit(() -> TenantContext.callWith(tenantId, () -> {
                    try {
                        validateNow(testCaseId);
                    } catch (Exception e) {
                        log.warn("Background validation of test case {} failed: {}", testCaseId, e.getMessage());
                    }
                    return null;
                }));
            } catch (RejectedExecutionException e) {
                log.warn("Validation queue full; test case {} stays pending until re-validated", testCaseId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }

    /** Queues every test case of the measure; returns how many were queued. */
    @Transactional
    public int scheduleAll(Long measureDefinitionId) {
        List<TestCaseEntity> entities = repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(measureDefinitionId);
        for (TestCaseEntity entity : entities) {
            markPending(entity);
            repository.save(entity);
            scheduleValidation(entity.getId());
        }
        return entities.size();
    }

    /** Validates the test case now, stores the outcome and returns it. */
    @Transactional
    public TestCaseValidation validateNow(Long testCaseId) {
        TestCaseEntity entity = repository.findById(testCaseId)
                .orElseThrow(() -> new IllegalArgumentException("Test case not found: " + testCaseId));
        TestCaseValidation outcome = validate(entity.getPatientBundleJson());
        entity.setValidationStatus(outcome.getStatus());
        entity.setValidatedAt(outcome.getValidatedAt());
        entity.setValidationSummary(write(outcome));
        repository.save(entity);
        log.info("Validated test case {}: {} ({} resources, {} invalid, {} errors, {} warnings)",
                testCaseId, outcome.getStatus(), outcome.getTotalResources(), outcome.getInvalidResources(),
                outcome.getErrorCount(), outcome.getWarningCount());
        return outcome;
    }

    /** The validation of one bundle, as stored on the test case. */
    public TestCaseValidation validate(String bundleJson) {
        LocalDateTime now = LocalDateTime.now();
        if (bundleJson == null || bundleJson.isBlank()) {
            return TestCaseValidation.builder().status(TestCaseValidation.INVALID).validatedAt(now)
                    .totalResources(0).invalidResources(0).errorCount(0).warningCount(0)
                    .message("The test case has no patient bundle").issues(List.of()).build();
        }
        FhirValidationService.BundleValidationResult result;
        try {
            result = fhirValidationService.validateBundle(bundleJson);
        } catch (Exception e) {
            return TestCaseValidation.builder().status(TestCaseValidation.ERROR).validatedAt(now)
                    .message("Validator failed: " + e.getMessage()).issues(List.of()).build();
        }
        if (result == null || result.entries() == null || result.entries().isEmpty()) {
            // validateBundle swallows parse failures into an empty result — an unparseable or empty
            // bundle is not a valid test case
            return TestCaseValidation.builder().status(TestCaseValidation.INVALID).validatedAt(now)
                    .totalResources(0).invalidResources(0).errorCount(0).warningCount(0)
                    .message("The patient bundle could not be parsed as a FHIR Bundle, or it contains no resources")
                    .issues(List.of()).build();
        }
        List<TestCaseValidation.Issue> issues = new ArrayList<>();
        int errors = 0;
        int warnings = 0;
        for (FhirValidationService.ResourceValidationEntry entry : result.entries()) {
            if (entry.issues() == null) continue;
            for (FhirValidationService.ValidationIssue issue : entry.issues()) {
                String severity = issue.severity() == null ? "" : issue.severity().toLowerCase();
                if ("error".equals(severity) || "fatal".equals(severity)) {
                    errors++;
                    if (issues.size() < TestCaseValidation.MAX_ISSUES) {
                        issues.add(TestCaseValidation.Issue.builder()
                                .resourceType(entry.resourceType()).resourceId(entry.resourceId())
                                .severity(severity).location(issue.location()).message(issue.message())
                                .build());
                    }
                } else if ("warning".equals(severity)) {
                    warnings++;
                }
            }
        }
        return TestCaseValidation.builder()
                .status(result.invalidResources() > 0 ? TestCaseValidation.INVALID : TestCaseValidation.VALID)
                .validatedAt(now)
                .totalResources(result.totalResources())
                .invalidResources(result.invalidResources())
                .errorCount(errors)
                .warningCount(warnings)
                .issues(issues)
                .build();
    }

    /** The stored summary, or {@code null} when none / unreadable. */
    public static TestCaseValidation read(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, TestCaseValidation.class);
        } catch (Exception e) {
            return null;
        }
    }

    static String write(TestCaseValidation validation) {
        try {
            return MAPPER.writeValueAsString(validation);
        } catch (Exception e) {
            return null;
        }
    }

    /** Convenience: pending or missing summary → a summary carrying just the status. */
    public static TestCaseValidation summaryFor(TestCaseEntity entity) {
        TestCaseValidation stored = read(entity.getValidationSummary());
        if (stored != null) return stored;
        if (entity.getValidationStatus() == null) return null;
        return TestCaseValidation.builder().status(entity.getValidationStatus()).validatedAt(entity.getValidatedAt()).build();
    }

    /** True when the entity is known to be invalid (unknown / pending counts as not invalid). */
    public static boolean isInvalid(TestCaseEntity entity) {
        return TestCaseValidation.INVALID.equals(entity.getValidationStatus());
    }

    Optional<TestCaseEntity> find(Long id) {
        return repository.findById(id);
    }
}
