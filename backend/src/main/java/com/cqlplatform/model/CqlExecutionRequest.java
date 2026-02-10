package com.cqlplatform.model;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class CqlExecutionRequest {
    @NotBlank(message = "CQL code or library ID is required")
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cql;

    private String libraryId;
    private String patientId;
    private String contextType = "Patient";
    private Map<String, Object> parameters;
    private String fhirServerUrl;
    private boolean debugMode = false;
    private String[] expressionNames;
}
