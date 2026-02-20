package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureScheduleEntity;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.repository.MeasureScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledMeasureEvaluationService {

    private final MeasureScheduleRepository scheduleRepository;
    private final MeasureDefinitionService definitionService;
    private final MeasureEvaluationService evaluationService;

    @Value("${measure.scheduling.enabled:true}")
    private boolean schedulingEnabled;

    @Scheduled(fixedRate = 60000)
    public void checkAndRunSchedules() {
        if (!schedulingEnabled) return;

        List<MeasureScheduleEntity> enabledSchedules = scheduleRepository.findByEnabledTrue();
        LocalDateTime now = LocalDateTime.now();

        for (MeasureScheduleEntity schedule : enabledSchedules) {
            if (schedule.getNextRunAt() != null && schedule.getNextRunAt().isBefore(now)) {
                try {
                    log.info("Running scheduled evaluation for measure definition {}",
                            schedule.getMeasureDefinitionId());
                    runScheduledEvaluation(schedule);
                } catch (Exception e) {
                    log.error("Failed scheduled evaluation for schedule {}", schedule.getId(), e);
                    schedule.setLastRunStatus("error");
                    schedule.setLastRunAt(now);
                    updateNextRunTime(schedule);
                    scheduleRepository.save(schedule);
                }
            }
        }
    }

    private void runScheduledEvaluation(MeasureScheduleEntity schedule) {
        MeasureDefinition definition = definitionService.getById(schedule.getMeasureDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Measure definition not found: " + schedule.getMeasureDefinitionId()));

        LocalDate[] period = computePeriod(schedule.getPeriodType());

        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId(definition.getId().toString());
        request.setMeasureCql(definition.getCqlContent());
        request.setPeriodStart(period[0]);
        request.setPeriodEnd(period[1]);
        request.setFhirServerUrl(schedule.getFhirServerUrl());
        request.setReportType("summary");

        evaluationService.evaluateMeasure(request, definition.getId(), definition);

        schedule.setLastRunAt(LocalDateTime.now());
        schedule.setLastRunStatus(com.cqlplatform.model.measure.EvaluationStatusConstants.COMPLETE);
        updateNextRunTime(schedule);
        scheduleRepository.save(schedule);
    }

    private LocalDate[] computePeriod(String periodType) {
        LocalDate now = LocalDate.now();
        return switch (periodType.toLowerCase()) {
            case "quarterly" -> {
                int quarter = (now.getMonthValue() - 1) / 3;
                int prevQuarter = quarter == 0 ? 3 : quarter - 1;
                int year = quarter == 0 ? now.getYear() - 1 : now.getYear();
                LocalDate start = LocalDate.of(year, prevQuarter * 3 + 1, 1);
                LocalDate end = start.plusMonths(3).minusDays(1);
                yield new LocalDate[]{start, end};
            }
            case "yearly" -> {
                LocalDate start = LocalDate.of(now.getYear() - 1, 1, 1);
                LocalDate end = LocalDate.of(now.getYear() - 1, 12, 31);
                yield new LocalDate[]{start, end};
            }
            default -> { // monthly
                LocalDate prevMonth = now.minusMonths(1);
                LocalDate start = prevMonth.withDayOfMonth(1);
                LocalDate end = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth());
                yield new LocalDate[]{start, end};
            }
        };
    }

    private void updateNextRunTime(MeasureScheduleEntity schedule) {
        try {
            CronExpression cron = CronExpression.parse(schedule.getCronExpression());
            LocalDateTime next = cron.next(LocalDateTime.now());
            schedule.setNextRunAt(next);
        } catch (Exception e) {
            log.warn("Invalid cron expression '{}' for schedule {}", schedule.getCronExpression(), schedule.getId());
        }
    }

    // CRUD operations

    public List<MeasureScheduleEntity> getSchedulesForMeasure(Long measureDefinitionId) {
        return scheduleRepository.findByMeasureDefinitionId(measureDefinitionId);
    }

    public Optional<MeasureScheduleEntity> getScheduleById(Long scheduleId) {
        return scheduleRepository.findById(scheduleId);
    }

    @Transactional
    public MeasureScheduleEntity createSchedule(MeasureScheduleEntity schedule) {
        // Validate cron expression
        try {
            CronExpression.parse(schedule.getCronExpression());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cron expression: " + schedule.getCronExpression());
        }

        // Compute initial next run time
        updateNextRunTime(schedule);
        MeasureScheduleEntity saved = scheduleRepository.save(schedule);
        log.info("Created schedule {} for measure {}", saved.getId(), saved.getMeasureDefinitionId());
        return saved;
    }

    @Transactional
    public MeasureScheduleEntity updateSchedule(Long scheduleId, MeasureScheduleEntity update) {
        MeasureScheduleEntity existing = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        if (update.getCronExpression() != null) {
            try {
                CronExpression.parse(update.getCronExpression());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid cron expression: " + update.getCronExpression());
            }
            existing.setCronExpression(update.getCronExpression());
        }
        if (update.getFhirServerUrl() != null) {
            existing.setFhirServerUrl(update.getFhirServerUrl());
        }
        if (update.getPeriodType() != null) {
            existing.setPeriodType(update.getPeriodType());
        }
        if (update.getEnabled() != null) {
            existing.setEnabled(update.getEnabled());
        }

        updateNextRunTime(existing);
        return scheduleRepository.save(existing);
    }

    @Transactional
    public void deleteSchedule(Long scheduleId) {
        scheduleRepository.deleteById(scheduleId);
        log.info("Deleted schedule: {}", scheduleId);
    }

    public MeasureEvaluationResult triggerManually(Long scheduleId) {
        MeasureScheduleEntity schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));

        MeasureDefinition definition = definitionService.getById(schedule.getMeasureDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Measure definition not found: " + schedule.getMeasureDefinitionId()));

        LocalDate[] period = computePeriod(schedule.getPeriodType());

        MeasureEvaluationRequest request = new MeasureEvaluationRequest();
        request.setMeasureId(definition.getId().toString());
        request.setMeasureCql(definition.getCqlContent());
        request.setPeriodStart(period[0]);
        request.setPeriodEnd(period[1]);
        request.setFhirServerUrl(schedule.getFhirServerUrl());
        request.setReportType("summary");

        MeasureEvaluationResult result = evaluationService.evaluateMeasure(request, definition.getId(), definition);

        schedule.setLastRunAt(LocalDateTime.now());
        schedule.setLastRunStatus(result.getStatus());
        updateNextRunTime(schedule);
        scheduleRepository.save(schedule);

        return result;
    }
}
