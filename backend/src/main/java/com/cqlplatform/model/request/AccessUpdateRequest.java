package com.cqlplatform.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AccessUpdateRequest {
    @NotBlank
    @Pattern(regexp = "private|shared|public")
    private String accessLevel;
}
