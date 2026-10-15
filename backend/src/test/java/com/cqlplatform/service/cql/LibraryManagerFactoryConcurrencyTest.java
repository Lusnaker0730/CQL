package com.cqlplatform.service.cql;

import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import org.cqframework.cql.cql2elm.ModelManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-152 (found by the PAT-266 concurrency run) — right after boot, every translation that started
 * at the same moment failed with "Circular model reference FHIR": the shared {@link ModelManager}
 * loads a model once and guards against recursion, and concurrent first loads trip that guard.
 * {@link LibraryManagerFactory#ensureModelsLoaded} serialises first loads; this test reproduces the
 * worst case — a fresh manager with nothing loaded and sixteen translations released together.
 */
@DisplayName("BUG-152 — concurrent first translations on a fresh shared ModelManager")
class LibraryManagerFactoryConcurrencyTest {

    private static final String CQL = """
            library ConcurrentProbe version '1.0.0'
            using FHIR version '4.0.1'
            include FHIRHelpers version '4.0.1' called FHIRHelpers
            context Patient
            define "Has Encounter": exists [Encounter]
            define "Initial Population": "Has Encounter"
            """;

    @Test
    void sixteenTranslationsReleasedTogether_allSucceed() throws Exception {
        // as right after boot: a shared manager that has loaded nothing yet
        ReflectionTestUtils.setField(LibraryManagerFactory.class, "sharedModelManager", new ModelManager());

        CqlExecutionService service = new CqlExecutionService(
                new FhirDataProviderService(null, null, null),
                new CqlEngineFuzzTest.NoOpTerminologyService(),
                Executors.newSingleThreadExecutor(),
                Mockito.mock(CqlLibraryRepository.class));
        ReflectionTestUtils.setField(service, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(service, "maxRetrieveCount", 10000);
        ReflectionTestUtils.setField(service, "maxCollectionSize", 1000);
        ReflectionTestUtils.setField(service, "defaultFhirServerUrl", "http://localhost:9999/fhir");

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> outcomes = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                outcomes.add(pool.submit(() -> {
                    go.await();
                    try {
                        CqlExecutionService.PreTranslatedContext ctx = service.translateOnce(CQL);
                        return ctx.elmLibrary() == null ? "no ELM" : null;
                    } catch (RuntimeException e) {
                        return e.getMessage();
                    }
                }));
            }
            go.countDown();
            List<String> failures = new ArrayList<>();
            for (Future<String> outcome : outcomes) {
                String failure = outcome.get(3, TimeUnit.MINUTES);
                if (failure != null) failures.add(failure);
            }
            assertThat(failures).as("every concurrent first translation must succeed").isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }
}
