package com.cqlplatform.model.request;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiKeyCreateRequest {
    @Size(max = 100)
    @NoXss
    private String name;
}
