package com.cqlplatform.entity;

import com.cqlplatform.security.EncryptionConverter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sandbox_preset")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxPresetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant (clinic) that owns this preset — the isolation boundary (BUG-134). Assigned
     * server-side; backfilled in V68. The {@code shared = true} branch of findAccessible
     * used to cross tenants; it is now scoped by this column.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "owner_username", nullable = false, length = 100)
    private String ownerUsername;

    @Column(name = "service_id", length = 100)
    private String serviceId;

    /** FHIR patient ID used as the CDS sandbox test context. Encrypted at rest — PHI. */
    @Column(name = "patient_id", length = 100)
    @Convert(converter = EncryptionConverter.class)
    private String patientId;

    /** Prefetched FHIR resources bundled with the preset (demographics, vitals,
     *  conditions). Encrypted at rest — full clinical context. */
    @Column(name = "prefetch_json", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = EncryptionConverter.class)
    private String prefetchJson;

    @Builder.Default
    @Column(nullable = false)
    private Boolean shared = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
