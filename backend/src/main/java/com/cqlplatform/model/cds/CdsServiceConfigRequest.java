package com.cqlplatform.model.cds;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdsServiceConfigRequest {

    @NotBlank(message = "Service ID is required")
    @Size(max = 100, message = "Service ID must be at most 100 characters")
    private String id;

    @NotBlank(message = "Hook type is required")
    @Size(max = 50, message = "Hook type must be at most 50 characters")
    private String hook;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    private String cqlContent;

    @Size(max = 100, message = "CQL Library ID must be at most 100 characters")
    private String cqlLibraryId;

    @Size(max = 20, message = "Default indicator must be at most 20 characters")
    private String defaultIndicator;

    @Builder.Default
    private Boolean enabled = true;

    private Map<String, String> prefetch;
}
