package com.cqlplatform.service.cql;

import com.cqlplatform.exception.CqlExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.Interval;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorator that enforces two safety limits on every {@code retrieve()} call:
 * <ol>
 *   <li><b>Interrupt check</b> — detects timeout cancellation from {@code future.cancel(true)}</li>
 *   <li><b>Result size cap</b> — prevents OOM by truncating oversized result sets.
 *       A CQL query like {@code [Observation]} (no date filter) could pull millions of rows
 *       into memory; this cap ensures the JVM stays healthy.</li>
 * </ol>
 */
@Slf4j
public class InterruptAwareRetrieveProvider implements RetrieveProvider {

    private final RetrieveProvider delegate;
    private final int maxRetrieveCount;

    public InterruptAwareRetrieveProvider(RetrieveProvider delegate) {
        this(delegate, 10_000);
    }

    public InterruptAwareRetrieveProvider(RetrieveProvider delegate, int maxRetrieveCount) {
        this.delegate = delegate;
        this.maxRetrieveCount = maxRetrieveCount;
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

        Iterable<Object> raw = delegate.retrieve(
                context, contextPath, contextValue,
                dataType, templateId,
                codePath, codes, valueSet,
                datePath, dateLowPath, dateHighPath, dateRange);

        // Materialise into a bounded list — stop consuming if cap is exceeded
        List<Object> bounded = new ArrayList<>();
        for (Object item : raw) {
            // Re-check interrupt between items (cheap and catches long iteration)
            if (Thread.currentThread().isInterrupted()) {
                throw new CqlExecutionException(
                        "CQL execution timed out (interrupted while iterating " + dataType + ")");
            }
            bounded.add(item);
            if (bounded.size() > maxRetrieveCount) {
                log.warn("Retrieve for {} exceeded max result count ({}). "
                        + "Results truncated — consider adding date or code filters to CQL.",
                        dataType, maxRetrieveCount);
                break;
            }
        }
        return bounded;
    }
}
