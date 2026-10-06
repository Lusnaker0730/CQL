package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * PAT-253 — a measure's version lineage (MADiE's "measure set"). Every {@link MeasureDefinitionEntity}
 * belongs to exactly one set; {@code createVersionAs} keeps the set, {@code create} opens a new one.
 * History, "supersede the other active version on approve" and version-number uniqueness key on
 * the set id, so renaming a version no longer splits its lineage and an unrelated measure that
 * happens to share the name no longer joins it. {@code name} follows the latest rename and is
 * display-only.
 */
@Entity
@Table(name = "measure_set")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

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
