package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_import")
@Data
public class FailedImportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "patient_fhir_id", nullable = false, length = 200)
    private String patientFhirId;

    @Column(name = "measure_id")
    private Long measureId;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(nullable = false, length = 20)
    private String status = "pending"; // pending, retrying, resolved, exhausted

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Tenant (clinic) this row belongs to — the isolation boundary (Phase 2, V65 / #698).
     * Assigned server-side at recordFailure; existing rows backfilled to the default tenant.
     * Note: patient_fhir_id on this table is stored UNENCRYPTED, so tenant scoping is the
     * primary containment for its PHI exposure.
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
