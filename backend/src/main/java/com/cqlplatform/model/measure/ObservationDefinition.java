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
    @Pattern(regexp = "sum|average|median|minimum|maximum|count")
    private String aggregateMethod;

    @Size(max = 200)
    private String populationRef;

    @Size(max = 2000)
    private String description;
}
