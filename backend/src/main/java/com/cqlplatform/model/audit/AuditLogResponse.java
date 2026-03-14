package com.cqlplatform.model.audit;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuditLogResponse {
    private List<AuditLogEntry> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
