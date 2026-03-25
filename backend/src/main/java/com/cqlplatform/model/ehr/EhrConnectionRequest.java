package com.cqlplatform.model.ehr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EhrConnectionRequest {
    @NotBlank(message = "名稱不可為空")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "FHIR 伺服器 URL 不可為空")
    @Size(max = 2048)
    private String fhirServerUrl;

    @Size(max = 50)
    private String authType; // none, basic, bearer, smart_backend

    @Size(max = 10000)
    private String credentials; // JSON string, encrypted at rest

    @Size(max = 1000)
    private String description;

    @Size(max = 100)
    private String department;

    @Size(max = 500)
    private String tokenEndpoint;

    private Boolean tlsEnabled;
    private String caCertPem;
    private String clientCertPem;
    private String clientKeyPem;
    @Size(max = 10)
    private String tlsMinVersion;
    private Boolean hostnameVerification;
}
