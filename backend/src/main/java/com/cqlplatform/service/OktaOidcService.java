package com.cqlplatform.service;

import com.cqlplatform.config.OktaProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "okta.enabled", havingValue = "true")
public class OktaOidcService {

    private final OktaProperties oktaProperties;
    private final RestTemplate restTemplate;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public OktaOidcService(OktaProperties oktaProperties) throws Exception {
        this.oktaProperties = oktaProperties;
        this.restTemplate = new RestTemplate();

        // Set up JWKS-based JWT processor for ID token verification
        JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(oktaProperties.getJwksUri()));
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
        this.jwtProcessor = new DefaultJWTProcessor<>();
        this.jwtProcessor.setJWSKeySelector(keySelector);

        log.info("OktaOidcService initialized with issuer: {}", oktaProperties.getIssuer());
    }

    public OktaUserInfo exchangeCodeForUser(String code, String redirectUri, String nonce) {
        // Exchange authorization code for tokens
        String tokenEndpoint = oktaProperties.getTokenEndpoint();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String credentials = oktaProperties.getClientId() + ":" + oktaProperties.getClientSecret();
        headers.set("Authorization", "Basic " +
                Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = restTemplate.postForObject(tokenEndpoint, request, Map.class);
        if (tokenResponse == null || !tokenResponse.containsKey("id_token")) {
            throw new RuntimeException("Failed to obtain ID token from Okta");
        }

        String idToken = (String) tokenResponse.get("id_token");

        // Validate and parse ID token
        try {
            JWTClaimsSet claims = jwtProcessor.process(idToken, null);

            // Validate issuer
            if (!oktaProperties.getIssuer().equals(claims.getIssuer())) {
                throw new RuntimeException("ID token issuer mismatch");
            }

            // Validate audience
            if (!claims.getAudience().contains(oktaProperties.getClientId())) {
                throw new RuntimeException("ID token audience mismatch");
            }

            // Validate expiration
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new RuntimeException("ID token is expired");
            }

            // Validate nonce if provided
            if (nonce != null && !nonce.isEmpty()) {
                String tokenNonce = (String) claims.getClaim("nonce");
                if (!nonce.equals(tokenNonce)) {
                    throw new RuntimeException("ID token nonce mismatch");
                }
            }

            return OktaUserInfo.builder()
                    .sub(claims.getSubject())
                    .email((String) claims.getClaim("email"))
                    .preferredUsername((String) claims.getClaim("preferred_username"))
                    .name((String) claims.getClaim("name"))
                    .build();

        } catch (Exception e) {
            log.error("Failed to validate Okta ID token", e);
            throw new RuntimeException("ID token validation failed: " + e.getMessage(), e);
        }
    }
}
