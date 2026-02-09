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
    private String populationBasis;
    private List<PopulationDefinition> populations;
    private List<StratifierDefinition> stratifiers;
    private List<ObservationDefinition> observations;
    private String scoringUnit;
    private Integer rateIndex;
    private String rateDescription;
}
