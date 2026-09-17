package com.cqlplatform.model.admin;

import com.cqlplatform.entity.ClinicApplicationEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Operator-facing view of a clinic application (includes the contact email). */
@Data
@Builder
public class ClinicApplicationResponse {

    private Long id;
    private String clinicName;
    private String tenantCode;
    private String adminUsername;
    private String adminEmail;
    private String status;
    private String rejectionReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private Long createdTenantId;
    private Long createdUserId;
    private LocalDateTime createdAt;

    public static ClinicApplicationResponse from(ClinicApplicationEntity e) {
        return ClinicApplicationResponse.builder()
                .id(e.getId())
                .clinicName(e.getClinicName())
                .tenantCode(e.getTenantCode())
                .adminUsername(e.getAdminUsername())
                .adminEmail(e.getAdminEmail())
                .status(e.getStatus())
                .rejectionReason(e.getRejectionReason())
                .reviewedBy(e.getReviewedBy())
                .reviewedAt(e.getReviewedAt())
                .createdTenantId(e.getCreatedTenantId())
                .createdUserId(e.getCreatedUserId())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
