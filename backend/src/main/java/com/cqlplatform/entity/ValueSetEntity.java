package com.cqlplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PAT-230 — one version of one platform-owned value set (V72). {@code concepts} is the JSON code
 * list; (tenant, url, version) is unique.
 */
@Entity
@Table(name = "value_set",
        uniqueConstraints = @UniqueConstraint(name = "uq_value_set_tenant_url_version",
                columnNames = {"tenant_id", "url", "version"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValueSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "publisher", length = 255)
    private String publisher;

    /** JSON array of {system, version?, code, display?}. */
    @Column(name = "concepts", columnDefinition = "TEXT", nullable = false)
    private String concepts;

    @Column(name = "concept_count", nullable = false)
    private int conceptCount;

    /** authored | imported */
    @Column(name = "origin", nullable = false, length = 20)
    private String origin;

    /** The FHIR resource as it was imported — kept so an unchanged import exports verbatim. */
    @Column(name = "source_json", columnDefinition = "TEXT")
    private String sourceJson;

    @Column(name = "owner_username", nullable = false, length = 100)
    private String ownerUsername;

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
