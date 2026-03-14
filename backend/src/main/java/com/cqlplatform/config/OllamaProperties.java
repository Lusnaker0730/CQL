package com.cqlplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
    private String url = "http://localhost:11434";
    private String model = "qwen2.5-coder:7b";
    private int timeoutSeconds = 60;
    private boolean enabled = false;
}
