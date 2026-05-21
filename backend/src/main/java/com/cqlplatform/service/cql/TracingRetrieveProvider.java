package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlExecutionResponse.RetrieveTrace;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.Interval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Debug-mode retrieve wrapper that (1) records per-call traces for the frontend
 * debug panel and (2) memoizes results by retrieve key so multiple references to
 * the same CQL define don't re-hit the FHIR server.
 *
 * <p>The HAPI CQL engine does not cache retrieve results between expression-define
 * references within a single evaluate() call. BUG-116's batch-eval unification
 * reduced N separate engine.evaluate() calls to 1, but within that single call
 * 4 defines each referencing [Observation] still triggered 4 distinct retrieve()
 * invocations. User-visible impact on BMI CDS hook: still saw 5 Observation
 * trace rows (one per define-reference path) instead of 1. This provider-level
 * cache closes that gap.
 *
 * <p>Cache scope is per-instance — CqlExecutionService constructs a fresh
 * TracingRetrieveProvider per debug-mode request, so the cache never leaks
 * across requests / tenants / patient contexts.
 */
public class TracingRetrieveProvider implements RetrieveProvider {

    private final RetrieveProvider delegate;
    private final List<RetrieveTrace> traces = new CopyOnWriteArrayList<>();

    /** Result cache keyed by the stable signature of all retrieve() parameters that
     *  could change the returned set. First call executes + populates; later calls
     *  with the same key reuse the cached List and DO NOT append a new trace. */
    private final Map<String, List<Object>> resultCache = new LinkedHashMap<>();

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

        String cacheKey = buildCacheKey(context, contextPath, contextValue,
                dataType, templateId, codePath, codes, valueSet,
                datePath, dateLowPath, dateHighPath, dateRange);

        // Synchronise on this instance — single debug request is single-threaded
        // in practice, but defense in depth against future concurrent engine usage.
        synchronized (resultCache) {
            List<Object> cached = resultCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        long start = System.currentTimeMillis();
        Iterable<Object> results = delegate.retrieve(
                context, contextPath, contextValue,
                dataType, templateId,
                codePath, codes, valueSet,
                datePath, dateLowPath, dateHighPath, dateRange);

        List<Object> resultList = new ArrayList<>();
        if (results != null) {
            for (Object obj : results) {
                resultList.add(obj);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        traces.add(RetrieveTrace.builder()
                .resourceType(dataType != null ? dataType : "Unknown")
                .resourceCount(resultList.size())
                .retrieveTimeMs(elapsed)
                .build());

        synchronized (resultCache) {
            resultCache.put(cacheKey, resultList);
        }
        return resultList;
    }

    public List<RetrieveTrace> getTraces() {
        return new ArrayList<>(traces);
    }

    /** Stable string signature for cache equality. Must include every parameter
     *  that affects which resources are returned; omitting one would silently
     *  return stale results for a legitimately-different retrieve. */
    private static String buildCacheKey(
            String context, String contextPath, Object contextValue,
            String dataType, String templateId,
            String codePath, Iterable<Code> codes, String valueSet,
            String datePath, String dateLowPath, String dateHighPath, Interval dateRange) {
        StringJoiner codesJoin = new StringJoiner(",", "[", "]");
        if (codes != null) {
            for (Code c : codes) {
                if (c == null) {
                    codesJoin.add("null");
                } else {
                    codesJoin.add(Objects.toString(c.getSystem()) + "|" + Objects.toString(c.getCode()));
                }
            }
        }
        return new StringJoiner("§")
                .add(Objects.toString(context))
                .add(Objects.toString(contextPath))
                .add(Objects.toString(contextValue))
                .add(Objects.toString(dataType))
                .add(Objects.toString(templateId))
                .add(Objects.toString(codePath))
                .add(codesJoin.toString())
                .add(Objects.toString(valueSet))
                .add(Objects.toString(datePath))
                .add(Objects.toString(dateLowPath))
                .add(Objects.toString(dateHighPath))
                .add(Objects.toString(dateRange))
                .toString();
    }
}
