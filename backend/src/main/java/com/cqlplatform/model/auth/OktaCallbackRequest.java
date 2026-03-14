package com.cqlplatform.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OktaCallbackRequest {
    @NotBlank
    @Size(max = 2000)
    private String code;

    @NotBlank
    @Size(max = 500)
    private String redirectUri;

    @Size(max = 500)
    private String nonce;
}
