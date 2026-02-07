package com.cqlplatform.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

/**
 * Tracks exceptions thrown from controllers and services via AOP.
 * Adds MDC context for structured logging and increments Micrometer error counters.
 */
@Configuration
@Aspect
@Slf4j
public class ErrorTrackingConfig {

    private final Counter controllerErrorCounter;
    private final Counter serviceErrorCounter;

    public ErrorTrackingConfig(MeterRegistry registry) {
        this.controllerErrorCounter = Counter.builder("application.controller.errors")
                .description("Total exceptions thrown from controllers")
                .register(registry);

        this.serviceErrorCounter = Counter.builder("application.service.errors")
                .description("Total exceptions thrown from services")
                .register(registry);
    }

    @AfterThrowing(pointcut = "within(com.cqlplatform.controller..*)", throwing = "ex")
    public void trackControllerException(Exception ex) {
        controllerErrorCounter.increment();
        MDC.put("errorType", ex.getClass().getSimpleName());
        MDC.put("errorMessage", ex.getMessage() != null ? ex.getMessage() : "null");
        log.warn("Controller exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        MDC.remove("errorType");
        MDC.remove("errorMessage");
    }

    @AfterThrowing(pointcut = "within(com.cqlplatform.service..*)", throwing = "ex")
    public void trackServiceException(Exception ex) {
        serviceErrorCounter.increment();
        MDC.put("errorType", ex.getClass().getSimpleName());
        MDC.put("errorMessage", ex.getMessage() != null ? ex.getMessage() : "null");
        log.warn("Service exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        MDC.remove("errorType");
        MDC.remove("errorMessage");
    }
}
