package com.cqlplatform.repository;

import com.cqlplatform.entity.CdsArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Every lookup is tenant-scoped (BUG-134). The unscoped {@code findById} inherited from
 * JpaRepository must not be used from request paths: OwnershipVerifier's ROLE_ADMIN bypass
 * never consults TenantContext, so an id-only lookup lets a clinic ADMIN reach another
 * tenant's artifact. Scoping here is what confines that bypass to the caller's own tenant.
 */
@Repository
public interface CdsArtifactRepository extends JpaRepository<CdsArtifactEntity, Long> {

    Optional<CdsArtifactEntity> findByIdAndTenantId(Long id, Long tenantId);

    List<CdsArtifactEntity> findByOwnerUsernameAndTenantId(String ownerUsername, Long tenantId);
}
