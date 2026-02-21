package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "measure_threshold")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureThresholdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_definition_id", nullable = false)
    private Long measureDefinitionId;

    @Column(name = "threshold_type", nullable = false, length = 30)
    private String thresholdType; // target, warning, critical

    @Column(name = "threshold_value", nullable = false)
    private Double thresholdValue;

    @Column(name = "comparison_operator", nullable = false, length = 10)
    @Builder.Default
    private String comparisonOperator = ">=";

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

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
