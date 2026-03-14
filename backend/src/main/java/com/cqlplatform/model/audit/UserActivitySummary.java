package com.cqlplatform.model.audit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserActivitySummary {
    private String username;
    private long eventCount;
    private String lastActivityAt;
}
