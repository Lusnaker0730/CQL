package com.cqlplatform.model.request;

import com.cqlplatform.security.NoXss;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowActionRequest {
    @Size(max = 1000)
    @NoXss
    private String reason;
}
