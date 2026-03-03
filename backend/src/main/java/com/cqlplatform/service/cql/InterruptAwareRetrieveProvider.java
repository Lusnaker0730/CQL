package com.cqlplatform.service.cql;

import com.cqlplatform.exception.CqlExecutionException;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.Interval;

/**
 * Decorator that checks the thread's interrupt flag before every {@code retrieve()} call.
 * When the CQL execution timeout fires, {@code future.cancel(true)} sets the interrupt flag;
 * this provider detects it and throws, allowing the worker thread to exit cleanly.
 */
public class InterruptAwareRetrieveProvider implements RetrieveProvider {

    private final RetrieveProvider delegate;

    public InterruptAwareRetrieveProvider(RetrieveProvider delegate) {
        this.delegate = delegate;
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

        if (Thread.currentThread().isInterrupted()) {
            throw new CqlExecutionException(
                    "CQL execution timed out (interrupted during retrieve of " + dataType + ")");
        }

        return delegate.retrieve(
                context, contextPath, contextValue,
                dataType, templateId,
                codePath, codes, valueSet,
                datePath, dateLowPath, dateHighPath, dateRange);
    }
}
