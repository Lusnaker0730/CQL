package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReportRepository;
import com.cqlplatform.util.ContentHash;
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
    private final MeasureDefinitionRepository measureDefinitionRepository;
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

            // Capture measure provenance (PAT-095): snapshot the version + CQL/ELM
            // hashes so this report can always be linked back to the exact bytes that
            // produced it, even after the measure definition is updated or deleted.
            // Lookup is best-effort — definition may have been deleted between
            // evaluation and save; in that case we record what we have and leave the
            // rest null rather than failing the whole save.
            String measureVersion = null;
            String cqlHash = null;
            String elmHash = null;
            if (measureDefinitionId != null) {
                Optional<MeasureDefinitionEntity> defOpt = measureDefinitionRepository.findById(measureDefinitionId);
                if (defOpt.isPresent()) {
                    MeasureDefinitionEntity def = defOpt.get();
                    measureVersion = def.getVersion();
                    cqlHash = ContentHash.sha256Hex(def.getCqlContent());
                    elmHash = ContentHash.sha256Hex(def.getElmJson());
                }
            }

            MeasureReportEntity entity = MeasureReportEntity.builder()
                    .measureDefinitionId(measureDefinitionId)
                    .measureVersion(measureVersion)
                    .cqlHash(cqlHash)
                    .elmHash(elmHash)
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
