package com.cqlplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private String provider = "none";
    private String ollamaUrl = "http://localhost:11434";
    private String ollamaModel = "qwen2.5-coder:7b";
    private int ollamaTimeoutSeconds = 120;
    private String cloudApiUrl = "https://api.openai.com/v1/chat/completions";
    private String cloudModel = "gpt-4o-mini";
    private volatile String cloudApiKey = "";
    private int cloudTimeoutSeconds = 60;
}
