package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsServiceDefinition {
    private String hook;
    private String title;
    private String description;
    private String id;
    private Map<String, PrefetchTemplate> prefetch;
    private String usageRequirements;

    @Data
    @Builder
    public static class PrefetchTemplate {
        private String query;
    }
}
