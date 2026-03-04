package com.cqlplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitProperties {
    private boolean enabled = true;
    private int requestsPerMinute = 60;

    // Per-tier IP limits (RPM)
    private int translateRpm = 20;
    private int executeRpm = 10;
    private int fixSuggestionRpm = 5;
    private int libraryReadRpm = 120;

    // Per-user limits (RPM)
    private boolean userLimitEnabled = true;
    private int userTranslateRpm = 15;
    private int userExecuteRpm = 8;
    private int userFixSuggestionRpm = 3;
    private int userDefaultRpm = 40;

    // Payload weighting thresholds (bytes) for /translate
    private int payloadTier2 = 10_240;    // 10 KB -> 2 tokens
    private int payloadTier3 = 51_200;    // 50 KB -> 3 tokens
    private int payloadTier4 = 204_800;   // 200 KB -> 5 tokens
}
