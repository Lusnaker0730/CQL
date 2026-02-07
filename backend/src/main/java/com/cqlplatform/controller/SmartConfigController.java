package com.cqlplatform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "SMART on FHIR", description = "SMART App Launch Framework endpoints")
public class SmartConfigController {

    @Value("${smart.issuer:#{null}}")
    private String issuer;

    @Value("${smart.authorization-endpoint:#{null}}")
    private String authorizationEndpoint;

    @Value("${smart.token-endpoint:#{null}}")
    private String tokenEndpoint;

    @GetMapping("/.well-known/smart-configuration")
    @Operation(summary = "SMART Configuration", description = "SMART on FHIR well-known configuration endpoint")
    public ResponseEntity<Map<String, Object>> smartConfiguration() {
        return ResponseEntity.ok(Map.of(
                "issuer", issuer != null ? issuer : "",
                "authorization_endpoint", authorizationEndpoint != null ? authorizationEndpoint : "",
                "token_endpoint", tokenEndpoint != null ? tokenEndpoint : "",
                "capabilities", List.of(
                        "launch-ehr",
                        "launch-standalone",
                        "client-public",
                        "client-confidential-symmetric",
                        "context-ehr-patient",
                        "context-standalone-patient",
                        "permission-offline",
                        "permission-patient",
                        "permission-user",
                        "sso-openid-connect"
                ),
                "grant_types_supported", List.of(
                        "authorization_code",
                        "client_credentials"
                ),
                "code_challenge_methods_supported", List.of("S256"),
                "scopes_supported", List.of(
                        "openid",
                        "fhirUser",
                        "launch",
                        "launch/patient",
                        "patient/*.read",
                        "user/*.read",
                        "offline_access"
                ),
                "response_types_supported", List.of("code"),
                "token_endpoint_auth_methods_supported", List.of(
                        "client_secret_basic",
                        "client_secret_post",
                        "private_key_jwt"
                )
        ));
    }
}
