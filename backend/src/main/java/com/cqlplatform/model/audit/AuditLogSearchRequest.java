package com.cqlplatform.model.audit;

import lombok.Data;

@Data
public class AuditLogSearchRequest {
    private String username;
    private String action;
    private String resourceType;
    private String startDate;
    private String endDate;
    private Integer statusCode;
    private int page = 0;
    private int size = 20;
}
