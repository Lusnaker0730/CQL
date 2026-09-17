package com.cqlplatform.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCache;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CqlConfig {

    @Bean
    public FhirContext fhirContext(
            @Value("${fhir.client.pool-max-total:50}") int poolMaxTotal,
            @Value("${fhir.client.pool-default-per-route:30}") int poolDefaultPerRoute,
            @Value("${fhir.client.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${fhir.client.socket-timeout-ms:15000}") int socketTimeout,
            @Value("${fhir.client.connection-request-timeout-ms:5000}") int connectionRequestTimeout) {
        FhirContext ctx = FhirContext.forR4();
        ApacheRestfulClientFactory clientFactory = new ApacheRestfulClientFactory(ctx);
        clientFactory.setPoolMaxTotal(poolMaxTotal);
        clientFactory.setPoolMaxPerRoute(poolDefaultPerRoute);
        clientFactory.setConnectTimeout(connectTimeout);
        clientFactory.setSocketTimeout(socketTimeout);
        clientFactory.setConnectionRequestTimeout(connectionRequestTimeout);
        ctx.setRestfulClientFactory(clientFactory);
        return ctx;
    }

    /**
     * ObjectMapper without blanket XSS string deserializer.
     * <p>
     * The previous {@code XssStringDeserializer} silently stripped regex-matched patterns
     * from ALL JSON string fields, which: (a) was bypassable via encoding tricks, and
     * (b) corrupted legitimate clinical data containing patterns like "eval" or angle brackets.
     * <p>
     * XSS prevention is now handled by:
     * <ul>
     *   <li>React auto-escaping on the frontend (primary defense)</li>
     *   <li>{@code @NoXss} field-level validation on DTOs</li>
     *   <li>{@code ExpressionTreeValidator} / {@code EcqmExpressionTreeValidator} for tree data</li>
     *   <li>{@code XssFilter} for query params and headers (HTML-encodes, doesn't strip)</li>
     * </ul>
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // PAT-117: tolerate unknown JSON fields during deserialization. Frontend / older
        // API clients may send fields (e.g. `currentUser`) that the server-side DTO has
        // since removed; strict rejection turns into a 500 via HttpMessageNotReadableException
        // and breaks legitimate flows (submit-for-review, lock, share etc all 500'd).
        // We still validate required fields via @Valid / @NotNull; extras are ignored.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCache("valueSets", 2000, 2, TimeUnit.HOURS),
                buildCache("codeValidation", 2000, 1, TimeUnit.HOURS),
                buildCache("codeLookup", 1000, 2, TimeUnit.HOURS),
                buildCache("cqlValidation", 500, 30, TimeUnit.MINUTES),
                buildCache("cqlTranslation", 300, 30, TimeUnit.MINUTES),
                buildCache("vsacValueSets", 200, 4, TimeUnit.HOURS),
                buildCache("codeSearch", 500, 1, TimeUnit.HOURS)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, int maxSize, long duration, TimeUnit unit) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(duration, unit)
                .recordStats()
                .build());
    }
}
