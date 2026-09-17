package com.cqlplatform.repository;

import com.cqlplatform.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    // Tenant-scoped (Phase 2 — #698): notifications are per-user, so the tenant predicate
    // is defence-in-depth against same-username collisions across clinics.
    List<NotificationEntity> findTop50ByTenantIdAndRecipientOrderByCreatedAtDesc(Long tenantId, String recipient);

    long countByTenantIdAndRecipientAndReadFalse(Long tenantId, String recipient);

    java.util.Optional<NotificationEntity> findByIdAndTenantId(Long id, Long tenantId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.tenantId = ?1 AND n.recipient = ?2 AND n.read = false")
    int markAllAsRead(Long tenantId, String recipient);
}
