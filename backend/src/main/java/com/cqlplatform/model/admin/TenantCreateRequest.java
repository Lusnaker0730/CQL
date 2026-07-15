package com.cqlplatform.model.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantCreateRequest {

    /** Stable machine identifier — lowercase slug, immutable after creation. */
    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "code must be a lowercase slug (a-z, 0-9, -)")
    private String code;

    /** Human-readable clinic name. */
    @NotBlank
    @Size(max = 200)
    private String name;
}
