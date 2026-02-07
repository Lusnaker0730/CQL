package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlExecutionResponse.RetrieveTrace;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.Interval;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TracingRetrieveProvider implements RetrieveProvider {

    private final RetrieveProvider delegate;
    private final List<RetrieveTrace> traces = new CopyOnWriteArrayList<>();

    public TracingRetrieveProvider(RetrieveProvider delegate) {
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

        long start = System.currentTimeMillis();
        Iterable<Object> results = delegate.retrieve(
                context, contextPath, contextValue,
                dataType, templateId,
                codePath, codes, valueSet,
                datePath, dateLowPath, dateHighPath, dateRange);

        // Count results
        int count = 0;
        List<Object> resultList = new ArrayList<>();
        if (results != null) {
            for (Object obj : results) {
                resultList.add(obj);
                count++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        traces.add(RetrieveTrace.builder()
                .resourceType(dataType != null ? dataType : "Unknown")
                .resourceCount(count)
                .retrieveTimeMs(elapsed)
                .build());

        return resultList;
    }

    public List<RetrieveTrace> getTraces() {
        return new ArrayList<>(traces);
    }
}
