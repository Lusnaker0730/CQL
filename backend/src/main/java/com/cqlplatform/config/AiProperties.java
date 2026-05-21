package com.cqlplatform.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ai")
@Slf4j
public class AiProperties {
    /** One of: {@code none}, {@code ollama}, {@code cloud}. */
    @Pattern(regexp = "none|ollama|cloud",
            message = "ai.provider must be one of: none, ollama, cloud")
    private String provider = "none";
    private String ollamaUrl = "http://localhost:11434";
    private String ollamaModel = "qwen2.5-coder:7b";
    @Min(value = 1, message = "ai.ollama-timeout-seconds must be >= 1")
    private int ollamaTimeoutSeconds = 120;
    private String cloudApiUrl = "https://api.openai.com/v1/chat/completions";
    private String cloudModel = "gpt-4o-mini";
    private volatile String cloudApiKey = "";
    @Min(value = 1, message = "ai.cloud-timeout-seconds must be >= 1")
    private int cloudTimeoutSeconds = 60;

    /**
     * PAT-150 — fail fast when the cloud provider is selected without a key.
     * Otherwise the first AI fix-suggestion request months after deploy throws
     * a 401 and the operator has to dig through logs to figure out the env
     * var was never set.
     */
    @PostConstruct
    void validate() {
        if ("cloud".equals(provider) && (cloudApiKey == null || cloudApiKey.isBlank())) {
            throw new IllegalStateException(
                    "ai.provider=cloud requires ai.cloud-api-key to be set "
                            + "(typically via AI_CLOUD_API_KEY env var).");
        }
        if ("ollama".equals(provider) && (ollamaUrl == null || ollamaUrl.isBlank())) {
            throw new IllegalStateException(
                    "ai.provider=ollama requires ai.ollama-url to be set.");
        }
        if (!"none".equals(provider)) {
            log.info("AI provider configured: {} ({})", provider,
                    "cloud".equals(provider) ? cloudModel : ollamaModel);
        }
    }
}
