package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cds_service_analytics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdsServiceAnalyticsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @Column(name = "invocation_count", nullable = false)
    @Builder.Default
    private Long invocationCount = 0L;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private Long errorCount = 0L;

    @Column(name = "total_response_time_ms", nullable = false)
    @Builder.Default
    private Long totalResponseTimeMs = 0L;

    @Column(name = "last_invoked_at")
    private LocalDateTime lastInvokedAt;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
