package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "fhir_subscription")
@Data
public class FhirSubscriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "fhir_subscription_id", length = 200)
    private String fhirSubscriptionId;

    @Column(nullable = false, length = 500)
    private String criteria;

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType = "rest-hook";

    @Column(name = "callback_url", nullable = false, length = 500)
    private String callbackUrl;

    @Column(nullable = false, length = 20)
    private String status = "requested";

    @Column(length = 500)
    private String reason;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "last_notification_at")
    private LocalDateTime lastNotificationAt;

    @Column(name = "notification_count", nullable = false)
    private int notificationCount;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
