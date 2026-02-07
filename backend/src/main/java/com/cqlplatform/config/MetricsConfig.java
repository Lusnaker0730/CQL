package com.cqlplatform.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
            @Qualifier("cqlExecutionExecutor") ThreadPoolTaskExecutor executor) {
        return Gauge.builder("cql.execution.queue.size", executor,
                e -> e.getThreadPoolExecutor().getQueue().size())
                .description("Current CQL execution queue depth")
                .register(registry);
    }

    @Bean
    public Gauge cqlExecutionPoolActive(MeterRegistry registry,
            @Qualifier("cqlExecutionExecutor") ThreadPoolTaskExecutor executor) {
        return Gauge.builder("cql.execution.pool.active", executor,
                e -> e.getThreadPoolExecutor().getActiveCount())
                .description("Active CQL execution threads")
                .register(registry);
    }

    @Bean
    public Gauge cqlExecutionPoolSize(MeterRegistry registry,
            @Qualifier("cqlExecutionExecutor") ThreadPoolTaskExecutor executor) {
        return Gauge.builder("cql.execution.pool.size", executor,
                e -> e.getThreadPoolExecutor().getPoolSize())
                .description("Current CQL execution pool size")
                .register(registry);
    }
}
