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
public class QualityReport {
    private String reportType; // monthly, quarterly, yearly
    private String periodLabel;
    private String department;
    private int totalMeasures;
    private int measuresAboveTarget;
    private int measuresBelowTarget;
    private double averageScore;
    private List<MeasureScoreSummary> measureScores;
    private Map<String, Double> departmentAverages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeasureScoreSummary {
        private Long measureId;
        private String measureName;
        private Double score;
        private String status; // above_target, below_target, warning, critical
        private Double targetThreshold;
        private String scoringType; // proportion, ratio, continuous-variable, cohort
    }
}
