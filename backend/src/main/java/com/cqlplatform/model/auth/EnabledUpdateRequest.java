package com.cqlplatform.model.auth;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnabledUpdateRequest {
    @NotNull
    private Boolean enabled;
}
