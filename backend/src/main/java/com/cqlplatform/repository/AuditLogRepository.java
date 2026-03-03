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

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {

    long countByCreatedAtAfter(LocalDateTime after);

    @Query("SELECT COUNT(DISTINCT a.username) FROM AuditLogEntity a WHERE a.createdAt > :after")
    long countDistinctUsernameByCreatedAtAfter(@Param("after") LocalDateTime after);

    @Query("SELECT a.action, COUNT(a) FROM AuditLogEntity a WHERE a.createdAt > :after GROUP BY a.action")
    List<Object[]> countByActionGrouped(@Param("after") LocalDateTime after);

    @Query("SELECT a.username, COUNT(a) as cnt, MAX(a.createdAt) FROM AuditLogEntity a WHERE a.createdAt > :after GROUP BY a.username ORDER BY cnt DESC")
    List<Object[]> findTopUsers(@Param("after") LocalDateTime after, Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.createdAt > :after AND a.phiAccess = true")
    long countPhiAccess(@Param("after") LocalDateTime after);

    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.createdAt > :after AND a.statusCode = 401 AND LOWER(a.path) LIKE '%login%'")
    long countFailedLoginAttempts(@Param("after") LocalDateTime after);

    @Query("SELECT CAST(a.createdAt AS java.time.LocalDate), COUNT(a) FROM AuditLogEntity a WHERE a.createdAt > :after GROUP BY CAST(a.createdAt AS java.time.LocalDate) ORDER BY CAST(a.createdAt AS java.time.LocalDate)")
    List<Object[]> countDailyActivity(@Param("after") LocalDateTime after);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.createdAt > :after AND a.phiAccess = true ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findPhiAccess(@Param("after") LocalDateTime after, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.createdAt > :after AND LOWER(a.path) LIKE '%auth%' ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findLoginActivity(@Param("after") LocalDateTime after, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.createdAt > :after AND a.statusCode IN (401, 403) ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findSecurityEvents(@Param("after") LocalDateTime after, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLogEntity a WHERE a.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") LocalDateTime before);
}
