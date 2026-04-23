package com.cqlplatform.config;

import com.cqlplatform.entity.MeasureReportEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class MetricsConfig {

    @Bean
    public Timer cqlTranslationTimer(MeterRegistry registry) {
        return Timer.builder("cql.translation.duration")
                .description("Time taken to translate CQL to ELM")
                .tag("operation", "translate")
                .register(registry);
    }

    @Bean
    public Counter cqlTranslationCounter(MeterRegistry registry) {
        return Counter.builder("cql.translation.total")
                .description("Total CQL translations")
                .tag("operation", "translate")
                .register(registry);
    }

    @Bean
    public Counter cqlTranslationErrorCounter(MeterRegistry registry) {
        return Counter.builder("cql.translation.errors")
                .description("CQL translation errors")
                .tag("operation", "translate")
                .register(registry);
    }

    @Bean
    public Timer cqlExecutionTimer(MeterRegistry registry) {
        return Timer.builder("cql.execution.duration")
                .description("Time taken to execute CQL")
                .tag("operation", "execute")
                .register(registry);
    }

    @Bean
    public Counter cqlExecutionCounter(MeterRegistry registry) {
        return Counter.builder("cql.execution.total")
                .description("Total CQL executions")
                .tag("operation", "execute")
                .register(registry);
    }

    @Bean
    public Counter cqlExecutionErrorCounter(MeterRegistry registry) {
        return Counter.builder("cql.execution.errors")
                .description("CQL execution errors")
                .tag("operation", "execute")
                .register(registry);
    }

    @Bean
    public Timer cdsInvocationTimer(MeterRegistry registry) {
        return Timer.builder("cds.invocation.duration")
                .description("Time taken to invoke CDS service")
                .tag("operation", "invoke")
                .register(registry);
    }

    @Bean
    public Counter cdsInvocationCounter(MeterRegistry registry) {
        return Counter.builder("cds.invocation.total")
                .description("Total CDS service invocations")
                .tag("operation", "invoke")
                .register(registry);
    }

    @Bean
    public Counter cdsInvocationErrorCounter(MeterRegistry registry) {
        return Counter.builder("cds.invocation.errors")
                .description("CDS invocation errors")
                .tag("operation", "invoke")
                .register(registry);
    }

    @Bean
    public Timer measureEvaluationTimer(MeterRegistry registry) {
        return Timer.builder("measure.evaluation.duration")
                .description("Time taken to evaluate a measure")
                .tag("operation", "evaluate")
                .register(registry);
    }

    @Bean
    public Counter measureEvaluationCounter(MeterRegistry registry) {
        return Counter.builder("measure.evaluation.total")
                .description("Total measure evaluations")
                .tag("operation", "evaluate")
                .register(registry);
    }

    @Bean
    public Counter measureEvaluationErrorCounter(MeterRegistry registry) {
        return Counter.builder("measure.evaluation.errors")
                .description("Measure evaluation errors")
                .tag("operation", "evaluate")
                .register(registry);
    }

    @Bean
    public Gauge cqlExecutionQueueSize(MeterRegistry registry,
            @Qualifier("cqlExecutionExecutor") ExecutorService executor) {
        return Gauge.builder("cql.execution.queue.size", executor,
                e -> ((ThreadPoolExecutor) e).getQueue().size())
                .description("Current CQL execution queue depth")
                .register(registry);
    }

    @Bean
    public Gauge cqlExecutionPoolActive(MeterRegistry registry,
            @Qualifier("cqlExecutionExecutor") ExecutorService executor) {
        return Gauge.builder("cql.execution.pool.active", executor,
                e -> ((ThreadPoolExecutor) e).getActiveCount())
                .description("Active CQL execution threads")
                .register(registry);
    }

    @Bean
    public Gauge cqlExecutionPoolSize(MeterRegistry registry,
            @Qualifier("cqlExecutionExecutor") ExecutorService executor) {
        return Gauge.builder("cql.execution.pool.size", executor,
                e -> ((ThreadPoolExecutor) e).getPoolSize())
                .description("Current CQL execution pool size")
                .register(registry);
    }

    @Bean
    public Gauge patientImportQueueSize(MeterRegistry registry,
            @Qualifier("patientImportExecutor") ExecutorService executor) {
        return Gauge.builder("patient.import.queue.size", executor,
                e -> ((ThreadPoolExecutor) e).getQueue().size())
                .description("Current patient import queue depth (bulk EHR import backlog)")
                .register(registry);
    }

    @Bean
    public Gauge patientImportPoolActive(MeterRegistry registry,
            @Qualifier("patientImportExecutor") ExecutorService executor) {
        return Gauge.builder("patient.import.pool.active", executor,
                e -> ((ThreadPoolExecutor) e).getActiveCount())
                .description("Active patient import threads")
                .register(registry);
    }

    @Bean
    public Gauge patientImportPoolSize(MeterRegistry registry,
            @Qualifier("patientImportExecutor") ExecutorService executor) {
        return Gauge.builder("patient.import.pool.size", executor,
                e -> ((ThreadPoolExecutor) e).getPoolSize())
                .description("Current patient import pool size")
                .register(registry);
    }

    // Non-zero means a measure_report row's result_json failed to deserialize at
    // @PostLoad — silent schema drift or data corruption. Alertable in Prometheus.
    @Bean
    public Gauge measureReportDeserializationFailures(MeterRegistry registry) {
        return Gauge.builder("measure.report.deserialization.failures",
                MeasureReportEntity.class,
                c -> MeasureReportEntity.getDeserializationFailureCount())
                .description("Cumulative count of measure_report rows whose result_json failed JSON deserialization at load time")
                .register(registry);
    }
}
