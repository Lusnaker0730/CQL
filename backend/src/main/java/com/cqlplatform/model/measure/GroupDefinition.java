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
public class GroupDefinition {
    private String groupId;
    private String description;
    private List<PopulationDefinition> populations;
    private List<StratifierDefinition> stratifiers;
}
