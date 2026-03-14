package com.cqlplatform.model.request;

import com.cqlplatform.security.NoXss;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LibrarySaveRequest {
    /**
     * CQL content — not sanitized because CQL legitimately contains angle brackets.
     */
    @NotBlank
    @Size(max = 512_000, message = "CQL content must be at most 512 KB")
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cql;

    @Size(max = 2000)
    @NoXss
    private String description;
}
