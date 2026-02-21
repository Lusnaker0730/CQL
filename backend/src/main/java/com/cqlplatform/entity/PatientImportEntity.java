package com.cqlplatform.entity;

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

    @Column(name = "patient_fhir_id", nullable = false, length = 200)
    private String patientFhirId;

    @Column(name = "patient_identifier", length = 200)
    private String patientIdentifier;

    @Column(name = "patient_name", length = 500)
    private String patientName;

    @Column(name = "resource_count")
    private int resourceCount;

    @Column(name = "bundle_json")
    @Lob
    private String bundleJson;

    @Column(name = "target_measure_id")
    private Long targetMeasureId;

    @Column(name = "target_test_case_id")
    private Long targetTestCaseId;

    @Column(name = "imported_by", length = 100)
    private String importedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
