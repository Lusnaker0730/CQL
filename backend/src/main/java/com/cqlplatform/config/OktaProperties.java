package com.cqlplatform.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "okta")
@Slf4j
public class OktaProperties {

    private boolean enabled = false;
    private String clientId = "";
    private String clientSecret = "";
    /** Issuer base URL (e.g. {@code https://example.okta.com/oauth2/default}). */
    @Pattern(regexp = "^$|^https?://.+", message = "okta.issuer must be empty or a valid http(s) URL")
    private String issuer = "";

    /**
     * PAT-150 — fail fast on misconfiguration. {@code @ConfigurationProperties}
     * binding tolerates blank values silently; without this check, an admin who
     * forgets to set {@code OKTA_CLIENT_SECRET} sees a successful startup and
     * only discovers the misconfiguration when the first user tries to log in
     * via Okta and gets a cryptic OIDC failure. Bean Validation can't express
     * "required when {@code enabled=true}" conditionally, so we enforce it
     * here at PostConstruct time.
     */
    @PostConstruct
    void validate() {
        if (!enabled) {
            return;
        }
        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()
                || issuer == null || issuer.isBlank()) {
            throw new IllegalStateException(
                    "okta.enabled=true requires okta.client-id, okta.client-secret, and okta.issuer "
                            + "to be set (typically via OKTA_CLIENT_ID / OKTA_CLIENT_SECRET / OKTA_ISSUER env vars).");
        }
        log.info("Okta SSO enabled (issuer={}, clientId={})", issuer, clientId);
    }

    public String getTokenEndpoint() {
        return issuer + "/v1/token";
    }

    public String getJwksUri() {
        return issuer + "/v1/keys";
    }

    public String getAuthorizationEndpoint() {
        return issuer + "/v1/authorize";
    }
}
