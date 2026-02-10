package com.cqlplatform.model.measure;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StratifierDefinition {
    @Size(max = 200)
    private String stratifierId;

    @Size(max = 500)
    private String criteriaExpression;

    @Size(max = 2000)
    private String description;

    private List<String> associations;
}
