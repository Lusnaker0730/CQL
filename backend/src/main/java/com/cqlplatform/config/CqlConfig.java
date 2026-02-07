package com.cqlplatform.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

@Configuration
public class CqlConfig {

    @Value("${cql.translation.enable-annotations:true}")
    private boolean enableAnnotations;

    @Value("${cql.translation.enable-locators:true}")
    private boolean enableLocators;

    @Value("${cql.translation.enable-result-types:true}")
    private boolean enableResultTypes;

    @Value("${cql.translation.validate-units:true}")
    private boolean validateUnits;

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
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
