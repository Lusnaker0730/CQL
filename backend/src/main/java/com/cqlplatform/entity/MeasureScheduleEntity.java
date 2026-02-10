package com.cqlplatform.entity;

import com.cqlplatform.security.NoXss;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "measure_schedule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_definition_id", nullable = false)
    private Long measureDefinitionId;

    @NotBlank
    @Size(max = 100)
    @NoXss
    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Size(max = 500)
    @Column(name = "fhir_server_url", length = 500)
    private String fhirServerUrl;

    @NotBlank
    @Pattern(regexp = "daily|weekly|monthly|quarterly|yearly")
    @Column(name = "period_type", nullable = false, length = 20)
    @Builder.Default
    private String periodType = "monthly";

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_status", length = 20)
    private String lastRunStatus;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
