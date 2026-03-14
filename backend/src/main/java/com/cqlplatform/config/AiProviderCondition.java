package com.cqlplatform.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Custom Spring Condition for selecting the AI provider bean.
 * Supports backward compatibility with the legacy {@code ollama.enabled} property.
 */
public abstract class AiProviderCondition implements Condition {

    protected abstract String targetProvider();

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String provider = context.getEnvironment().getProperty("ai.provider", "").trim().toLowerCase();

        if (!provider.isEmpty() && !"none".equals(provider)) {
            return targetProvider().equals(provider);
        }

        // Backward compatibility: if ai.provider is unset/none, check legacy ollama.enabled
        boolean ollamaEnabled = "true".equalsIgnoreCase(
                context.getEnvironment().getProperty("ollama.enabled", "false"));
        if (ollamaEnabled) {
            return "ollama".equals(targetProvider());
        }

        return false;
    }

    public static class Ollama extends AiProviderCondition {
        @Override
        protected String targetProvider() {
            return "ollama";
        }
    }

    public static class Cloud extends AiProviderCondition {
        @Override
        protected String targetProvider() {
            return "cloud";
        }
    }
}
