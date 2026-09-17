package com.cqlplatform.repository;

import com.cqlplatform.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, Long> {

    Optional<DepartmentEntity> findByCode(String code);

    // Tenant-scoped variants (Phase 2 — #698). The global UNIQUE(code) -> (tenant_id, code)
    // swap lands with the batch hardening migration.
    Optional<DepartmentEntity> findByTenantIdAndCode(Long tenantId, String code);
    List<DepartmentEntity> findByTenantIdAndActiveTrue(Long tenantId);
    List<DepartmentEntity> findByTenantIdAndParentCode(Long tenantId, String parentCode);
    boolean existsByTenantIdAndCode(Long tenantId, String code);

    List<DepartmentEntity> findByActiveTrue();

    List<DepartmentEntity> findByParentCode(String parentCode);

    boolean existsByCode(String code);
}
