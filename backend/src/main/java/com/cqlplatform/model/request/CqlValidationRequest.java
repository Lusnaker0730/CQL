package com.cqlplatform.model.request;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CqlValidationRequest {
    /**
     * CQL content — not sanitized because CQL legitimately contains angle brackets.
     */
    @NotBlank
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cql;
}
