package com.cqlplatform.model;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CqlExecutionRequest {
    @NotBlank(message = "CQL code or library ID is required")
    @Size(max = 512_000, message = "CQL content must be at most 512 KB")
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cql;

    @Size(max = 128)
    private String libraryId;

    @Size(max = 128)
    private String patientId;

    @Size(max = 50)
    private String contextType = "Patient";

    private Map<String, Object> parameters;

    @Size(max = 500)
    private String fhirServerUrl;

    private boolean debugMode = false;
    private String[] expressionNames;
}
