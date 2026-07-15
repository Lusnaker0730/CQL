package com.cqlplatform.model.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TenantActiveRequest {

    @NotNull
    private Boolean active;
}
