package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationDefinition {
    private String criteriaExpression;
    private String aggregateMethod;
    private String populationRef;
    private String description;
}
