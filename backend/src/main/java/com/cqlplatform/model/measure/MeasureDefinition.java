package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureDefinition {
    private Long id;
    private String name;
    private String version;
    private String title;
    private String description;
    private String status;
    private String scoringType;
    private String cqlLibraryId;
    private String cqlContent;
    private String fhirMeasureJson;
    private List<GroupDefinition> groupDefinitions;
    private String compositeScoring;
    private List<Long> componentMeasureIds;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
