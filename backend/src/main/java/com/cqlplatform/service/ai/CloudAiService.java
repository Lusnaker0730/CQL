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
@Conditional(AiProviderCondition.Cloud.class)
public class CloudAiService implements CqlFixService {

    private static final int KB_TOP_K = 3;

    private final AiProperties properties;
    private final CqlKnowledgeBase knowledgeBase;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CloudAiService(AiProperties properties, CqlKnowledgeBase knowledgeBase) {
        this.properties = properties;
        this.knowledgeBase = knowledgeBase;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(properties.getCloudTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "cloud";
    }

    @Override
    public String getModelName() {
        return properties.getCloudModel();
    }

    @Override
    @CircuitBreaker(name = "cloudAiService", fallbackMethod = "suggestFixFallback")
    @Retry(name = "cloudAiService")
    public CqlFixSuggestionResponse suggestFix(String cql, CqlError error) {
        String apiKey = properties.getCloudApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return CqlFixSuggestionResponse.builder()
                    .success(false)
                    .errorMessage("Cloud AI API key is not configured")
                    .model(properties.getCloudModel())
                    .build();
        }

        String prompt = CqlFixPromptHelper.buildPrompt(cql, error);
        List<KnowledgeEntry> relevant = knowledgeBase.findRelevant(error.getMessage(), cql, KB_TOP_K);
        String systemPrompt = CqlFixPromptHelper.buildSystemPrompt(relevant);
        if (!relevant.isEmpty()) {
            log.debug("AI fix — matched {} knowledge entries: {}", relevant.size(),
                    relevant.stream().map(KnowledgeEntry::id).toList());
        }

        Map<String, Object> requestBody = Map.of(
                "model", properties.getCloudModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.1,
                "max_tokens", 2048
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Azure OpenAI uses "api-key" header; others use "Authorization: Bearer"
        String apiUrl = properties.getCloudApiUrl();
        if (apiUrl.contains(".openai.azure.com")) {
            headers.set("api-key", apiKey);
        } else {
            headers.setBearerAuth(apiKey);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.info("Calling Cloud AI API: url={}, model={}, error=line {}:{} {}",
                apiUrl, properties.getCloudModel(),
                error.getStartLine(), error.getStartColumn(), error.getMessage());

        String responseBody = restTemplate.postForObject(apiUrl, request, String.class);

        try {
            JsonNode json = objectMapper.readTree(responseBody);
            String responseText = json.path("choices").path(0).path("message").path("content").asText("");
            log.info("Cloud AI response ({} chars):\n{}", responseText.length(), responseText);

            String explanation = CqlFixPromptHelper.extractExplanation(responseText);
            String suggestedCql = CqlFixPromptHelper.extractCodeBlock(responseText);
            log.info("Parsed — explanation: [{}], suggestedCql length: {}",
                    explanation, suggestedCql != null ? suggestedCql.length() : "null");

            if (suggestedCql == null || suggestedCql.isBlank()) {
                return CqlFixSuggestionResponse.builder()
                        .success(false)
                        .errorMessage("AI returned no code suggestion")
                        .model(properties.getCloudModel())
                        .build();
            }

            return CqlFixSuggestionResponse.builder()
                    .success(true)
                    .explanation(explanation)
                    .suggestedCql(suggestedCql)
                    .model(properties.getCloudModel())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Cloud AI response", e);
            return CqlFixSuggestionResponse.builder()
                    .success(false)
                    .errorMessage("Failed to parse AI response")
                    .model(properties.getCloudModel())
                    .build();
        }
    }

    @SuppressWarnings("unused")
    private CqlFixSuggestionResponse suggestFixFallback(String cql, CqlError error, Throwable t) {
        log.warn("Circuit breaker fallback for Cloud AI suggestFix: {}", t.getMessage());
        return CqlFixSuggestionResponse.builder()
                .success(false)
                .errorMessage("AI suggestion service temporarily unavailable")
                .model(properties.getCloudModel())
                .build();
    }
}
