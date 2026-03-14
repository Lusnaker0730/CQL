package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SandboxPresetResponse {

    private Long id;
    private String name;
    private String description;
    private String ownerUsername;
    private String serviceId;
    private String patientId;
    private String prefetchJson;
    private Boolean shared;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
