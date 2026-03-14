package com.cqlplatform.model.request;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CqlValidationRequest {
    /**
     * CQL content — not sanitized because CQL legitimately contains angle brackets.
     */
    @NotBlank
    @Size(max = 512_000, message = "CQL content must be at most 512 KB")
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cql;
}
