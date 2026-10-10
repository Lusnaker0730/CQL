package com.cqlplatform.repository;

import com.cqlplatform.entity.MeasureSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** PAT-253: measure version lineages. Tenant-scoped like every other repository. */
@Repository
public interface MeasureSetRepository extends JpaRepository<MeasureSetEntity, Long> {

    Optional<MeasureSetEntity> findByIdAndTenantId(Long id, Long tenantId);
}
