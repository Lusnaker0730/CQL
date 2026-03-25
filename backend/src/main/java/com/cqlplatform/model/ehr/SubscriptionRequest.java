package com.cqlplatform.model.ehr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubscriptionRequest {
    @NotBlank(message = "Subscription criteria is required")
    @Size(max = 500)
    private String criteria;

    @Size(max = 500)
    private String reason;
}
