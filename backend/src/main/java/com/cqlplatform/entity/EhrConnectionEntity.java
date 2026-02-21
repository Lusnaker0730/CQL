package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "ehr_connection")
@Data
public class EhrConnectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "fhir_server_url", nullable = false, length = 500)
    private String fhirServerUrl;

    @Column(name = "auth_type", nullable = false, length = 20)
    private String authType = "none";

    @Column(length = 2000)
    private String credentials;

    @Column(length = 100)
    private String department;

    @Column(length = 20)
    private String status = "untested";

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
