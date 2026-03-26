package com.cqlplatform.model.measure;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MeasureEvaluationResult {
    private String measureId;
    private String measureName;
    private String status;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String reportType;
    private List<GroupResult> groups;
    private Map<String, Object> supplementalData;
    private String errorMessage;

    @Data
    @Builder
    public static class GroupResult {
        private String groupId;
        private String description;
        private List<PopulationResult> populations;
        private Double measureScore;
        private String measureScoreUnit;
        private List<StratifierResult> stratifiers;
        private Integer totalPatients;
        private ObservationStatistics observationStatistics;
    }

    @Data
    @Builder
    public static class ObservationStatistics {
        private String aggregateMethod;
        private Double aggregateValue;
        private Integer observationCount;
        private Double minimum;
        private Double maximum;
        private Double average;
        private Double median;
        private String unit;
    }

    @Data
    @Builder
    public static class PopulationResult {
        private String populationType; // initial-population, numerator, denominator, etc.
        private String populationId;
        private Integer count;
        private List<String> subjectIds;
    }

    @Data
    @Builder
    public static class StratifierResult {
        private String strataId;
        private String strataValue;
        private List<PopulationResult> populations;
        private Double measureScore;
    }
}
