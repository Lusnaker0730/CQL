package com.cqlplatform.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitProperties {
    private boolean enabled = true;
    @Min(value = 1, message = "rate-limit.requests-per-minute must be >= 1")
    private int requestsPerMinute = 60;

    // Per-tier IP limits (RPM)
    private int translateRpm = 20;
    private int executeRpm = 10;
    private int fixSuggestionRpm = 5;
    private int libraryReadRpm = 120;
    private int authRpm = 10;
    private int cdsInvokeRpm = 10;
    private int cdsDiscoveryRpm = 20;

    // Per-user limits (RPM)
    private boolean userLimitEnabled = true;
    private int userTranslateRpm = 15;
    private int userExecuteRpm = 8;
    private int userFixSuggestionRpm = 3;
    private int userDefaultRpm = 40;

    // Per-tenant limits (RPM) — aggregate ceiling across ALL of a clinic's users, so
    // one tenant's combined traffic cannot starve the shared platform for other tenants.
    // Set above the per-user limits (a clinic has several staff) but bounded. Only applies
    // to requests carrying a tenant (provisioned clinics); anonymous / legacy platform
    // accounts fall through to the IP + per-user layers.
    private boolean tenantLimitEnabled = true;
    private int tenantTranslateRpm = 60;
    private int tenantExecuteRpm = 32;
    private int tenantFixSuggestionRpm = 12;
    private int tenantDefaultRpm = 160;

    // Payload weighting thresholds (bytes) for /translate
    private int payloadTier2 = 10_240;    // 10 KB -> 2 tokens
    private int payloadTier3 = 51_200;    // 50 KB -> 3 tokens
    private int payloadTier4 = 204_800;   // 200 KB -> 5 tokens
}
