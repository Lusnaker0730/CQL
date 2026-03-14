package com.cqlplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "okta")
public class OktaProperties {

    private boolean enabled = false;
    private String clientId = "";
    private String clientSecret = "";
    private String issuer = "";

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
