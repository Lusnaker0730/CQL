package com.cqlplatform.entity;

import com.cqlplatform.security.EncryptionConverter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "ehr_connection")
@Data
public class EhrConnectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "fhir_server_url", nullable = false, length = 500)
    private String fhirServerUrl;

    @Column(name = "auth_type", nullable = false, length = 50)
    private String authType = "none";

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String credentials;

    @Column(name = "token_endpoint", length = 500)
    private String tokenEndpoint;

    @Column(name = "tls_enabled")
    private boolean tlsEnabled = false;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Convert(converter = EncryptionConverter.class)
    @Column(name = "ca_cert_pem", columnDefinition = "TEXT")
    private String caCertPem;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Convert(converter = EncryptionConverter.class)
    @Column(name = "client_cert_pem", columnDefinition = "TEXT")
    private String clientCertPem;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Convert(converter = EncryptionConverter.class)
    @Column(name = "client_key_pem", columnDefinition = "TEXT")
    private String clientKeyPem;

    @Column(name = "tls_min_version", length = 10)
    private String tlsMinVersion = "TLSv1.2";

    @Column(name = "hostname_verification")
    private boolean hostnameVerification = true;

    @Column(length = 100)
    private String department;

    /**
     * Tenant (clinic) that owns this connection — the isolation boundary (Phase 2).
     * Assigned server-side on create; existing rows were backfilled to the default tenant.
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(length = 20)
    private String status = "untested";

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean active = true;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
