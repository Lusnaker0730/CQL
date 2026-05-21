package com.cqlplatform.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class AsyncConfig {

    /**
     * PAT-150 — keep references to the executor beans so {@link #shutdownExecutors()}
     * can drain in-flight work on application shutdown. Spring's default behavior
     * for {@code ExecutorService} beans is to call {@link ExecutorService#shutdown()}
     * (refuse new tasks), but it does NOT wait for in-flight tasks to complete;
     * a long CQL evaluation or patient import can be cut off mid-write when the
     * JVM exits. Awaiting termination here gives those tasks up to 20s to finish
     * before the JVM proceeds.
     */
    private final List<ExecutorService> registeredExecutors = new ArrayList<>();

    private static final long SHUTDOWN_AWAIT_SECONDS = 20L;

    @Value("${cql.execution.thread-pool-size:10}")
    private int corePoolSize;

    @Value("${cql.execution.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${cql.execution.queue-capacity:50}")
    private int queueCapacity;

    @Value("${ehr.import.thread-pool-size:4}")
    private int importCorePoolSize;

    @Value("${ehr.import.max-pool-size:8}")
    private int importMaxPoolSize;

    @Value("${ehr.import.queue-capacity:100}")
    private int importQueueCapacity;

    @Bean(name = "cqlExecutionExecutor")
    public ExecutorService cqlExecutionExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("cql-exec-" + t.getId());
                    t.setDaemon(false);
                    // Inherit the Spring Boot LaunchedURLClassLoader so that
                    // ServiceLoader-based CQL model discovery works correctly
                    t.setContextClassLoader(Thread.currentThread().getContextClassLoader());
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        registeredExecutors.add(executor);
        return executor;
    }

    // Dedicated pool for bulk patient import. Previously shared cqlExecutionExecutor,
    // which meant a 100-patient import could starve /api/cql/execute requests.
    // AbortPolicy (not CallerRunsPolicy) so the submitting request fails fast if the
    // queue overflows rather than blocking the API thread for minutes.
    @Bean(name = "patientImportExecutor")
    public ExecutorService patientImportExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                importCorePoolSize,
                importMaxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(importQueueCapacity),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("patient-import-" + t.getId());
                    t.setDaemon(false);
                    t.setContextClassLoader(Thread.currentThread().getContextClassLoader());
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        registeredExecutors.add(executor);
        return executor;
    }

    /**
     * PAT-150 — graceful shutdown for both executor pools.
     *
     * <p>Steps:
     * <ol>
     *   <li>{@code shutdown()} — refuse new tasks but let queued work finish.</li>
     *   <li>{@code awaitTermination(20s)} — give in-flight CQL evaluations and
     *       patient imports a chance to complete.</li>
     *   <li>{@code shutdownNow()} — interrupt anything still running so the
     *       JVM doesn't hang past the grace window.</li>
     * </ol>
     */
    @PreDestroy
    void shutdownExecutors() {
        for (ExecutorService executor : registeredExecutors) {
            executor.shutdown();
        }
        for (ExecutorService executor : registeredExecutors) {
            try {
                if (!executor.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("Executor did not terminate within {}s — forcing shutdownNow()", SHUTDOWN_AWAIT_SECONDS);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
        log.info("AsyncConfig executors shut down");
    }
}
