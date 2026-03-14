package com.cqlplatform.model.measure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopulationDefinition {
    @NotBlank
    @Size(max = 50)
    private String populationType;

    @Size(max = 500)
    private String criteriaExpression;

    @Size(max = 2000)
    private String description;

    @Size(max = 50)
    private String associationType;
}
