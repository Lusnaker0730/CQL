package com.cqlplatform.repository;

import com.cqlplatform.entity.EhrConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EhrConnectionRepository extends JpaRepository<EhrConnectionEntity, Long> {
    List<EhrConnectionEntity> findByActiveTrue();
    List<EhrConnectionEntity> findByDepartmentAndActiveTrue(String department);
    List<EhrConnectionEntity> findByStatusAndActiveTrue(String status);

    // Phase 2 — tenant-scoped management queries.
    List<EhrConnectionEntity> findByTenantIdAndActiveTrue(Long tenantId);
    List<EhrConnectionEntity> findByTenantIdAndDepartmentAndActiveTrue(Long tenantId, String department);
    Optional<EhrConnectionEntity> findByIdAndTenantId(Long id, Long tenantId);
}
