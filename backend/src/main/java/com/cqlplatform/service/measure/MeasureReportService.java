package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.repository.MeasureReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureReportService {

    private final MeasureReportRepository repository;
    private final MeasureReportNormalizer normalizer;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Transactional
    public MeasureReportEntity saveReport(MeasureEvaluationResult result, Long measureDefinitionId,
                                          String fhirServerUrl, String evaluatedBy, long durationMs) {
        try {
            String resultJson = MAPPER.writeValueAsString(result);

            Double measureScore = null;
            Integer totalPatients = null;
            if (result.getGroups() != null && !result.getGroups().isEmpty()) {
                measureScore = result.getGroups().get(0).getMeasureScore();
                totalPatients = result.getGroups().get(0).getTotalPatients();
            }

            MeasureReportEntity entity = MeasureReportEntity.builder()
                    .measureDefinitionId(measureDefinitionId)
                    .measureName(result.getMeasureName() != null ? result.getMeasureName() : result.getMeasureId())
                    .status(result.getStatus())
                    .reportType(result.getReportType())
                    .periodStart(result.getPeriodStart())
                    .periodEnd(result.getPeriodEnd())
                    .scoringType(com.cqlplatform.model.measure.ScoringTypeConstants.PROPORTION)
                    .measureScore(measureScore)
                    .totalPatients(totalPatients)
                    .resultJson(resultJson)
                    .fhirServerUrl(fhirServerUrl)
                    .evaluatedBy(evaluatedBy)
                    .evaluationDurationMs(durationMs)
                    .build();

            entity = repository.save(entity);

            // Dual-write (Phase 1 of ADR-001): populate the normalized child tables alongside
            // result_json. Consumers keep reading result_json until Phase 2 migrates each one.
            // Any failure here does NOT abort the save — the report itself is canonical in
            // result_json, and the backfill service can retry the normalized write later.
            try {
                normalizer.persist(entity.getId(), result);
            } catch (Exception normErr) {
                log.warn("Dual-write to normalized measure_report_* tables failed for report {}: {}. "
                                + "Primary result_json is saved; backfill will retry on next startup.",
                        entity.getId(), normErr.getMessage(), normErr);
            }

            log.info("Saved measure report {} for measure {}", entity.getId(), entity.getMeasureName());
            return entity;
        } catch (Exception e) {
            log.error("Failed to save measure report", e);
            throw new com.cqlplatform.exception.CqlExecutionException("Failed to save measure report: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<MeasureReportEntity> getRecentReports() {
        return repository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<MeasureReportEntity> getReportsForMeasure(Long measureDefinitionId) {
        return repository.findByMeasureDefinitionIdOrderByCreatedAtDesc(measureDefinitionId);
    }

    @Transactional(readOnly = true)
    public List<MeasureReportEntity> getReportsByMeasureName(String measureName) {
        return repository.findByMeasureNameOrderByCreatedAtDesc(measureName);
    }

    @Transactional(readOnly = true)
    public List<MeasureReportEntity> getReportsByMeasureNameOrderByPeriod(String measureName) {
        return repository.findByMeasureNameOrderByPeriodStartAsc(measureName);
    }

    @Transactional(readOnly = true)
    public List<MeasureReportEntity> getReportsForPeriod(String measureName, LocalDate periodStart, LocalDate periodEnd) {
        List<MeasureReportEntity> exact = repository.findByMeasureNameAndPeriodStartAndPeriodEnd(measureName, periodStart, periodEnd);
        if (!exact.isEmpty()) {
            return exact;
        }
        return repository.findByMeasureNameAndPeriodOverlap(measureName, periodStart, periodEnd);
    }

    @Transactional(readOnly = true)
    public List<MeasureReportEntity> getReportsForPeriodById(Long measureDefinitionId, LocalDate periodStart, LocalDate periodEnd) {
        List<MeasureReportEntity> exact = repository.findByMeasureDefinitionIdAndPeriodStartAndPeriodEnd(measureDefinitionId, periodStart, periodEnd);
        if (!exact.isEmpty()) {
            return exact;
        }
        return repository.findByMeasureDefinitionIdAndPeriodOverlap(measureDefinitionId, periodStart, periodEnd);
    }

    @Transactional(readOnly = true)
    public List<MeasureReportEntity> getReportsByMeasureIdOrderByPeriod(Long measureDefinitionId) {
        return repository.findByMeasureDefinitionIdOrderByPeriodStartAsc(measureDefinitionId);
    }

    @Transactional(readOnly = true)
    public Optional<MeasureReportEntity> getReport(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public void deleteReport(Long id) {
        repository.deleteById(id);
        log.info("Deleted measure report: {}", id);
    }
}
