package com.cqlplatform.entity;

import com.cqlplatform.security.EncryptionConverter;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_import")
@Data
public class PatientImportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    /** FHIR-server-assigned patient ID. Encrypted at rest — direct PHI identifier.
     *  Column is not queried by equality (see PatientImportRepository). */
    @Column(name = "patient_fhir_id", nullable = false, length = 200)
    @Convert(converter = EncryptionConverter.class)
    private String patientFhirId;

    /** Source-system identifier (e.g. MRN). Encrypted at rest. */
    @Column(name = "patient_identifier", length = 200)
    @Convert(converter = EncryptionConverter.class)
    private String patientIdentifier;

    /** Patient full name. Encrypted at rest — PII. */
    @Column(name = "patient_name", length = 500)
    @Convert(converter = EncryptionConverter.class)
    private String patientName;

    @Column(name = "resource_count")
    private int resourceCount;

    /** Complete imported FHIR bundle. Encrypted at rest — contains clinical
     *  records, observations, vitals, all associated with the patient. */
    @Column(name = "bundle_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptionConverter.class)
    private String bundleJson;

    @Column(name = "target_measure_id")
    private Long targetMeasureId;

    @Column(name = "target_test_case_id")
    private Long targetTestCaseId;

    @Column(name = "imported_by", length = 100)
    private String importedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Tenant (clinic) this row belongs to — the isolation boundary (Phase 2, V64 / #698).
     * NOT NULL since V66; assigned server-side on every write path (PAT-195..199).
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
