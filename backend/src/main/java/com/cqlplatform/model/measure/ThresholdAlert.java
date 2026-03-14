package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdAlert {
    private Long measureId;
    private String measureName;
    private String thresholdType;
    private Double thresholdValue;
    private Double actualScore;
    private String comparisonOperator;
    private String department;
    private String severity; // critical, warning, info
}
