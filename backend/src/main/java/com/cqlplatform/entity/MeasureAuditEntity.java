package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "measure_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measure_id", nullable = false)
    private Long measureId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
