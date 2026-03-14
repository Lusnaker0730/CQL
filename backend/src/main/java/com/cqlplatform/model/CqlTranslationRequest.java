package com.cqlplatform.model;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CqlTranslationRequest {
    @NotBlank(message = "CQL code is required")
    @Size(max = 512_000, message = "CQL content must be at most 512 KB")
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cql;

    private boolean enableAnnotations = true;
    private boolean enableLocators = true;
    private boolean enableResultTypes = true;
    private boolean validateUnits = true;
}
