package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CdsRequest {
    @NotBlank
    @Size(max = 100)
    private String hook;

    @NotBlank
    @Size(max = 200)
    private String hookInstance;

    private String fhirServer;
    private String fhirAuthorization;

    @NotNull
    @Valid
    private CdsContext context;

    private Map<String, Object> prefetch;

    @Data
    public static class CdsContext {
        private String userId;
        private String patientId;
        private String encounterId;

        @JsonProperty("draftOrders")
        private Object draftOrders;

        @JsonProperty("selections")
        private Object selections;
    }
}
