package com.cqlplatform.model.request;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiKeyCreateRequest {
    @NotBlank
    @Size(max = 100)
    @NoXss
    private String name;
}
