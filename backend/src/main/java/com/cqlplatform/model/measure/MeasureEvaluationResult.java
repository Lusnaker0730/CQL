package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Serialized form of a measure evaluation's result.
 *
 * <p>{@code @NoArgsConstructor + @AllArgsConstructor + @Builder} is required for Jackson:
 * without an explicit no-arg constructor, Lombok's {@code @Builder} generates an
 * all-args constructor that Jackson cannot use by default, and deserialization silently
 * returns {@code null} (historical bug tracked by PAT-075 / code review issue #6 —
 * previously masked by a silent catch block in MeasureReportEntity.@PostLoad).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @NoArgsConstructor
    @AllArgsConstructor
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
    @NoArgsConstructor
    @AllArgsConstructor
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PopulationResult {
        private String populationType; // initial-population, numerator, denominator, etc.
        private String populationId;
        private Integer count;
        private List<String> subjectIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StratifierResult {
        private String strataId;
        private String strataValue;
        private List<PopulationResult> populations;
        private Double measureScore;
    }
}
