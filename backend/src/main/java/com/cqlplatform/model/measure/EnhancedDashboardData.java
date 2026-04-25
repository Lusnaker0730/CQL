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
        /**
         * FHIR scoring type of the measure that produced this point — proportion, ratio,
         * continuous-variable, cohort, composite. Lets the dashboard render per-type charts:
         * proportion/ratio/composite share the 0–100% axis, continuous-variable needs auto
         * Y-axis (raw clinical values like HbA1c=5.6), cohort is a patient count.
         */
        private String scoringType;
        /**
         * Optional unit string for the score (typically populated for continuous-variable —
         * e.g. "mmol/L", "mg/dL"). Sourced from the report's group {@code measureScoreUnit}.
         */
        private String unit;
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
