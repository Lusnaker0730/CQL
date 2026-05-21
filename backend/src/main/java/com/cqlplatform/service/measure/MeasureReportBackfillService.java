package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.repository.MeasureReportGroupRepository;
import com.cqlplatform.repository.MeasureReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-shot startup task that backfills the normalized {@code measure_report_*} child tables
 * from each existing {@link MeasureReportEntity#getResultJson() result_json} blob.
 *
 * <p>Runs on {@link ApplicationReadyEvent} (async, doesn't block startup). Idempotent — skips
 * reports whose normalized rows are already present, so re-starts after a partial run are safe.
 *
 * <p>Disabled in tests / when explicitly opted out via {@code measure.report.backfill.enabled=false}.
 * Phase 1 of ADR-001 / code-review issue #6.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureReportBackfillService {

    private final MeasureReportRepository reportRepository;
    private final MeasureReportGroupRepository groupRepository;
    private final MeasureReportNormalizer normalizer;

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Value("${measure.report.backfill.enabled:true}")
    private boolean enabled;

    /** Cap the per-startup batch size so a huge historical DB doesn't block operator tasks. */
    @Value("${measure.report.backfill.batch-size:1000}")
    private int batchSize;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void runBackfillOnStartup() {
        if (!enabled) {
            log.info("measure_report backfill disabled via config");
            return;
        }
        try {
            int converted = runBackfill();
            if (converted == 0) {
                log.info("measure_report normalized-table backfill: nothing to do (all reports already normalized)");
            } else {
                log.info("measure_report normalized-table backfill: {} report(s) converted", converted);
            }
        } catch (Exception e) {
            // Backfill is best-effort. result_json remains the source of truth in Phase 1.
            log.error("measure_report normalized-table backfill failed — consumers are still safe "
                    + "(they read result_json). Investigate before dropping result_json in Phase 3.", e);
        }
    }

    /**
     * Core backfill loop. Package-visible for tests.
     *
     * @return number of reports whose normalized rows were (re-)populated this run
     */
    @Transactional
    int runBackfill() {
        // Identify already-backfilled report ids — any measure_report_id referenced by a row
        // in measure_report_group is considered done.
        List<Long> alreadyBackfilled = groupRepository.findDistinctMeasureReportIdsAlreadyBackfilled();
        Set<Long> done = new HashSet<>(alreadyBackfilled);

        // Stream a bounded batch of reports; avoids OOM on very large DBs.
        // Sorted by id asc so each run resumes roughly where the last left off when done-set is small.
        var page = reportRepository.findAll(
                org.springframework.data.domain.PageRequest.of(
                        0, batchSize, org.springframework.data.domain.Sort.by("id").ascending()));
        List<MeasureReportEntity> candidates = page.getContent();
        int converted = 0;
        int failed = 0;
        for (MeasureReportEntity report : candidates) {
            if (done.contains(report.getId())) continue;
            try {
                MeasureEvaluationResult result = parseResult(report);
                if (result == null) {
                    // Either result_json is missing/blank or deserialization failed (now WARN-logged
                    // by MeasureReportEntity.@PostLoad). Skip — nothing to normalize.
                    continue;
                }
                normalizer.persist(report.getId(), result);
                converted++;
            } catch (Exception e) {
                failed++;
                log.warn("backfill failed for measure_report id={}: {}", report.getId(), e.getMessage());
            }
        }
        if (failed > 0) {
            log.warn("backfill completed with {} failure(s); see preceding warnings", failed);
        }
        return converted;
    }

    private static MeasureEvaluationResult parseResult(MeasureReportEntity report) {
        // Prefer the already-deserialized transient (populated by @PostLoad), fall back to re-parse
        MeasureEvaluationResult cached = report.getEvaluationResult();
        if (cached != null) return cached;
        String json = report.getResultJson();
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, MeasureEvaluationResult.class);
        } catch (Exception e) {
            return null;
        }
    }
}
