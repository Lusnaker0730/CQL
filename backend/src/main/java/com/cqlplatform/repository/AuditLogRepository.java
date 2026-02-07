package com.cqlplatform.repository;

import com.cqlplatform.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findByUsernameOrderByCreatedAtDesc(String username);
    List<AuditLogEntity> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);
    List<AuditLogEntity> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, String resourceId);
}
