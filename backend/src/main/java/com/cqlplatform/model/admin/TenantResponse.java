package com.cqlplatform.model.admin;

import com.cqlplatform.entity.TenantEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantResponse {

    private Long id;
    private String code;
    private String name;
    private Boolean active;
    private LocalDateTime createdAt;

    public static TenantResponse from(TenantEntity entity) {
        return TenantResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
