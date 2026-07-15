package com.cqlplatform.entity;

import com.cqlplatform.security.EncryptionConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A clinic's self-service application (#700). On approval the platform operator
 * provisions a tenant plus its first tenant-admin account; the application row keeps
 * the audit trail (who reviewed, what was created).
 */
@Entity
@Table(name = "clinic_application")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicApplicationEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinic_name", nullable = false, length = 200)
    private String clinicName;

    /** Requested tenant code (lowercase slug); validated again at approval time. */
    @Column(name = "tenant_code", nullable = false, length = 50)
    private String tenantCode;

    @Column(name = "admin_username", nullable = false, length = 100)
    private String adminUsername;

    /** Contact/admin email — encrypted at rest, same as app_user.email. */
    @Column(name = "admin_email", nullable = false, length = 500)
    @Convert(converter = EncryptionConverter.class)
    private String adminEmail;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_tenant_id")
    private Long createdTenantId;

    @Column(name = "created_user_id")
    private Long createdUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
