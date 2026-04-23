package com.cqlplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AsyncConfig {

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
        return executor;
    }
}
