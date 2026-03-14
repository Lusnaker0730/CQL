package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cds_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdsFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @Column(name = "card_uuid", length = 100)
    private String cardUuid;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "outcome_timestamp")
    private LocalDateTime outcomeTimestamp;

    @Column(name = "override_reason_code", length = 100)
    private String overrideReasonCode;

    @Column(name = "override_reason_display", length = 500)
    private String overrideReasonDisplay;

    @Column(name = "accepted_suggestions", columnDefinition = "TEXT")
    private String acceptedSuggestions;

    @Column(name = "hook_instance", length = 100)
    private String hookInstance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
