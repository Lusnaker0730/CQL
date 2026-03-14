package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureComparisonResult {
    private String measureName;
    private PeriodSummary period1;
    private PeriodSummary period2;
    private Double scoreDelta;
    private Double scorePercentChange;
    private Map<String, Integer> populationDeltas;
    private String trend; // improving, declining, stable

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodSummary {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private Double measureScore;
        private Integer totalPatients;
        private Map<String, Integer> populationCounts;
    }
}
