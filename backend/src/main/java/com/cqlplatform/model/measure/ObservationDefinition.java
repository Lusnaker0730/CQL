package com.cqlplatform.model.measure;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationDefinition {
    @Size(max = 500)
    private String criteriaExpression;

    @Size(max = 50)
    @Pattern(regexp = "sum|average|median|minimum|maximum|count|percentile")
    private String aggregateMethod;

    /** PAT-264: the percentile (0–100) when {@code aggregateMethod} is {@code percentile}; ignored otherwise. */
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.DecimalMax("100")
    private Double percentileValue;

    @Size(max = 200)
    private String populationRef;

    @Size(max = 2000)
    private String description;
}
