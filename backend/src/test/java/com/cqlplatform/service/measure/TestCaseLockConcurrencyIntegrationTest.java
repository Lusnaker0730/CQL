package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.TestCaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-253 — the race the first CI smoke run caught: the background FHIR validation (and a test
 * case run) loads the test case, does slow work, then saves it. With Hibernate's default full-row
 * UPDATE that wrote every column back from the stale snapshot, so an edit lock taken in between
 * was silently reverted. {@code @DynamicUpdate} on {@code TestCaseEntity} makes the late save
 * write only the columns it changed. Two real transactions on two threads, so this class is not
 * {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
class TestCaseLockConcurrencyIntegrationTest {

    @Autowired private TestCaseService testCaseService;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private MeasureDefinitionRepository measureRepository;
    @Autowired private PlatformTransactionManager txManager;

    private Long measureId;
    private Long testCaseId;

    @AfterEach
    void cleanUp() {
        if (testCaseId != null) testCaseRepository.deleteById(testCaseId);
        if (measureId != null) measureRepository.deleteById(measureId);
    }

    @Test
    void aLockTakenWhileAValidationHoldsTheEntity_survivesTheValidationsSave() throws Exception {
        measureId = measureRepository.save(MeasureDefinitionEntity.builder()
                .name("LockRaceMeasure").version("1.0.0").status("draft").scoringType("cohort")
                .ownerUsername("owner").tenantId(1L).build()).getId();
        testCaseId = testCaseRepository.save(TestCaseEntity.builder()
                .measureDefinitionId(measureId).title("raced").patientBundleJson("{\"resourceType\":\"Bundle\"}").build()).getId();

        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch locked = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // the "validation": load, wait while someone else locks, then store the outcome
        Thread validation = new Thread(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    TestCaseEntity entity = testCaseRepository.findById(testCaseId).orElseThrow();
                    loaded.countDown();
                    try {
                        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    entity.setValidationStatus("valid");
                    entity.setValidatedAt(LocalDateTime.now());
                    testCaseRepository.save(entity);
                });
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "late-validation");
        validation.start();
        assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(testCaseService.lock(testCaseId, "alice").getLockedBy()).isEqualTo("alice");
        locked.countDown();
        validation.join(10_000);
        assertThat(failure.get()).isNull();

        TestCaseEntity after = testCaseRepository.findById(testCaseId).orElseThrow();
        assertThat(after.getValidationStatus()).isEqualTo("valid");   // the late save landed…
        assertThat(after.getLockedBy()).isEqualTo("alice");           // …without wiping the lock
        assertThat(after.getLockedAt()).isNotNull();
    }
}
