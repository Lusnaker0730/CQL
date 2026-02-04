package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class CdsRequest {
    private String hook;
    private String hookInstance;
    private String fhirServer;
    private String fhirAuthorization;
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
