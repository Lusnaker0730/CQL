package com.cqlplatform.service.cds;

import com.cqlplatform.entity.SandboxPresetEntity;
import com.cqlplatform.model.cds.SandboxPresetRequest;
import com.cqlplatform.model.cds.SandboxPresetResponse;
import com.cqlplatform.repository.SandboxPresetRepository;
import com.cqlplatform.security.OwnershipVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxPresetService {

    private final SandboxPresetRepository presetRepository;
    private final OwnershipVerifier ownershipVerifier;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    /** Caller's tenant ?? default — see EhrConnectionService for the canonical pattern. */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    @Transactional(readOnly = true)
    public List<SandboxPresetResponse> listAccessible(String username) {
        return presetRepository.findAccessible(username, effectiveTenantId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public SandboxPresetResponse create(String username, SandboxPresetRequest request) {
        SandboxPresetEntity entity = SandboxPresetEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerUsername(username)
                .tenantId(effectiveTenantId())
                .serviceId(request.getServiceId())
                .patientId(request.getPatientId())
                .prefetchJson(request.getPrefetchJson())
                .shared(request.getShared() != null ? request.getShared() : false)
                .build();

        SandboxPresetEntity saved = presetRepository.save(entity);
        log.info("Created sandbox preset '{}' for user '{}'", saved.getName(), username);
        return toResponse(saved);
    }

    @Transactional
    public SandboxPresetResponse update(Long id, SandboxPresetRequest request) {
        // Tenant-scoped (BUG-134): a preset in another tenant reads as not-found, which is what
        // confines the ROLE_ADMIN bypass in verifyOwnership below to the caller's own tenant.
        SandboxPresetEntity entity = presetRepository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + id));

        ownershipVerifier.verifyOwnership(entity.getOwnerUsername());

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setServiceId(request.getServiceId());
        entity.setPatientId(request.getPatientId());
        entity.setPrefetchJson(request.getPrefetchJson());
        entity.setShared(request.getShared() != null ? request.getShared() : false);

        SandboxPresetEntity saved = presetRepository.save(entity);
        log.info("Updated sandbox preset '{}' (id={})", saved.getName(), id);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        SandboxPresetEntity entity = presetRepository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + id));

        ownershipVerifier.verifyOwnership(entity.getOwnerUsername());

        presetRepository.delete(entity);
        log.info("Deleted sandbox preset '{}' (id={})", entity.getName(), id);
    }

    private SandboxPresetResponse toResponse(SandboxPresetEntity entity) {
        return SandboxPresetResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .ownerUsername(entity.getOwnerUsername())
                .serviceId(entity.getServiceId())
                .patientId(entity.getPatientId())
                .prefetchJson(entity.getPrefetchJson())
                .shared(entity.getShared())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
