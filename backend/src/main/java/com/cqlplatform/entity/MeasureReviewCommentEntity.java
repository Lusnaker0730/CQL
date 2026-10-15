package com.cqlplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PAT-262 — one review comment. A reply carries the id of the thread's root in {@code parentId}
 * (threads are one level deep); {@code section} / {@code subSection} anchor the thread to a part of
 * the measure (MADiE's mainSection / subSection); {@code resolved} lives on the root.
 */
@Entity
@Table(name = "measure_review_comment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReviewCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_id", nullable = false)
    private Long measureId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "author", nullable = false, length = 100)
    private String author;

    @Column(name = "section", length = 100)
    private String section;

    @Column(name = "sub_section", length = 200)
    private String subSection;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Builder.Default
    @Column(name = "edited", nullable = false)
    private boolean edited = false;

    @Builder.Default
    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
