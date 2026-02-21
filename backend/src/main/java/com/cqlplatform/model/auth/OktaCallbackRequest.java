package com.cqlplatform.model.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OktaCallbackRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String redirectUri;

    private String nonce;
}
