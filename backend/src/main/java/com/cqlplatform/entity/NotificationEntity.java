package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient", nullable = false, length = 100)
    private String recipient;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "link", length = 500)
    private String link;

    @Column(name = "is_read")
    private boolean read;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

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
