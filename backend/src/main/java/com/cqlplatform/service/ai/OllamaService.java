package com.cqlplatform.service.ai;

import com.cqlplatform.config.AiProperties;
import com.cqlplatform.config.AiProviderCondition;
import com.cqlplatform.model.CqlFixSuggestionResponse;
import com.cqlplatform.model.CqlTranslationResponse.CqlError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Conditional(AiProviderCondition.Ollama.class)
public class OllamaService implements CqlFixService {

    private static final int KB_TOP_K = 3;

    private final AiProperties properties;
    private final CqlKnowledgeBase knowledgeBase;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaService(AiProperties properties, CqlKnowledgeBase knowledgeBase) {
        this.properties = properties;
        this.knowledgeBase = knowledgeBase;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(properties.getOllamaTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public String getModelName() {
        return properties.getOllamaModel();
    }

    @Override
    @CircuitBreaker(name = "ollamaService", fallbackMethod = "suggestFixFallback")
    @Retry(name = "ollamaService")
    public CqlFixSuggestionResponse suggestFix(String cql, CqlError error) {
        String prompt = CqlFixPromptHelper.buildPrompt(cql, error);
        List<KnowledgeEntry> relevant = knowledgeBase.findRelevant(error.getMessage(), cql, KB_TOP_K);
        String systemPrompt = CqlFixPromptHelper.buildSystemPrompt(relevant);
        if (!relevant.isEmpty()) {
            log.debug("AI fix — matched {} knowledge entries: {}", relevant.size(),
                    relevant.stream().map(KnowledgeEntry::id).toList());
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.getOllamaModel(),
                "system", systemPrompt,
                "prompt", prompt,
                "stream", false,
                "options", Map.of(
                        "temperature", 0.1,
                        "num_predict", 2048
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        String url = properties.getOllamaUrl() + "/api/generate";
        log.info("Calling Ollama API: model={}, error=line {}:{} {}", properties.getOllamaModel(),
                error.getStartLine(), error.getStartColumn(), error.getMessage());
        log.debug("Ollama prompt:\n{}", prompt);

        String responseBody = restTemplate.postForObject(url, request, String.class);

        try {
            JsonNode json = objectMapper.readTree(responseBody);
            String responseText = json.path("response").asText("");
            log.info("Ollama raw response ({} chars):\n{}", responseText.length(), responseText);

            String explanation = CqlFixPromptHelper.extractExplanation(responseText);
            String suggestedCql = CqlFixPromptHelper.extractCodeBlock(responseText);
            log.info("Parsed — explanation: [{}], suggestedCql length: {}",
                    explanation, suggestedCql != null ? suggestedCql.length() : "null");

            if (suggestedCql == null || suggestedCql.isBlank()) {
                return CqlFixSuggestionResponse.builder()
                        .success(false)
                        .errorMessage("AI returned no code suggestion")
                        .model(properties.getOllamaModel())
                        .build();
            }

            return CqlFixSuggestionResponse.builder()
                    .success(true)
                    .explanation(explanation)
                    .suggestedCql(suggestedCql)
                    .model(properties.getOllamaModel())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Ollama response", e);
            return CqlFixSuggestionResponse.builder()
                    .success(false)
                    .errorMessage("Failed to parse AI response")
                    .model(properties.getOllamaModel())
                    .build();
        }
    }

    @SuppressWarnings("unused")
    private CqlFixSuggestionResponse suggestFixFallback(String cql, CqlError error, Throwable t) {
        log.warn("Circuit breaker fallback for suggestFix: {}", t.getMessage());
        return CqlFixSuggestionResponse.builder()
                .success(false)
                .errorMessage("AI suggestion service temporarily unavailable")
                .model(properties.getOllamaModel())
                .build();
    }
}
