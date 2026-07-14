package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "batch_import_job")
@Data
public class BatchImportJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(nullable = false, length = 20)
    private String status = "pending"; // pending, running, completed, failed, cancelled

    @Column(name = "total_patients", nullable = false)
    private int totalPatients;

    @Column(name = "completed_count", nullable = false)
    private int completedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "patient_ids", nullable = false, columnDefinition = "TEXT")
    private String patientIds; // JSON array of patient IDs

    @Column(name = "measure_id")
    private Long measureId;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary; // JSON summary of import results

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private boolean cancelled;

    /**
     * Tenant (clinic) this row belongs to — the isolation boundary (Phase 2, V64 / #698).
     * Foundation only: nullable, existing rows backfilled to the default tenant; the
     * per-table enforcement PR assigns it server-side and scopes the reads.
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id")
    private Long tenantId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
