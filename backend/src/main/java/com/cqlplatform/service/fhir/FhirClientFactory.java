package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized FHIR client creation with standard logging interceptors.
 * Caches unauthenticated clients per URL (10-minute TTL) to avoid repeated client creation.
 */
@Component
@Slf4j
public class FhirClientFactory {

    private final FhirContext fhirContext;

    private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes
    private static final int MAX_CACHE_SIZE = 50;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, CachedClient> clientCache = new ConcurrentHashMap<>();

    @Value("${fhir.server.url:http://hapi-fhir:8080/fhir}")
    private String defaultFhirServerUrl;

    public FhirClientFactory(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    /**
     * Returns a cached FHIR client with logging interceptors for the given URL.
     * Falls back to the default FHIR server URL if null is provided.
     */
    public IGenericClient createClient(String fhirServerUrl) {
        String serverUrl = fhirServerUrl != null ? fhirServerUrl : defaultFhirServerUrl;
        CachedClient cached = clientCache.get(serverUrl);
        if (cached != null && !cached.isExpired()) {
            return cached.client;
        }

        log.debug("Creating FHIR Client for URL: {}", serverUrl);
        IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);
        registerLoggingInterceptor(client);

        evictIfNeeded();
        clientCache.put(serverUrl, new CachedClient(client));
        return client;
    }

    /**
     * Returns a cached plain FHIR client without interceptors.
     */
    public IGenericClient createPlainClient(String fhirServerUrl) {
        String serverUrl = fhirServerUrl != null ? fhirServerUrl : defaultFhirServerUrl;
        String cacheKey = serverUrl + "::plain";
        CachedClient cached = clientCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.client;
        }

        IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);
        evictIfNeeded();
        clientCache.put(cacheKey, new CachedClient(client));
        return client;
    }

    /**
     * Creates a FRESH (non-cached) authenticated FHIR client based on the EHR connection's auth config.
     * Must not reuse cached clients — adding interceptors to a cached client leaks credentials
     * between connections and accumulates interceptors on every call.
     */
    public IGenericClient createAuthenticatedClient(EhrConnectionEntity connection) {
        String serverUrl = connection.getFhirServerUrl() != null ? connection.getFhirServerUrl() : defaultFhirServerUrl;
        log.debug("Creating authenticated FHIR Client for URL: {}", serverUrl);
        IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);
        registerLoggingInterceptor(client);

        if ("basic".equals(connection.getAuthType()) && connection.getCredentials() != null) {
            try {
                var creds = MAPPER.readTree(connection.getCredentials());
                String username = creds.has("username") ? creds.get("username").asText() : "";
                String password = creds.has("password") ? creds.get("password").asText() : "";
                client.registerInterceptor(new BasicAuthInterceptor(username, password));
            } catch (Exception e) {
                log.warn("Failed to parse basic auth credentials for connection {}", connection.getId(), e);
            }
        } else if ("bearer".equals(connection.getAuthType()) && connection.getCredentials() != null) {
            try {
                var creds = MAPPER.readTree(connection.getCredentials());
                String token = creds.has("token") ? creds.get("token").asText() : "";
                client.registerInterceptor(new BearerTokenAuthInterceptor(token));
            } catch (Exception e) {
                log.warn("Failed to parse bearer token credentials for connection {}", connection.getId(), e);
            }
        }

        return client;
    }

    private static void registerLoggingInterceptor(IGenericClient client) {
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLogRequestSummary(true);
        loggingInterceptor.setLogResponseSummary(true);
        client.registerInterceptor(loggingInterceptor);
    }

    public String getDefaultFhirServerUrl() {
        return defaultFhirServerUrl;
    }

    /**
     * Evicts expired entries first; if still over MAX_CACHE_SIZE, removes the oldest entry.
     */
    private void evictIfNeeded() {
        // Remove expired entries
        clientCache.entrySet().removeIf(e -> e.getValue().isExpired());

        // If still over limit, remove the oldest entry
        while (clientCache.size() >= MAX_CACHE_SIZE) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, CachedClient> entry : clientCache.entrySet()) {
                if (entry.getValue().createdAt < oldestTime) {
                    oldestTime = entry.getValue().createdAt;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) {
                clientCache.remove(oldestKey);
            } else {
                break;
            }
        }
    }

    private static class CachedClient {
        final IGenericClient client;
        final long createdAt;

        CachedClient(IGenericClient client) {
            this.client = client;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }
}
