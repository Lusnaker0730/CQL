package com.cqlplatform.entity;

import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "measure_report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReportEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

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
                evaluationResult = null;
            }
        }
    }
}
