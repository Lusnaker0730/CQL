package com.cqlplatform.model.measure;

import jakarta.validation.Valid;
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
public class GroupDefinition {
    @Size(max = 100)
    private String groupId;

    @Size(max = 2000)
    private String description;

    @Size(max = 50)
    private String populationBasis;

    @Valid
    @Size(max = 50)
    private List<PopulationDefinition> populations;

    @Valid
    @Size(max = 50)
    private List<StratifierDefinition> stratifiers;

    @Valid
    @Size(max = 50)
    private List<ObservationDefinition> observations;

    @Size(max = 100)
    private String scoringUnit;
    private Integer rateIndex;

    @Size(max = 2000)
    private String rateDescription;
}
