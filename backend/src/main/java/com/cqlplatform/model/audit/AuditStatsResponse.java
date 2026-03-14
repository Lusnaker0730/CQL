package com.cqlplatform.model.audit;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AuditStatsResponse {
    private long totalEventsToday;
    private long totalEventsWeek;
    private long totalEventsMonth;
    private long phiAccessCount;
    private long failedLoginAttempts;
    private long activeUsersToday;
    private Map<String, Long> actionCounts;
    private List<UserActivitySummary> topUsers;
    private List<DailyActivityCount> dailyActivity;
}
