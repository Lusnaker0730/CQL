package com.cqlplatform.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import com.cqlplatform.security.XssStringDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
            @Value("${fhir.client.pool-max-total:20}") int poolMaxTotal,
            @Value("${fhir.client.pool-default-per-route:10}") int poolDefaultPerRoute,
            @Value("${fhir.client.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${fhir.client.socket-timeout-ms:30000}") int socketTimeout) {
        FhirContext ctx = FhirContext.forR4();
        ApacheRestfulClientFactory clientFactory = new ApacheRestfulClientFactory(ctx);
        clientFactory.setPoolMaxTotal(poolMaxTotal);
        clientFactory.setPoolMaxPerRoute(poolDefaultPerRoute);
        clientFactory.setConnectTimeout(connectTimeout);
        clientFactory.setSocketTimeout(socketTimeout);
        ctx.setRestfulClientFactory(clientFactory);
        return ctx;
    }

    @Bean
    public ObjectMapper objectMapper() {
        SimpleModule xssModule = new SimpleModule("XssProtection");
        xssModule.addDeserializer(String.class, new XssStringDeserializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(xssModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
