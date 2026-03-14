package com.cqlplatform.service.cql;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.Interval;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorator that wraps every {@code retrieve()} call with a Resilience4j circuit breaker.
 * Shares the {@code "fhirDataProvider"} CB instance with {@link com.cqlplatform.service.fhir.FhirDataProviderService},
 * so failure counts from CQL retrieves and direct FHIR calls are combined.
 *
 * <p>When the circuit is open, {@code CallNotPermittedException} propagates up to
 * {@link CqlExecutionService}, which converts it to a 503 response.</p>
 *
 * <p>The Iterable is materialized (collected into a List) inside the CB scope so that
 * any lazy HTTP exceptions are recorded by the circuit breaker.</p>
 */
@Slf4j
public class CircuitBreakerRetrieveProvider implements RetrieveProvider {

    private final RetrieveProvider delegate;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerRetrieveProvider(RetrieveProvider delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Iterable<Object> retrieve(
            String context,
            String contextPath,
            Object contextValue,
            String dataType,
            String templateId,
            String codePath,
            Iterable<Code> codes,
            String valueSet,
            String datePath,
            String dateLowPath,
            String dateHighPath,
            Interval dateRange) {

        return circuitBreaker.executeSupplier(() -> {
            Iterable<Object> results = delegate.retrieve(
                    context, contextPath, contextValue,
                    dataType, templateId,
                    codePath, codes, valueSet,
                    datePath, dateLowPath, dateHighPath, dateRange);

            // Materialize the Iterable inside the CB scope so lazy HTTP errors
            // are recorded as failures by the circuit breaker.
            List<Object> materialized = new ArrayList<>();
            if (results != null) {
                results.forEach(materialized::add);
            }
            return materialized;
        });
    }
}
