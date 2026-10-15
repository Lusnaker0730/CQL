package com.cqlplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PAT-262 — a user asked to review a measure, and their decision in the current review round.
 * {@code decision} is {@code pending} until the reviewer records {@code approved} or
 * {@code changes-requested}; submit-for-review resets it to {@code pending}.
 */
@Entity
@Table(name = "measure_reviewer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReviewerEntity {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String CHANGES_REQUESTED = "changes-requested";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_id", nullable = false)
    private Long measureId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @Builder.Default
    @Column(name = "decision", nullable = false, length = 20)
    private String decision = PENDING;

    @Column(name = "decision_comment", length = 2000)
    private String decisionComment;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) assignedAt = LocalDateTime.now();
        if (decision == null) decision = PENDING;
    }
}
