package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

@Data
@Builder
@Jacksonized
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
    private Object appointments;

    /** When true, response will include DebugTrace and structured error info. */
    private boolean debugMode = false;

    /** When true, resolve prefetch + check context but skip CQL execution. Implies debugMode. */
    private boolean dryRun = false;
}
