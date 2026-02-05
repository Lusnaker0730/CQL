package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsServiceConfigResponse {

    private String id;
    private String hook;
    private String title;
    private String description;
    private String cqlContent;
    private String cqlLibraryId;
    private String defaultIndicator;
    private Boolean enabled;
    private Map<String, String> prefetch;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
