package com.cqlplatform.service.cds;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * In-memory ring buffer of the most recent CDS invocation summaries.
 * Intended for admin visibility — lightweight alternative to full log aggregation.
 */
@Service
public class CdsRecentInvocationsService {

    private static final int MAX_ENTRIES = 100;

    private final Deque<InvocationRecord> buffer = new LinkedList<>();

    public synchronized void record(InvocationRecord record) {
        buffer.addFirst(record);
        while (buffer.size() > MAX_ENTRIES) {
            buffer.removeLast();
        }
    }

    public synchronized List<InvocationRecord> recent(int limit) {
        int n = Math.min(limit, buffer.size());
        List<InvocationRecord> out = new ArrayList<>(n);
        int i = 0;
        for (InvocationRecord r : buffer) {
            if (i++ >= n) break;
            out.add(r);
        }
        return out;
    }

    @Data
    @Builder
    public static class InvocationRecord {
        private Instant timestamp;
        private String serviceId;
        private String hook;
        private String patientId;
        private boolean success;
        private long elapsedMs;
        /** Error phase if failed (e.g. "cql_execution"). */
        private String errorPhase;
        private String errorMessage;
    }
}
