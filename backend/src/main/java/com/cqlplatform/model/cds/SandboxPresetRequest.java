package com.cqlplatform.model.cds;

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
public class SandboxPresetRequest {

    @NotBlank(message = "Preset name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @Size(max = 100, message = "Service ID must be at most 100 characters")
    private String serviceId;

    @Size(max = 100, message = "Patient ID must be at most 100 characters")
    private String patientId;

    @NotBlank(message = "Prefetch JSON is required")
    @Size(max = 2_097_152, message = "Prefetch JSON must be at most 2 MB")
    private String prefetchJson;

    @Builder.Default
    private Boolean shared = false;
}
