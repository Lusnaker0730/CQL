package com.cqlplatform.model.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuditLogSearchRequest {
    @Size(max = 100)
    private String username;

    @Size(max = 100)
    private String action;

    @Size(max = 100)
    private String resourceType;

    @Size(max = 20)
    private String startDate;

    @Size(max = 20)
    private String endDate;

    private Integer statusCode;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(200)
    private int size = 20;
}
