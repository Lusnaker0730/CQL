package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedDashboardData {
    private int totalMeasures;
    private Map<String, Integer> byStatus;
    private Map<String, Integer> byScoring;
    private Map<String, Double> departmentScores;
    private List<ThresholdAlert> alerts;
    private List<TrendDataPoint> recentTrends;
    private List<DashboardEvaluation> recentEvaluations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        private String period;
        private String measureName;
        private Long measureId;
        private Double score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardEvaluation {
        private Long id;
        private String measureName;
        private Double score;
        private String status;
        private String department;
        private String createdAt;
    }
}
