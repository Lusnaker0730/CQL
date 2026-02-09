package com.cqlplatform.model.measure;

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
    private String stratifierId;
    private String criteriaExpression;
    private String description;
    private List<String> associations;
}
