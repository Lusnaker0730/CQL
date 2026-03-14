package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureTrendResult {
    private String measureName;
    private List<TrendDataPoint> dataPoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private Double score;
        private Map<String, Integer> populationCounts;
    }
}
