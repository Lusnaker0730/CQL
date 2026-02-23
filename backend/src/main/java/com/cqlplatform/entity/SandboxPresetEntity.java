package com.cqlplatform.entity;

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

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "owner_username", nullable = false, length = 100)
    private String ownerUsername;

    @Column(name = "service_id", length = 100)
    private String serviceId;

    @Column(name = "patient_id", length = 100)
    private String patientId;

    @Column(name = "prefetch_json", nullable = false, columnDefinition = "TEXT")
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
