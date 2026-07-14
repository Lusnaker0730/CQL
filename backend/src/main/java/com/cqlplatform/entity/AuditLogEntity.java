package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "phi_access", nullable = false)
    private boolean phiAccess;

    @Column(name = "query_parameters", length = 2000)
    private String queryParameters;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "patient_fhir_id", length = 200)
    private String patientFhirId;

    @Column(name = "connection_name", length = 200)
    private String connectionName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
