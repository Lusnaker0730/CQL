package com.cqlplatform.model.ecqm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcqmArtifactSummary {

    private Long id;
    private String name;
    private String version;
    private String description;
    private String status;
    private String scoringType;
    private String populationBasis;
    private Long publishedMeasureId;
    private String ownerUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
