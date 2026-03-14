package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsServiceAnalyticsDTO {

    private String serviceId;
    private long invocationCount;
    private long errorCount;
    private double avgResponseTimeMs;
    private double errorRate;
    private LocalDateTime lastInvokedAt;
    private long feedbackAcceptedCount;
    private long feedbackOverriddenCount;
}
