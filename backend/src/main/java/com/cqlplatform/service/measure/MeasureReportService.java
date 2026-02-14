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
                String desc = result.getGroups().get(0).getDescription();
                if (desc != null && desc.contains("Total Patients: ")) {
                    try {
                        String countStr = desc.substring(desc.indexOf("Total Patients: ") + 16);
                        countStr = countStr.replace(")", "").trim();
                        totalPatients = Integer.parseInt(countStr);
                    } catch (Exception ignored) {
                    }
                }
            }

            MeasureReportEntity entity = MeasureReportEntity.builder()
                    .measureDefinitionId(measureDefinitionId)
                    .measureName(result.getMeasureName() != null ? result.getMeasureName() : result.getMeasureId())
                    .status(result.getStatus())
                    .reportType(result.getReportType())
                    .periodStart(result.getPeriodStart())
                    .periodEnd(result.getPeriodEnd())
                    .scoringType("proportion")
                    .measureScore(measureScore)
                    .totalPatients(totalPatients)
                    .resultJson(resultJson)
                    .fhirServerUrl(fhirServerUrl)
                    .evaluatedBy(evaluatedBy)
                    .evaluationDurationMs(durationMs)
                    .build();

            entity = repository.save(entity);
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
        return repository.findByMeasureNameAndPeriodStartAndPeriodEnd(measureName, periodStart, periodEnd);
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
