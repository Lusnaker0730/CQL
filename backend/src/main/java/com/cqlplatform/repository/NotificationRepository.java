package com.cqlplatform.repository;

import com.cqlplatform.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByRecipientOrderByCreatedAtDesc(String recipient);

    List<NotificationEntity> findTop50ByRecipientOrderByCreatedAtDesc(String recipient);

    long countByRecipientAndReadFalse(String recipient);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.recipient = ?1 AND n.read = false")
    int markAllAsRead(String recipient);
}
