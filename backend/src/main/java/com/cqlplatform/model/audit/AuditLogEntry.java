package com.cqlplatform.model.audit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditLogEntry {
    private Long id;
    private String username;
    private String method;
    private String path;
    private String resourceType;
    private String resourceId;
    private String action;
    private Integer statusCode;
    private String ipAddress;
    private String userAgent;
    private Long responseTimeMs;
    private boolean phiAccess;
    private String queryParameters;
    private String createdAt;
}
