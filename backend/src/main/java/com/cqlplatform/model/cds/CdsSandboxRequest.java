package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsSandboxRequest {

    private String serviceId;
    private String hook;
    private String hookInstance;
    private CdsRequest.CdsContext context;
    private Map<String, Object> testData;
}
