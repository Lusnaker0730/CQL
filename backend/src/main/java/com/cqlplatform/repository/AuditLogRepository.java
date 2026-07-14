package com.cqlplatform.repository;

import com.cqlplatform.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * All management/dashboard queries are tenant-scoped (Phase 2 — #698): a clinic's
 * admins must not see another clinic's audit trail (it includes patient_fhir_id and
 * user activity). Only the retention DELETE stays global — it is a system operation
 * with a uniform, non-tenant policy.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {

    long countByTenantIdAndCreatedAtAfter(Long tenantId, LocalDateTime after);

    @Query("SELECT COUNT(DISTINCT a.username) FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after")
    long countDistinctUsernameByCreatedAtAfter(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after);

    @Query("SELECT a.action, COUNT(a) FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after GROUP BY a.action")
    List<Object[]> countByActionGrouped(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after);

    @Query("SELECT a.username, COUNT(a) as cnt, MAX(a.createdAt) FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after GROUP BY a.username ORDER BY cnt DESC")
    List<Object[]> findTopUsers(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after, Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after AND a.phiAccess = true")
    long countPhiAccess(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after);

    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after AND a.statusCode = 401 AND LOWER(a.path) LIKE '%login%'")
    long countFailedLoginAttempts(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after);

    @Query("SELECT CAST(a.createdAt AS java.time.LocalDate), COUNT(a) FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after GROUP BY CAST(a.createdAt AS java.time.LocalDate) ORDER BY CAST(a.createdAt AS java.time.LocalDate)")
    List<Object[]> countDailyActivity(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after AND a.phiAccess = true ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findPhiAccess(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after AND LOWER(a.path) LIKE '%auth%' ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findLoginActivity(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.createdAt > :after AND a.statusCode IN (401, 403) ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findSecurityEvents(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after, Pageable pageable);

    /**
     * PAT-146 — {@code clearAutomatically=true} ensures that any audit-log
     * entities loaded into the persistence context within the same transaction
     * are evicted after the bulk DELETE, so subsequent reads in that
     * transaction don't return stale (already-deleted) rows.
     *
     * <p>Intentionally NOT tenant-scoped: retention is a uniform system policy
     * applied by the scheduled cleanup (and the ADMIN-only manual cleanup).
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuditLogEntity a WHERE a.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") LocalDateTime before);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.connectionId = :connectionId ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findByConnectionId(@Param("tenantId") Long tenantId, @Param("connectionId") Long connectionId, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.patientFhirId = :patientFhirId ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findByPatientFhirId(@Param("tenantId") Long tenantId, @Param("patientFhirId") String patientFhirId, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId AND a.connectionId IS NOT NULL AND a.createdAt > :after ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findEhrOperations(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after, Pageable pageable);
}
