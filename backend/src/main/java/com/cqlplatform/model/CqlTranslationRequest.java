package com.cqlplatform.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CqlTranslationRequest {
    @NotBlank(message = "CQL code is required")
    private String cql;

    private boolean enableAnnotations = true;
    private boolean enableLocators = true;
    private boolean enableResultTypes = true;
    private boolean validateUnits = true;
}
