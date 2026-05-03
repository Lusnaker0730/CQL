package com.cqlplatform.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
    @NotBlank(message = "ollama.url must not be blank")
    private String url = "http://localhost:11434";
    @NotBlank(message = "ollama.model must not be blank")
    private String model = "qwen2.5-coder:7b";
    @Min(value = 1, message = "ollama.timeout-seconds must be >= 1")
    private int timeoutSeconds = 60;
    private boolean enabled = false;
}
