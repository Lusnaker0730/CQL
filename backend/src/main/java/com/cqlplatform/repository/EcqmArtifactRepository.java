package com.cqlplatform.repository;

import com.cqlplatform.entity.EcqmArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EcqmArtifactRepository extends JpaRepository<EcqmArtifactEntity, Long> {

    List<EcqmArtifactEntity> findByOwnerUsername(String ownerUsername);

    boolean existsByNameAndVersionAndOwnerUsername(String name, String version, String ownerUsername);

    // Phase 2 — tenant-scoped management queries.
    java.util.Optional<EcqmArtifactEntity> findByIdAndTenantId(Long id, Long tenantId);

    /** PAT-238: the builder artifact a measure was published from (latest, if several ever were). */
    java.util.Optional<EcqmArtifactEntity> findFirstByTenantIdAndPublishedMeasureIdOrderByUpdatedAtDesc(Long tenantId, Long publishedMeasureId);

    List<EcqmArtifactEntity> findByTenantIdAndOwnerUsername(Long tenantId, String ownerUsername);
}
