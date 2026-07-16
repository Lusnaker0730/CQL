package com.cqlplatform.repository;

import com.cqlplatform.entity.SandboxPresetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Every lookup is tenant-scoped (BUG-134). Sharing is a within-tenant concept: the
 * {@code shared = true} branch below used to return every tenant's shared presets, and the
 * id-only lookups let OwnershipVerifier's ROLE_ADMIN bypass (which never consults
 * TenantContext) mutate another tenant's preset.
 */
@Repository
public interface SandboxPresetRepository extends JpaRepository<SandboxPresetEntity, Long> {

    Optional<SandboxPresetEntity> findByIdAndTenantId(Long id, Long tenantId);

    List<SandboxPresetEntity> findByOwnerUsernameAndTenantIdOrderByUpdatedAtDesc(
            String ownerUsername, Long tenantId);

    @Query("SELECT p FROM SandboxPresetEntity p WHERE p.tenantId = :tenantId "
            + "AND (p.ownerUsername = :username OR p.shared = true) ORDER BY p.updatedAt DESC")
    List<SandboxPresetEntity> findAccessible(@Param("username") String username,
                                             @Param("tenantId") Long tenantId);
}
