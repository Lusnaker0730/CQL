package com.cqlplatform.repository;

import com.cqlplatform.entity.FailedImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FailedImportRepository extends JpaRepository<FailedImportEntity, Long> {
    List<FailedImportEntity> findByStatusOrderByCreatedAtDesc(String status);
    List<FailedImportEntity> findByConnectionIdOrderByCreatedAtDesc(Long connectionId);
    List<FailedImportEntity> findAllByOrderByCreatedAtDesc();

    // System query — intentionally unscoped: the retry scheduler sweeps ALL tenants' due
    // rows and re-executes each one under its own tenant via TenantContext.callWith.
    @Query("SELECT f FROM FailedImportEntity f WHERE f.status = 'pending' AND f.nextRetryAt <= :now ORDER BY f.nextRetryAt")
    List<FailedImportEntity> findDueForRetry(@Param("now") LocalDateTime now);

    long countByStatus(String status);

    // Tenant-scoped variants (Phase 2 — #698 import-domain enforcement)
    java.util.Optional<FailedImportEntity> findByIdAndTenantId(Long id, Long tenantId);
    List<FailedImportEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(Long tenantId, String status);
    List<FailedImportEntity> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
