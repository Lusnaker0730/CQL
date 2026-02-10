package com.cqlplatform.model.measure;

import jakarta.validation.Valid;
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

    @Valid
    private List<PopulationDefinition> populations;

    @Valid
    private List<StratifierDefinition> stratifiers;

    @Valid
    private List<ObservationDefinition> observations;

    private String scoringUnit;
    private Integer rateIndex;
    private String rateDescription;
}
