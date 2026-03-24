package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.exception.FhirServerUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles SMART Backend Services (OAuth 2.0 client_credentials + JWT assertion) token exchange.
 * <p>
 * Flow: build RS384-signed JWT assertion → POST to token endpoint → cache access_token.
 */
@Service
@Slf4j
public class SmartBackendTokenService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TOKEN_SAFETY_MARGIN_SECONDS = 60;
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 300;
    private static final long JWT_ASSERTION_LIFETIME_SECONDS = 300;

    private final RestTemplate restTemplate;
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ReentrantLock> connectionLocks = new ConcurrentHashMap<>();

    public SmartBackendTokenService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Returns a valid access token for the given SMART Backend Services connection.
     * Fetches a new token if none is cached or the cached one is expired.
     * Uses per-connection locking so concurrent fetches for different connections don't block each other.
     */
    public String getAccessToken(EhrConnectionEntity connection) {
        CachedToken cached = tokenCache.get(connection.getId());
        if (cached != null && !cached.isExpired()) {
            return cached.accessToken;
        }

        ReentrantLock lock = connectionLocks.computeIfAbsent(connection.getId(), k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check after acquiring lock
            cached = tokenCache.get(connection.getId());
            if (cached != null && !cached.isExpired()) {
                return cached.accessToken;
            }

            CachedToken newToken = fetchAccessToken(connection);
            tokenCache.put(connection.getId(), newToken);
            return newToken.accessToken;
        } finally {
            lock.unlock();
        }
    }

    private CachedToken fetchAccessToken(EhrConnectionEntity connection) {
        try {
            JsonNode creds = MAPPER.readTree(connection.getCredentials());
            String clientId = requiredField(creds, "clientId", "Client ID");
            String privateKeyPem = requiredField(creds, "privateKey", "Private Key");
            String scopes = creds.path("scopes").asText("");

            String tokenEndpoint = connection.getTokenEndpoint();
            if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
                throw new IllegalStateException("Token endpoint not configured for connection: " + connection.getId());
            }

            RSAPrivateKey privateKey = parsePrivateKey(privateKeyPem);
            String assertion = buildJwtAssertion(clientId, tokenEndpoint, privateKey);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
            body.add("client_assertion", assertion);
            if (!scopes.isBlank()) {
                body.add("scope", scopes);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    tokenEndpoint, HttpMethod.POST, request, String.class);

            JsonNode tokenResponse = MAPPER.readTree(response.getBody());
            JsonNode accessTokenNode = tokenResponse.path("access_token");
            if (accessTokenNode.isMissingNode() || accessTokenNode.asText().isBlank()) {
                throw new IllegalStateException("Token endpoint did not return an access_token");
            }
            String accessToken = accessTokenNode.asText();
            long expiresIn = tokenResponse.path("expires_in").asLong(DEFAULT_TOKEN_LIFETIME_SECONDS);

            log.info("Obtained SMART Backend Services access token for connection '{}' (expires in {}s)",
                    connection.getName(), expiresIn);

            return new CachedToken(accessToken,
                    Instant.now().plusSeconds(expiresIn - TOKEN_SAFETY_MARGIN_SECONDS));

        } catch (Exception e) {
            tokenCache.remove(connection.getId());
            throw new FhirServerUnavailableException(
                    "Failed to obtain SMART Backend Services access token for connection '"
                            + connection.getName() + "': " + e.getMessage(), e);
        }
    }

    private static String requiredField(JsonNode node, String fieldName, String displayName) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.asText().isBlank()) {
            throw new IllegalArgumentException(displayName + " is required in SMART Backend Services credentials");
        }
        return field.asText();
    }

    private String buildJwtAssertion(String clientId, String tokenEndpoint, RSAPrivateKey privateKey)
            throws JOSEException {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(clientId)
                .subject(clientId)
                .audience(tokenEndpoint)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(JWT_ASSERTION_LIFETIME_SECONDS)))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS384)
                .type(JOSEObjectType.JWT)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    /**
     * Parses a PEM-encoded RSA private key using nimbus-jose-jwt.
     * Supports both PKCS#8 and PKCS#1 formats.
     */
    static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            JWK jwk = JWK.parseFromPEMEncodedObjects(pem);
            if (!(jwk instanceof RSAKey rsaKey)) {
                throw new IllegalArgumentException("PEM does not contain an RSA key");
            }
            return rsaKey.toRSAPrivateKey();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid RSA private key PEM: " + e.getMessage(), e);
        }
    }

    /**
     * Evicts cached token for a specific connection (e.g., when credentials are updated or deleted).
     */
    public void evictToken(Long connectionId) {
        tokenCache.remove(connectionId);
        connectionLocks.remove(connectionId);
    }

    private static class CachedToken {
        final String accessToken;
        final Instant expiresAt;

        CachedToken(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
