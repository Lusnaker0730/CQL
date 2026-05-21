package com.cqlplatform.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-109: {@link AsyncConfig#patientImportExecutor()} must be an isolated pool
 * so bulk patient imports cannot starve the CQL execution pool. Tests the bean
 * characteristics that the isolation story depends on: separate instance,
 * independent size, AbortPolicy (fail fast instead of blocking the caller).
 */
@DisplayName("AsyncConfig — patientImportExecutor isolation characteristics")
class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();
    private ExecutorService cqlExec;
    private ExecutorService importExec;

    private void configureWith(int coreCql, int maxCql, int queueCql,
                               int coreImport, int maxImport, int queueImport) {
        ReflectionTestUtils.setField(asyncConfig, "corePoolSize", coreCql);
        ReflectionTestUtils.setField(asyncConfig, "maxPoolSize", maxCql);
        ReflectionTestUtils.setField(asyncConfig, "queueCapacity", queueCql);
        ReflectionTestUtils.setField(asyncConfig, "importCorePoolSize", coreImport);
        ReflectionTestUtils.setField(asyncConfig, "importMaxPoolSize", maxImport);
        ReflectionTestUtils.setField(asyncConfig, "importQueueCapacity", queueImport);
        cqlExec = asyncConfig.cqlExecutionExecutor();
        importExec = asyncConfig.patientImportExecutor();
    }

    @AfterEach
    void shutdown() {
        if (cqlExec != null) cqlExec.shutdownNow();
        if (importExec != null) importExec.shutdownNow();
    }

    @Test
    @DisplayName("patientImportExecutor is a separate ThreadPoolExecutor instance from cqlExecutionExecutor")
    void importExecutorIsSeparateInstance() {
        configureWith(10, 20, 50, 4, 8, 100);

        assertThat(importExec).isNotSameAs(cqlExec);
        assertThat(importExec).isInstanceOf(ThreadPoolExecutor.class);
    }

    @Test
    @DisplayName("patientImportExecutor honors independent size + queue properties")
    void importExecutorHasIndependentSizing() {
        configureWith(10, 20, 50, 4, 8, 100);
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) importExec;

        assertThat(tpe.getCorePoolSize()).isEqualTo(4);
        assertThat(tpe.getMaximumPoolSize()).isEqualTo(8);
        assertThat(tpe.getQueue().remainingCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("patientImportExecutor uses AbortPolicy — callers fail fast on saturation")
    void importExecutorAbortsWhenSaturated() {
        configureWith(10, 20, 50, 1, 1, 1);
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) importExec;

        assertThat(tpe.getRejectedExecutionHandler())
                .as("bulk import must NOT block the caller thread on saturation — use AbortPolicy so /api/ehr/import returns 503 fast")
                .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);

        AtomicInteger blockCount = new AtomicInteger();
        Runnable blockingTask = () -> {
            try { Thread.sleep(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            blockCount.incrementAndGet();
        };
        // Fill pool (1) + queue (1) → 3rd submit must be rejected
        importExec.submit(blockingTask);
        importExec.submit(blockingTask);
        assertThatThrownBy(() -> importExec.submit(blockingTask))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    @DisplayName("cqlExecutionExecutor unchanged — keeps CallerRunsPolicy for backpressure")
    void cqlExecutorKeepsCallerRunsPolicy() {
        configureWith(10, 20, 50, 4, 8, 100);
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) cqlExec;

        assertThat(tpe.getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        assertThat(tpe.getCorePoolSize()).isEqualTo(10);
        assertThat(tpe.getMaximumPoolSize()).isEqualTo(20);
    }

    // =====================================================================
    // PAT-150 — graceful shutdown via @PreDestroy
    // =====================================================================

    @Test
    @DisplayName("PAT-150: shutdownExecutors drains both pools and waits for in-flight tasks")
    void shutdownDrainsBothPools() throws Exception {
        configureWith(2, 2, 10, 2, 2, 10);

        // Submit a quick task — must complete before shutdown returns
        AtomicInteger completed = new AtomicInteger();
        cqlExec.submit(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            completed.incrementAndGet();
        });
        importExec.submit(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            completed.incrementAndGet();
        });

        // Invoke @PreDestroy directly (Spring would in production)
        ReflectionTestUtils.invokeMethod(asyncConfig, "shutdownExecutors");

        assertThat(cqlExec.isShutdown()).isTrue();
        assertThat(importExec.isShutdown()).isTrue();
        // Tasks were short — must have run before grace window expired
        assertThat(completed.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("PAT-150: shutdownExecutors is idempotent (safe to call twice)")
    void shutdownIdempotent() {
        configureWith(2, 2, 10, 2, 2, 10);

        // Calling twice must not throw
        ReflectionTestUtils.invokeMethod(asyncConfig, "shutdownExecutors");
        ReflectionTestUtils.invokeMethod(asyncConfig, "shutdownExecutors");

        assertThat(cqlExec.isShutdown()).isTrue();
        assertThat(importExec.isShutdown()).isTrue();
    }
}
