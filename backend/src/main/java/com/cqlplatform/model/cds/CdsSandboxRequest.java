package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank
    @Size(max = 100)
    private String serviceId;

    @NotBlank
    @Size(max = 100)
    private String hook;
    @Size(max = 200)
    private String hookInstance;

    @Valid
    private CdsRequest.CdsContext context;
    private Map<String, Object> testData;
    private Object draftOrders;
}
