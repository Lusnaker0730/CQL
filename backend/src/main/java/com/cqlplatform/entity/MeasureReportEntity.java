package com.cqlplatform.entity;

import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.LongAdder;

@Entity
@Table(name = "measure_report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class MeasureReportEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Process-wide counter of failed result_json deserializations. Exposed via
     * {@link #getDeserializationFailureCount()} for tests / health endpoints.
     *
     * <p>A non-zero value means at least one historical report is no longer loadable
     * — usually caused by a {@link MeasureEvaluationResult} schema change without a
     * corresponding data migration. Before this safeguard, deserialization failures
     * silently produced null {@code evaluationResult} and the dashboard rendered
     * empty groups. Now each failure WARN-logs with the report id so operators see
     * it in telemetry.
     */
    private static final LongAdder DESERIALIZATION_FAILURES = new LongAdder();

    public static long getDeserializationFailureCount() {
        return DESERIALIZATION_FAILURES.sum();
    }

    /** Test-only: reset the counter between runs. Not for production use. */
    public static void resetDeserializationFailureCount() {
        DESERIALIZATION_FAILURES.reset();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_definition_id")
    private Long measureDefinitionId;

    @Column(name = "measure_name", nullable = false, length = 200)
    private String measureName;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "report_type", length = 30)
    private String reportType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "scoring_type", length = 30)
    private String scoringType;

    @Column(name = "measure_score")
    private Double measureScore;

    @Column(name = "total_patients")
    private Integer totalPatients;

    @Column(name = "result_json", columnDefinition = "TEXT", nullable = false)
    private String resultJson;

    @Transient
    private MeasureEvaluationResult evaluationResult;

    @Column(name = "fhir_server_url", length = 500)
    private String fhirServerUrl;

    @Column(name = "evaluated_by", length = 100)
    private String evaluatedBy;

    @Column(name = "evaluation_duration_ms")
    private Long evaluationDurationMs;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PostLoad
    protected void onLoad() {
        if (resultJson != null && !resultJson.isBlank()) {
            try {
                evaluationResult = MAPPER.readValue(resultJson, MeasureEvaluationResult.class);
            } catch (JsonProcessingException e) {
                // Previously the failure was swallowed to null — the dashboard silently rendered
                // empty groups when the stored JSON couldn't be deserialized (usually after a
                // MeasureEvaluationResult schema change without data migration). Surface it now.
                DESERIALIZATION_FAILURES.increment();
                log.warn("Failed to deserialize measure_report.result_json for report id={}: {} ({}). "
                                + "The record is still accessible via column fields (status, measure_score, "
                                + "total_patients, period) but getEvaluationResult() will return null.",
                        id, e.getClass().getSimpleName(), e.getOriginalMessage());
                evaluationResult = null;
            }
        }
    }
}
